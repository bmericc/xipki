/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * THE AUTHOR LIJUN LIAO. LIJUN LIAO DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the XiPKI software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Lijun Liao at this
 * address: lijun.liao@gmail.com
 */

package org.xipki.pki.ca.server.impl.cmp;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1OutputStream;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIHeaderBuilder;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.audit.api.AuditEvent;
import org.xipki.commons.audit.api.AuditEventData;
import org.xipki.commons.audit.api.AuditLevel;
import org.xipki.commons.audit.api.AuditService;
import org.xipki.commons.audit.api.AuditServiceRegister;
import org.xipki.commons.audit.api.AuditStatus;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class HttpCmpServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(HttpCmpServlet.class);

    private static final long serialVersionUID = 1L;

    private static final String CT_REQUEST = "application/pkixcmp";

    private static final String CT_RESPONSE = "application/pkixcmp";

    private CmpResponderManager responderManager;

    private AuditServiceRegister auditServiceRegister;

    public HttpCmpServlet() {
    }

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response)
    throws ServletException, IOException {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute(
                "javax.servlet.request.X509Certificate");
        X509Certificate clientCert = (certs == null || certs.length < 1)
                ? null
                : certs[0];

        AuditService auditService = auditServiceRegister.getAuditService();
        AuditEvent auditEvent = (auditService != null)
                ? new AuditEvent(new Date())
                : null;
        if (auditEvent != null) {
            auditEvent.setApplicationName("CA");
            auditEvent.setName("PERF");
        }

        AuditLevel auditLevel = AuditLevel.INFO;
        AuditStatus auditStatus = AuditStatus.SUCCESSFUL;
        String auditMessage = null;
        try {
            if (responderManager == null) {
                String message = "responderManager in servlet not configured";
                LOG.error(message);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentLength(0);

                auditLevel = AuditLevel.ERROR;
                auditStatus = AuditStatus.FAILED;
                auditMessage = message;
                return;
            }

            if (!CT_REQUEST.equalsIgnoreCase(request.getContentType())) {
                response.setContentLength(0);
                response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);

                auditStatus = AuditStatus.FAILED;
                auditMessage = "unsupported media type " + request.getContentType();
                return;
            }

            String requestUri = request.getRequestURI();
            String servletPath = request.getServletPath();

            String caName = null;
            X509CaCmpResponder responder = null;
            int len = servletPath.length();
            if (requestUri.length() > len + 1) {
                String caAlias = URLDecoder.decode(requestUri.substring(len + 1), "UTF-8");
                caName = responderManager.getCaNameForAlias(caAlias);
                if (caName == null) {
                    caName = caAlias;
                }
                caName = caName.toUpperCase();
                responder = responderManager.getX509CaCmpResponder(caName);
            }

            if (caName == null || responder == null || !responder.isInService()) {
                if (caName == null) {
                    auditMessage = "no CA is specified";
                } else if (responder == null) {
                    auditMessage = "unknown CA '" + caName + "'";
                } else {
                    auditMessage = "CA '" + caName + "' is out of service";
                }
                LOG.warn(auditMessage);

                response.setContentLength(0);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);

                auditStatus = AuditStatus.FAILED;
                return;
            }

            if (auditEvent != null) {
                auditEvent.addEventData(new AuditEventData("CA",
                        responder.getCa().getCaInfo().getName()));
            }

            PKIMessage pkiReq;
            try {
                pkiReq = generatePkiMessage(request.getInputStream());
            } catch (Exception ex) {
                response.setContentLength(0);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                auditStatus = AuditStatus.FAILED;
                auditMessage = "bad request";

                LogUtil.error(LOG, ex, "could not parse the request (PKIMessage)");
                return;
            }

            PKIHeader reqHeader = pkiReq.getHeader();
            ASN1OctetString tid = reqHeader.getTransactionID();

            PKIHeaderBuilder respHeader = new PKIHeaderBuilder(
                    reqHeader.getPvno().getValue().intValue(),
                    reqHeader.getRecipient(),
                    reqHeader.getSender());
            respHeader.setTransactionID(tid);

            PKIMessage pkiResp = responder.processPkiMessage(pkiReq, clientCert, auditEvent);

            response.setContentType(HttpCmpServlet.CT_RESPONSE);
            response.setStatus(HttpServletResponse.SC_OK);
            ASN1OutputStream asn1Out = new ASN1OutputStream(response.getOutputStream());
            asn1Out.writeObject(pkiResp);
            asn1Out.flush();
        } catch (EOFException ex) {
            LogUtil.warn(LOG, ex, "connection reset by peer");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);
        } catch (Throwable th) {
            final String message = "Throwable thrown, this should not happen!";
            LogUtil.error(LOG, th, message);

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentLength(0);
            auditLevel = AuditLevel.ERROR;
            auditStatus = AuditStatus.FAILED;
            auditMessage = "internal error";
        } finally {
            try {
                response.flushBuffer();
            } finally {
                if (auditEvent != null) {
                    audit(auditService, auditEvent, auditLevel, auditStatus, auditMessage);
                }
            }
        }
    } // method doPost

    protected PKIMessage generatePkiMessage(final InputStream is) throws IOException {
        ParamUtil.requireNonNull("is", is);
        ASN1InputStream asn1Stream = new ASN1InputStream(is);

        try {
            return PKIMessage.getInstance(asn1Stream.readObject());
        } finally {
            try {
                asn1Stream.close();
            } catch (Exception ex) {
                LOG.error("could not close ASN1Stream: {}", ex.getMessage());
            }
        }
    }

    public void setResponderManager(final CmpResponderManager responderManager) {
        this.responderManager = responderManager;
    }

    public void setAuditServiceRegister(final AuditServiceRegister auditServiceRegister) {
        this.auditServiceRegister = auditServiceRegister;
    }

    private static void audit(final AuditService auditService, final AuditEvent auditEvent,
            final AuditLevel auditLevel, final AuditStatus auditStatus, final String auditMessage) {
        if (auditLevel != null) {
            auditEvent.setLevel(auditLevel);
        }

        if (auditStatus != null) {
            auditEvent.setStatus(auditStatus);
        }

        if (auditMessage != null) {
            auditEvent.addEventData(new AuditEventData("message", auditMessage));
        }

        auditEvent.setDuration(System.currentTimeMillis() - auditEvent.getTimestamp().getTime());

        if (!auditEvent.containsChildAuditEvents()) {
            auditService.logEvent(auditEvent);
        } else {
            List<AuditEvent> expandedAuditEvents = auditEvent.expandAuditEvents();
            for (AuditEvent event : expandedAuditEvents) {
                auditService.logEvent(event);
            }
        }
    } // method audit

}
