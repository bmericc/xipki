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

package org.xipki.pki.ca.server.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CRLException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.asn1.x509.CertificateList;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuingDistributionPoint;
import org.bouncycastle.asn1.x509.ReasonFlags;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory;
import org.bouncycastle.jce.provider.X509CRLObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.audit.api.AuditEvent;
import org.xipki.commons.audit.api.AuditEventData;
import org.xipki.commons.audit.api.AuditLevel;
import org.xipki.commons.audit.api.AuditService;
import org.xipki.commons.audit.api.AuditServiceRegister;
import org.xipki.commons.audit.api.AuditStatus;
import org.xipki.commons.common.HealthCheckResult;
import org.xipki.commons.common.util.CollectionUtil;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.security.CertRevocationInfo;
import org.xipki.commons.security.ConcurrentContentSigner;
import org.xipki.commons.security.CrlReason;
import org.xipki.commons.security.FpIdCalculator;
import org.xipki.commons.security.KeyUsage;
import org.xipki.commons.security.ObjectIdentifiers;
import org.xipki.commons.security.SecurityFactory;
import org.xipki.commons.security.X509Cert;
import org.xipki.commons.security.XiSecurityConstants;
import org.xipki.commons.security.exception.NoIdleSignerException;
import org.xipki.commons.security.exception.XiSecurityException;
import org.xipki.commons.security.util.X509Util;
import org.xipki.pki.ca.api.BadCertTemplateException;
import org.xipki.pki.ca.api.BadFormatException;
import org.xipki.pki.ca.api.OperationException;
import org.xipki.pki.ca.api.OperationException.ErrorCode;
import org.xipki.pki.ca.api.RequestType;
import org.xipki.pki.ca.api.RequestorInfo;
import org.xipki.pki.ca.api.X509CertWithDbId;
import org.xipki.pki.ca.api.profile.CertValidity;
import org.xipki.pki.ca.api.profile.CertprofileException;
import org.xipki.pki.ca.api.profile.ExtensionValue;
import org.xipki.pki.ca.api.profile.ExtensionValues;
import org.xipki.pki.ca.api.profile.x509.SpecialX509CertprofileBehavior;
import org.xipki.pki.ca.api.profile.x509.SubjectInfo;
import org.xipki.pki.ca.api.profile.x509.X509CertVersion;
import org.xipki.pki.ca.api.publisher.x509.X509CertificateInfo;
import org.xipki.pki.ca.server.impl.cmp.CmpRequestorEntryWrapper;
import org.xipki.pki.ca.server.impl.cmp.CmpRequestorInfo;
import org.xipki.pki.ca.server.impl.store.CertificateStore;
import org.xipki.pki.ca.server.impl.store.X509CertWithRevocationInfo;
import org.xipki.pki.ca.server.mgmt.api.CaHasRequestorEntry;
import org.xipki.pki.ca.server.mgmt.api.CaMgmtException;
import org.xipki.pki.ca.server.mgmt.api.CaStatus;
import org.xipki.pki.ca.server.mgmt.api.ValidityMode;
import org.xipki.pki.ca.server.mgmt.api.x509.CrlControl;
import org.xipki.pki.ca.server.mgmt.api.x509.CrlControl.HourMinute;
import org.xipki.pki.ca.server.mgmt.api.x509.CrlControl.UpdateMode;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class X509Ca {

    private class ScheduledExpiredCertsRemover implements Runnable {

        private boolean inProcess;

        @Override
        public void run() {
            int keepDays = caInfo.getKeepExpiredCertInDays();
            if (keepDays < 0) {
                return;
            }

            if (inProcess) {
                return;
            }

            inProcess = true;
            boolean successful = true;
            final long startTime = System.currentTimeMillis();
            final Date expiredAt = new Date(startTime - DAY_IN_MS * (keepDays + 1));

            int num = 0;
            try {
                num = removeExpirtedCerts(expiredAt);
                LOG.info("removed {} certificates expired at {}", num, expiredAt.toString());
            } catch (Throwable th) {
                successful = false;
                LogUtil.error(LOG, th, "could not remove expired certificates");
            } finally {
                AuditEvent auditEvent = newAuditEvent();
                auditEvent.setDuration(System.currentTimeMillis() - startTime);
                auditEvent.addEventData(new AuditEventData("CA", caInfo.getName()));
                auditEvent.addEventData(new AuditEventData("expiredAt", expiredAt.toString()));
                auditEvent.addEventData(new AuditEventData("eventType", "REMOVE_EXPIRED_CERTS"));
                auditEvent.addEventData(new AuditEventData("numCerts", Integer.toString(num)));

                if (successful) {
                    auditEvent.setLevel(AuditLevel.INFO);
                    auditEvent.setStatus(AuditStatus.SUCCESSFUL);
                } else {
                    auditEvent.setLevel(AuditLevel.ERROR);
                    auditEvent.setStatus(AuditStatus.FAILED);
                }
                getAuditService().logEvent(auditEvent);

                inProcess = false;
            }
        } // method run

    } // class ScheduledExpiredCertsRemover

    private class ScheduledCrlGenerationService implements Runnable {

        @Override
        public void run() {
            X509CrlSignerEntryWrapper crlSigner = getCrlSigner();
            if (crlSigner == null
                    || crlSigner.getCrlControl().getUpdateMode() != UpdateMode.interval) {
                return;
            }

            if (crlGenInProcess.get()) {
                return;
            }

            crlGenInProcess.set(true);

            try {
                doRun();
            } catch (Throwable th) {
                LOG.error("CRL_GEN_INTERVAL: fatal error", th);
            } finally {
                crlGenInProcess.set(false);
            }
        } // method run

        private void doRun() throws OperationException {
            final long signWindowMin = 20;

            Date thisUpdate = new Date();
            long minSinceCrlBaseTime = (thisUpdate.getTime() - caInfo.getCrlBaseTime().getTime())
                            / MS_PER_MINUTE;

            CrlControl control = getCrlSigner().getCrlControl();
            int interval;

            if (control.getIntervalMinutes() != null && control.getIntervalMinutes() > 0) {
                long intervalMin = control.getIntervalMinutes();
                interval = (int) (minSinceCrlBaseTime / intervalMin);

                long baseTimeInMin = interval * intervalMin;
                if (minSinceCrlBaseTime - baseTimeInMin > signWindowMin) {
                    // only generate CRL within the time window
                    return;
                }
            } else if (control.getIntervalDayTime() != null) {
                HourMinute hm = control.getIntervalDayTime();
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTime(thisUpdate);
                int minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
                int scheduledMinute = hm.getHour() * 60 + hm.getMinute();
                if (minute < scheduledMinute || minute - scheduledMinute > signWindowMin) {
                    return;
                }
                interval = (int) (minSinceCrlBaseTime % MINUTE_PER_DAY);
            } else {
                throw new RuntimeException("should not reach here, neither interval minutes"
                        + " nor dateTime is specified");
            }

            boolean deltaCrl;
            if (interval % control.getFullCrlIntervals() == 0) {
                deltaCrl = false;
            } else if (control.getDeltaCrlIntervals() > 0
                    && interval % control.getDeltaCrlIntervals() == 0) {
                deltaCrl = true;
            } else {
                return;
            }

            if (deltaCrl && !certstore.hasCrl(caInfo.getCertificate())) {
                // DeltaCRL will be generated only if fullCRL exists
                return;
            }

            long nowInSecond = thisUpdate.getTime() / MS_PER_SECOND;
            long thisUpdateOfCurrentCrl = certstore.getThisUpdateOfCurrentCrl(
                    caInfo.getCertificate());
            if (nowInSecond - thisUpdateOfCurrentCrl <= (signWindowMin + 5) * 60) {
                // CRL was just generated within SIGN_WINDOW_MIN + 5 minutes
                return;
            }

            // find out the next interval for fullCRL and deltaCRL
            int nextFullCrlInterval = 0;
            int nextDeltaCrlInterval = 0;

            for (int i = interval + 1;; i++) {
                if (i % control.getFullCrlIntervals() == 0) {
                    nextFullCrlInterval = i;
                    break;
                }

                if (nextDeltaCrlInterval != 0 && control.getDeltaCrlIntervals() != 0
                        && i % control.getDeltaCrlIntervals() == 0) {
                    nextDeltaCrlInterval = i;
                }
            }

            int intervalOfNextUpdate;
            if (deltaCrl) {
                intervalOfNextUpdate = nextDeltaCrlInterval == 0 ? nextFullCrlInterval
                        : Math.min(nextFullCrlInterval, nextDeltaCrlInterval);
            } else {
                if (nextDeltaCrlInterval == 0) {
                    intervalOfNextUpdate = nextFullCrlInterval;
                } else {
                    intervalOfNextUpdate = control.isExtendedNextUpdate() ? nextFullCrlInterval
                            : Math.min(nextFullCrlInterval, nextDeltaCrlInterval);
                }
            }

            Date nextUpdate;
            if (control.getIntervalMinutes() != null) {
                int minutesTillNextUpdate = (intervalOfNextUpdate - interval)
                        * control.getIntervalMinutes() + control.getOverlapMinutes();
                nextUpdate = new Date(MS_PER_SECOND * (nowInSecond + minutesTillNextUpdate * 60));
            } else {
                HourMinute hm = control.getIntervalDayTime();
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                cal.setTime(new Date(nowInSecond * MS_PER_SECOND));
                cal.add(Calendar.DAY_OF_YEAR, (intervalOfNextUpdate - interval));
                cal.set(Calendar.HOUR_OF_DAY, hm.getHour());
                cal.set(Calendar.MINUTE, hm.getMinute());
                cal.add(Calendar.MINUTE, control.getOverlapMinutes());
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                nextUpdate = cal.getTime();
            }

            Date start = new Date();
            AuditEvent auditEvent = new AuditEvent(start);
            auditEvent.setApplicationName("CA");
            auditEvent.setName("PERF");
            auditEvent.addEventData(new AuditEventData("eventType", "CRL_GEN_INTERVAL"));

            try {
                long maxIdOfDeltaCrlCache = certstore.getMaxIdOfDeltaCrlCache(
                        caInfo.getCertificate());

                generateCrl(deltaCrl, thisUpdate, nextUpdate, auditEvent);
                auditEvent.setStatus(AuditStatus.SUCCESSFUL);
                auditEvent.setLevel(AuditLevel.INFO);

                try {
                    certstore.clearDeltaCrlCache(caInfo.getCertificate(), maxIdOfDeltaCrlCache);
                } catch (Throwable th) {
                    LogUtil.error(LOG, th, "CRL_GEN_INTERVAL: could not clear DeltaCRLCache of CA "
                            + caInfo.getName());
                }
            } catch (Throwable th) {
                auditEvent.setStatus(AuditStatus.FAILED);
                auditEvent.setLevel(AuditLevel.ERROR);
                LogUtil.error(LOG, th, "CRL_GEN_INTERVAL: Error");
            } finally {
                auditEvent.setDuration(System.currentTimeMillis() - start.getTime());
            }

            getAuditService().logEvent(auditEvent);
            LOG.info("CRL_GEN_INTERVAL: {}", auditEvent.getStatus().name());
        } // method doRun

    } // class ScheduledCrlGenerationService

    private class ScheduledSuspendedCertsRevoker implements Runnable {

        private boolean inProcess;

        @Override
        public void run() {
            if (caInfo.getRevokeSuspendedCertsControl() == null) {
                return;
            }

            if (inProcess) {
                return;
            }

            inProcess = true;
            try {
                LOG.debug("revoking suspended certificates");
                int num = revokeSuspendedCerts();
                if (num == 0) {
                    LOG.debug("revokend {} suspended certificates of CA '{}'", num,
                            caInfo.getCaEntry().getName());
                } else {
                    LOG.info("revokend {} suspended certificates of CA '{}'", num,
                            caInfo.getCaEntry().getName());
                }
            } catch (Throwable th) {
                LogUtil.error(LOG, th, "could not revoke suspended certificates");
            } finally {
                inProcess = false;
            }
        } // method run

    } // class ScheduledSuspendedCertsRevoker

    private static final long MS_PER_SECOND = 1000L;

    private static final long MS_PER_MINUTE = 60000L;

    private static final int MINUTE_PER_DAY = 24 * 60;

    private static final long DAY_IN_MS = MS_PER_MINUTE * MINUTE_PER_DAY;

    private static final long MAX_CERT_TIME_MS = 253402300799982L; //9999-12-31-23-59-59

    private static final Logger LOG = LoggerFactory.getLogger(X509Ca.class);

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss.SSSz");

    private final CertificateFactory cf;

    private final X509CaInfo caInfo;

    private final CertificateStore certstore;

    private final boolean masterMode;

    private final CaManagerImpl caManager;

    private Boolean tryNssToVerify;

    private AtomicBoolean crlGenInProcess = new AtomicBoolean(false);

    private ScheduledFuture<?> crlGenerationService;

    private ScheduledFuture<?> expiredCertsRemover;

    private ScheduledFuture<?> suspendedCertsRevoker;

    private AuditServiceRegister auditServiceRegister;

    public X509Ca(final CaManagerImpl caManager, final X509CaInfo caInfo,
            final CertificateStore certstore, final SecurityFactory securityFactory,
            final boolean masterMode) throws OperationException {
        this.caManager = ParamUtil.requireNonNull("caManager", caManager);
        this.caInfo = ParamUtil.requireNonNull("caInfo", caInfo);
        this.certstore = ParamUtil.requireNonNull("certstore", certstore);
        this.masterMode = masterMode;

        if (caInfo.isSignerRequired()) {
            try {
                caInfo.initSigner(securityFactory);
            } catch (XiSecurityException ex) {
                LogUtil.error(LOG, ex,
                        "security.createSigner caSigner (ca=" + caInfo.getName() + ")");
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
            }
        }

        X509Cert caCert = caInfo.getCertificate();

        X509CrlSignerEntryWrapper crlSigner = getCrlSigner();
        if (crlSigner != null) {
            // CA signs the CRL
            if (caManager.getCrlSignerWrapper(caInfo.getCrlSignerName()) == null
                    && !X509Util.hasKeyusage(caInfo.getCertificate().getCert(), KeyUsage.cRLSign)) {
                final String msg = "CRL signer does not have keyusage cRLSign";
                LOG.error(msg);
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, msg);
            }
        }

        this.cf = new CertificateFactory();
        if (!masterMode) {
            return;
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            publisher.caAdded(caCert);
        }

        // CRL generation services
        this.crlGenerationService = caManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(
                new ScheduledCrlGenerationService(), 1, 1, TimeUnit.MINUTES);

        this.expiredCertsRemover = caManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(
                new ScheduledExpiredCertsRemover(), 1, 1, TimeUnit.DAYS);

        this.suspendedCertsRevoker = caManager.getScheduledThreadPoolExecutor().scheduleAtFixedRate(
                new ScheduledSuspendedCertsRevoker(), 30, 60, TimeUnit.MINUTES);
    } // constructor

    public X509CaInfo getCaInfo() {
        return caInfo;
    }

    public X509Certificate getCertificate(final BigInteger serialNumber)
    throws CertificateException, OperationException {
        X509CertificateInfo certInfo = certstore.getCertificateInfoForSerial(
                caInfo.getCertificate(), serialNumber);
        return (certInfo == null) ? null : certInfo.getCert().getCert();
    }

    /**
     *
     * @param subjectName Subject of the certificate.
     * @param transactionId <code>null</code> for all transactionIds.
     */
    public List<X509Certificate> getCertificate(final X500Name subjectName,
            final byte[] transactionId) throws OperationException {
        return certstore.getCertificate(subjectName, transactionId);
    }

    public KnowCertResult knowsCertificate(final X509Certificate cert) throws OperationException {
        ParamUtil.requireNonNull("cert", cert);
        if (!caInfo.getSubject().equals(X509Util.getRfc4519Name(cert.getIssuerX500Principal()))) {
            return KnowCertResult.UNKNOWN;
        }

        return certstore.knowsCertForSerial(caInfo.getCertificate(), cert.getSerialNumber());
    }

    public X509CertWithRevocationInfo getCertWithRevocationInfo(final BigInteger serialNumber)
    throws CertificateException, OperationException {
        return certstore.getCertWithRevocationInfo(caInfo.getCertificate(), serialNumber);
    }

    public boolean authenticateUser(final String user, final byte[] password)
    throws OperationException {
        return certstore.authenticateUser(user, password);
    }

    public String getCnRegexForUser(final String user) throws OperationException {
        return certstore.getCnRegexForUser(user);
    }

    public CertificateList getCurrentCrl() throws OperationException {
        return getCrl(null);
    }

    public CertificateList getCrl(final BigInteger crlNumber) throws OperationException {
        LOG.info("     START getCurrentCrl: ca={}, crlNumber={}", caInfo.getName(), crlNumber);
        boolean successful = false;

        try {
            byte[] encodedCrl = certstore.getEncodedCrl(caInfo.getCertificate(), crlNumber);
            if (encodedCrl == null) {
                return null;
            }

            try {
                CertificateList crl = CertificateList.getInstance(encodedCrl);
                successful = true;

                LOG.info("SUCCESSFUL getCurrentCrl: ca={}, thisUpdate={}", caInfo.getName(),
                        crl.getThisUpdate().getTime());

                return crl;
            } catch (RuntimeException ex) {
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
            }
        } finally {
            if (!successful) {
                LOG.info("    FAILED getCurrentCrl: ca={}", caInfo.getName());
            }
        }
    } // method getCrl

    private void cleanupCrlsWithoutException() throws OperationException {
        try {
            cleanupCrls();
        } catch (Throwable th) {
            LOG.warn("could not cleanup CRLs.{}: {}", th.getClass().getName(), th.getMessage());
        }
    }

    private void cleanupCrls() throws OperationException {
        int numCrls = caInfo.getNumCrls();
        LOG.info("     START cleanupCrls: ca={}, numCrls={}", caInfo.getName(), numCrls);

        boolean successful = false;

        try {
            int numOfRemovedCrls = (numCrls <= 0) ? 0
                    : certstore.cleanupCrls(caInfo.getCertificate(), caInfo.getNumCrls());
            successful = true;
            LOG.info("SUCCESSFUL cleanupCrls: ca={}, numOfRemovedCRLs={}", caInfo.getName(),
                    numOfRemovedCrls);
        } catch (RuntimeException ex) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        } finally {
            if (!successful) {
                LOG.info("    FAILED cleanupCrls: ca={}", caInfo.getName());
            }
        }
    } // method cleanupCrls

    public X509CRL generateCrlOnDemand(final AuditEvent auditEvent) throws OperationException {
        X509CrlSignerEntryWrapper crlSigner = getCrlSigner();
        if (crlSigner == null) {
            throw new OperationException(ErrorCode.NOT_PERMITTED, "CA could not generate CRL");
        }

        if (crlGenInProcess.get()) {
            throw new OperationException(ErrorCode.SYSTEM_UNAVAILABLE, "TRY_LATER");
        }

        crlGenInProcess.set(true);
        try {
            Date thisUpdate = new Date();
            Date nextUpdate = getCrlNextUpdate(thisUpdate);
            if (nextUpdate != null && !nextUpdate.after(thisUpdate)) {
                nextUpdate = null;
            }

            long maxIdOfDeltaCrlCache = certstore.getMaxIdOfDeltaCrlCache(caInfo.getCertificate());
            X509CRL crl = generateCrl(false, thisUpdate, nextUpdate, auditEvent);

            if (crl != null) {
                try {
                    certstore.clearDeltaCrlCache(caInfo.getCertificate(), maxIdOfDeltaCrlCache);
                } catch (Throwable th) {
                    LogUtil.error(LOG, th,
                            "could not clear DeltaCRLCache of CA " + caInfo.getName());
                }
            }

            return crl;
        } finally {
            crlGenInProcess.set(false);
        }
    } // method generateCrlOnDemand

    private X509CRL generateCrl(final boolean deltaCrl, final Date thisUpdate,
            final Date nextUpdate, final AuditEvent auditEvent) throws OperationException {
        X509CrlSignerEntryWrapper crlSigner = getCrlSigner();
        if (crlSigner == null) {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "CRL generation is not allowed");
        }

        LOG.info("     START generateCrl: ca={}, deltaCRL={}, nextUpdate={}",
                caInfo.getName(), deltaCrl, nextUpdate);
        auditEvent.addEventData(new AuditEventData("crlType", deltaCrl ? "DELTA_CRL" : "FULL_CRL"));

        if (nextUpdate != null) {
            String value;
            synchronized (dateFormat) {
                value = dateFormat.format(nextUpdate);
            }
            auditEvent.addEventData(new AuditEventData("nextUpdate", value));
        } else {
            auditEvent.addEventData(new AuditEventData("nextUpdate", "NULL"));
        }

        if (nextUpdate != null) {
            if (nextUpdate.getTime() - thisUpdate.getTime() < 10 * 60 * MS_PER_SECOND) {
                // less than 10 minutes
                throw new OperationException(ErrorCode.CRL_FAILURE,
                        "nextUpdate and thisUpdate are too close");
            }
        }

        CrlControl crlControl = crlSigner.getCrlControl();
        boolean successful = false;

        try {
            ConcurrentContentSigner tmpCrlSigner = crlSigner.getSigner();

            CrlControl control = crlSigner.getCrlControl();

            boolean directCrl = (tmpCrlSigner == null);
            X500Name crlIssuer;
            if (directCrl) {
                crlIssuer = caInfo.getPublicCaInfo().getX500Subject();
            } else {
                crlIssuer = X500Name.getInstance(
                        tmpCrlSigner.getCertificate().getSubjectX500Principal().getEncoded());
            }

            X509v2CRLBuilder crlBuilder = new X509v2CRLBuilder(crlIssuer, thisUpdate);
            if (nextUpdate != null) {
                crlBuilder.setNextUpdate(nextUpdate);
            }

            int startId = 1;
            final int numEntries = 100;

            X509Cert caCert = caInfo.getCertificate();
            List<CertRevInfoWithSerial> revInfos;
            boolean isFirstCrlEntry = true;

            Date notExpireAt;
            if (control.isIncludeExpiredCerts()) {
                notExpireAt = new Date(0);
            } else {
                // 10 minutes buffer
                notExpireAt = new Date(thisUpdate.getTime() - 600L * MS_PER_SECOND);
            }

            do {
                if (deltaCrl) {
                    revInfos = certstore.getCertsForDeltaCrl(caCert, startId, numEntries,
                            control.isOnlyContainsCaCerts(), control.isOnlyContainsUserCerts());
                } else {
                    revInfos = certstore.getRevokedCerts(caCert, notExpireAt, startId, numEntries,
                            control.isOnlyContainsCaCerts(), control.isOnlyContainsUserCerts());
                }

                int maxId = 1;

                for (CertRevInfoWithSerial revInfo : revInfos) {
                    if (revInfo.getId() > maxId) {
                        maxId = revInfo.getId();
                    }

                    CrlReason reason = revInfo.getReason();
                    if (crlControl.isExcludeReason() && reason != CrlReason.REMOVE_FROM_CRL) {
                        reason = CrlReason.UNSPECIFIED;
                    }

                    Date revocationTime = revInfo.getRevocationTime();
                    Date invalidityTime = revInfo.getInvalidityTime();

                    switch (crlControl.getInvalidityDateMode()) {
                    case FORBIDDEN:
                        invalidityTime = null;
                        break;
                    case OPTIONAL:
                        break;
                    case REQUIRED:
                        if (invalidityTime == null) {
                            invalidityTime = revocationTime;
                        }
                        break;
                    default:
                        throw new RuntimeException("unknown TripleState: "
                                + crlControl.getInvalidityDateMode());
                    }

                    if (directCrl || !isFirstCrlEntry) {
                        if (invalidityTime != null) {
                            crlBuilder.addCRLEntry(revInfo.getSerial(), revocationTime,
                                    reason.getCode(), invalidityTime);
                        } else {
                            crlBuilder.addCRLEntry(revInfo.getSerial(), revocationTime,
                                    reason.getCode());
                        }
                        continue;
                    }

                    List<Extension> extensions = new ArrayList<>(3);
                    if (reason != CrlReason.UNSPECIFIED) {
                        Extension ext = createReasonExtension(reason.getCode());
                        extensions.add(ext);
                    }
                    if (invalidityTime != null) {
                        Extension ext = createInvalidityDateExtension(invalidityTime);
                        extensions.add(ext);
                    }

                    Extension ext = createCertificateIssuerExtension(
                            caInfo.getPublicCaInfo().getX500Subject());
                    extensions.add(ext);

                    Extensions asn1Extensions = new Extensions(
                            extensions.toArray(new Extension[0]));
                    crlBuilder.addCRLEntry(revInfo.getSerial(), revocationTime, asn1Extensions);
                    isFirstCrlEntry = false;
                } // end for

                startId = maxId + 1;

            } while (revInfos.size() >= numEntries);
            // end do

            BigInteger crlNumber = caInfo.nextCrlNumber();
            auditEvent.addEventData(new AuditEventData("crlNumber", crlNumber.toString()));

            boolean onlyUserCerts = crlControl.isOnlyContainsUserCerts();
            boolean onlyCaCerts = crlControl.isOnlyContainsCaCerts();
            if (onlyUserCerts && onlyCaCerts) {
                throw new RuntimeException(
                        "should not reach here, onlyUserCerts and onlyCACerts are both true");
            }

            try {
                // AuthorityKeyIdentifier
                byte[] akiValues = directCrl
                        ? caInfo.getPublicCaInfo().getSubjectKeyIdentifer()
                        : crlSigner.getSubjectKeyIdentifier();
                AuthorityKeyIdentifier aki = new AuthorityKeyIdentifier(akiValues);
                crlBuilder.addExtension(Extension.authorityKeyIdentifier, false, aki);

                // add extension CRL Number
                crlBuilder.addExtension(Extension.cRLNumber, false, new ASN1Integer(crlNumber));

                // IssuingDistributionPoint
                if (onlyUserCerts || onlyCaCerts || !directCrl) {
                    IssuingDistributionPoint idp = new IssuingDistributionPoint(
                            (DistributionPointName) null, // distributionPoint,
                            onlyUserCerts, // onlyContainsUserCerts,
                            onlyCaCerts, // onlyContainsCACerts,
                            (ReasonFlags) null, // onlySomeReasons,
                            !directCrl, // indirectCRL,
                            false); // onlyContainsAttributeCerts

                    crlBuilder.addExtension(Extension.issuingDistributionPoint, true, idp);
                }
            } catch (CertIOException ex) {
                LogUtil.error(LOG, ex, "crlBuilder.addExtension");
                throw new OperationException(ErrorCode.INVALID_EXTENSION, ex);
            }

            startId = 1;
            if (!deltaCrl && control.isXipkiCertsetIncluded()) { // XiPKI extension
                /*
                 * Xipki-CrlCertSet ::= SET OF Xipki-CrlCert
                 *
                 * Xipki-CrlCert ::= SEQUENCE {
                 *         serial            INTEGER
                 *         cert        [0] EXPLICIT    Certificate OPTIONAL
                 *         profileName [1] EXPLICIT    UTF8String    OPTIONAL
                 *         }
                 */
                ASN1EncodableVector vector = new ASN1EncodableVector();

                List<SerialWithId> serials;

                do {
                    serials = certstore.getCertSerials(caCert, notExpireAt, startId, numEntries,
                            false, onlyCaCerts, onlyUserCerts);

                    int maxId = 1;
                    for (SerialWithId sid : serials) {
                        if (sid.getId() > maxId) {
                            maxId = sid.getId();
                        }

                        ASN1EncodableVector vec = new ASN1EncodableVector();
                        vec.add(new ASN1Integer(sid.getSerial()));

                        String profileName = null;

                        if (control.isXipkiCertsetCertIncluded()) {
                            X509CertificateInfo certInfo;
                            try {
                                certInfo = certstore.getCertificateInfoForId(caCert, sid.getId());
                            } catch (CertificateException ex) {
                                throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                                        "CertificateException: " + ex.getMessage());
                            }

                            Certificate cert = Certificate.getInstance(
                                    certInfo.getCert().getEncodedCert());

                            vec.add(new DERTaggedObject(true, 0, cert));

                            if (control.isXipkiCertsetProfilenameIncluded()) {
                                profileName = certInfo.getProfileName();
                            }
                        } else if (control.isXipkiCertsetProfilenameIncluded()) {
                            profileName = certstore.getCertProfileForId(caCert, sid.getId());
                        }

                        if (StringUtil.isNotBlank(profileName)) {
                            vec.add(new DERTaggedObject(true, 1, new DERUTF8String(profileName)));
                        }

                        ASN1Sequence certWithInfo = new DERSequence(vec);

                        vector.add(certWithInfo);
                    } // end for

                    startId = maxId + 1;
                } while (serials.size() >= numEntries);
                // end do

                try {
                    crlBuilder.addExtension(ObjectIdentifiers.id_xipki_ext_crlCertset, false,
                            new DERSet(vector));
                } catch (CertIOException ex) {
                    throw new OperationException(ErrorCode.INVALID_EXTENSION,
                            "CertIOException: " + ex.getMessage());
                }
            }

            ConcurrentContentSigner concurrentSigner = (tmpCrlSigner == null)
                    ? caInfo.getSigner(null) : tmpCrlSigner;

            X509CRLHolder crlHolder;
            try {
                crlHolder = concurrentSigner.build(crlBuilder);
            } catch (NoIdleSignerException ex) {
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, "NoIdleSignerException: "
                        + ex.getMessage());
            }

            try {
                X509CRL crl = new X509CRLObject(crlHolder.toASN1Structure());
                caInfo.getCaEntry().setNextCrlNumber(crlNumber.intValue() + 1);
                caInfo.commitNextCrlNo();
                publishCrl(crl);

                successful = true;
                LOG.info("SUCCESSFUL generateCrl: ca={}, crlNumber={}, thisUpdate={}",
                        caInfo.getName(), crlNumber, crl.getThisUpdate());

                if (deltaCrl) {
                    return crl;
                }

                // clean up the CRL
                cleanupCrlsWithoutException();
                return crl;
            } catch (CRLException ex) {
                throw new OperationException(ErrorCode.CRL_FAILURE, ex);
            }
        } finally {
            if (!successful) {
                LOG.info("    FAILED generateCrl: ca={}", caInfo.getName());
            }
        }
    } // method generateCrl

    public X509CertificateInfo generateCertificate(final CertTemplateData certTemplate,
            final boolean requestedByRa, final RequestorInfo requestor, final String user,
            final RequestType reqType, final byte[] transactionId) throws OperationException {
        ParamUtil.requireNonNull("certTemplate", certTemplate);
        final String certprofileName = certTemplate.getCertprofileName();
        final String subjectText = X509Util.getRfc4519Name(certTemplate.getSubject());
        LOG.info("     START generateCertificate: CA={}, profile={}, subject='{}'",
                caInfo.getName(), certprofileName, subjectText);

        boolean successful = false;
        try {
            X509CertificateInfo ret = doGenerateCertificate(certTemplate, requestedByRa, requestor,
                    user, false, reqType, transactionId);
            successful = true;

            if (LOG.isInfoEnabled()) {
                String prefix = ret.isAlreadyIssued() ? "RETURN_OLD_CERT" : "SUCCESSFUL";
                X509CertWithDbId cert = ret.getCert();
                LOG.info("{} generateCertificate: CA={}, profile={}, subject='{}', serialNumber={}",
                        prefix, caInfo.getName(), certprofileName, cert.getSubject(),
                        LogUtil.formatCsn(cert.getCert().getSerialNumber()));
            }
            return ret;
        } catch (RuntimeException ex) {
            LogUtil.warn(LOG, ex);
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        } finally {
            if (!successful) {
                LOG.warn("    FAILED generateCertificate: CA={}, profile={}, subject='{}'",
                        caInfo.getName(), certprofileName, subjectText);
            }
        }
    } // method generateCertificate

    public X509CertificateInfo regenerateCertificate(final CertTemplateData certTemplate,
            final boolean requestedByRa, final RequestorInfo requestor, final String user,
            final RequestType reqType, final byte[] transactionId) throws OperationException {
        ParamUtil.requireNonNull("certTemplate", certTemplate);
        final String certprofileName = certTemplate.getCertprofileName();
        final String subjectText = X509Util.getRfc4519Name(certTemplate.getSubject());
        LOG.info("     START regenerateCertificate: CA={}, profile={}, subject='{}'",
                caInfo.getName(), certprofileName, subjectText);

        boolean successful = false;

        try {
            X509CertificateInfo ret = doGenerateCertificate(certTemplate, requestedByRa, requestor,
                    user, false, reqType, transactionId);
            successful = true;
            if (LOG.isInfoEnabled()) {
                X509CertWithDbId cert = ret.getCert();
                LOG.info("SUCCESSFUL generateCertificate: CA={}, profile={}, subject='{}', {}={}",
                    caInfo.getName(), certprofileName, cert.getSubject(), "serialNumber",
                    LogUtil.formatCsn(cert.getCert().getSerialNumber()));
            }

            return ret;
        } catch (RuntimeException ex) {
            LogUtil.warn(LOG, ex);
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        } finally {
            if (!successful) {
                LOG.warn("    FAILED regenerateCertificate: CA={}, profile={}, subject='{}'",
                        caInfo.getName(), certprofileName, subjectText);
            }
        }
    } // method regenerateCertificate

    public boolean publishCertificate(final X509CertificateInfo certInfo) {
        return doPublishCertificate(certInfo) == 0;
    }

    /**
     *
     * @param certInfo certificate to be published.
     * @return 0 for published successfuly, 1 if could not be published to CA certstore and
     *     any publishers, 2 if could be published to CA certstore but not to all publishers.
     */
    private int doPublishCertificate(final X509CertificateInfo certInfo) {
        ParamUtil.requireNonNull("certInfo", certInfo);
        if (certInfo.isAlreadyIssued()) {
            return 0;
        }

        if (!certstore.addCertificate(certInfo)) {
            return 1;
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            if (!publisher.isAsyn()) {
                boolean successful;
                try {
                    successful = publisher.certificateAdded(certInfo);
                } catch (RuntimeException ex) {
                    successful = false;
                    LogUtil.warn(LOG, ex, "could not publish certificate to the publisher "
                            + publisher.getName());
                }

                if (successful) {
                    continue;
                }
            } // end if

            Integer certId = certInfo.getCert().getCertId();
            try {
                certstore.addToPublishQueue(publisher.getName(), certId.intValue(),
                        caInfo.getCertificate());
            } catch (Throwable th) {
                LogUtil.error(LOG, th, "could not add entry to PublishQueue");
                return 2;
            }
        } // end for

        return 0;
    } // method doPublishCertificate

    public boolean republishCertificates(final List<String> publisherNames) {
        List<IdentifiedX509CertPublisher> publishers;
        if (publisherNames == null) {
            publishers = getPublishers();
        } else {
            publishers = new ArrayList<>(publisherNames.size());

            for (String publisherName : publisherNames) {
                IdentifiedX509CertPublisher publisher = null;
                for (IdentifiedX509CertPublisher p : getPublishers()) {
                    if (p.getName().equals(publisherName)) {
                        publisher = p;
                        break;
                    }
                }

                if (publisher == null) {
                    throw new IllegalArgumentException(
                            "could not find publisher " + publisherName + " for CA "
                            + caInfo.getName());
                }
                publishers.add(publisher);
            }
        } // end if

        if (CollectionUtil.isEmpty(publishers)) {
            return true;
        }

        CaStatus status = caInfo.getStatus();

        caInfo.setStatus(CaStatus.INACTIVE);

        boolean onlyRevokedCerts = true;
        for (IdentifiedX509CertPublisher publisher : publishers) {
            if (publisher.publishsGoodCert()) {
                onlyRevokedCerts = false;
            }

            String name = publisher.getName();
            try {
                LOG.info("clearing PublishQueue for publisher {}", name);
                certstore.clearPublishQueue(this.caInfo.getCertificate(), name);
                LOG.info(" cleared PublishQueue for publisher {}", name);
            } catch (OperationException ex) {
                LogUtil.error(LOG, ex, "could not clear PublishQueue for publisher");
            }
        } // end for

        try {
            X509Cert caCert = caInfo.getCertificate();
            for (IdentifiedX509CertPublisher publisher : publishers) {
                boolean successful = publisher.caAdded(caCert);
                if (!successful) {
                    LOG.error("republish CA certificate {} to publisher {} failed",
                            caInfo.getName(), publisher.getName());
                    return false;
                }
            }

            Date notExpiredAt = null;

            int startId = 1;
            int numEntries = 100;

            List<SerialWithId> serials;
            int sum = 0;
            do {
                try {
                    serials = certstore.getCertSerials(caCert, notExpiredAt, startId, numEntries,
                            onlyRevokedCerts, false, false);
                } catch (OperationException ex) {
                    LogUtil.error(LOG, ex);
                    return false;
                }

                int maxId = 1;
                for (SerialWithId sid : serials) {
                    if (sid.getId() > maxId) {
                        maxId = sid.getId();
                    }

                    X509CertificateInfo certInfo;

                    try {
                        certInfo = certstore.getCertificateInfoForId(caCert, sid.getId());
                    } catch (OperationException | CertificateException ex) {
                        LogUtil.error(LOG, ex);
                        return false;
                    }

                    for (IdentifiedX509CertPublisher publisher : publishers) {
                        if (!certInfo.isRevoked() && !publisher.publishsGoodCert()) {
                            continue;
                        }

                        boolean successful = publisher.certificateAdded(certInfo);
                        if (!successful) {
                            LOG.error("republish certificate serial={} to publisher {} failed",
                                    LogUtil.formatCsn(sid.getSerial()), publisher.getName());
                            return false;
                        }
                    }
                } // end for

                startId = maxId + 1;

                sum += serials.size();
                System.out.println("CA " + caInfo.getName() + " republished " + sum
                        + " certificates");
            } while (serials.size() >= numEntries);
            // end do

            if (caInfo.getRevocationInfo() != null) {
                for (IdentifiedX509CertPublisher publisher : publishers) {
                    boolean successful = publisher.caRevoked(caInfo.getCertificate(),
                            caInfo.getRevocationInfo());
                    if (!successful) {
                        LOG.error("republishing CA revocation to publisher {} failed",
                                publisher.getName());
                        return false;
                    }
                }
            } // end if

            return true;
        } finally {
            caInfo.setStatus(status);
        }
    } // method republishCertificates

    public boolean clearPublishQueue(final List<String> publisherNames) throws CaMgmtException {
        if (publisherNames == null) {
            try {
                certstore.clearPublishQueue(caInfo.getCertificate(), null);
                return true;
            } catch (OperationException ex) {
                throw new CaMgmtException(ex.getMessage(), ex);
            }
        }

        for (String publisherName : publisherNames) {
            try {
                certstore.clearPublishQueue(caInfo.getCertificate(), publisherName);
            } catch (OperationException ex) {
                throw new CaMgmtException(ex.getMessage(), ex);
            }
        }

        return true;
    } // method clearPublishQueue

    public boolean publishCertsInQueue() {
        boolean allSuccessful = true;
        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            if (!publishCertsInQueue(publisher)) {
                allSuccessful = false;
            }
        }

        return allSuccessful;
    }

    private boolean publishCertsInQueue(final IdentifiedX509CertPublisher publisher) {
        ParamUtil.requireNonNull("publisher", publisher);
        X509Cert caCert = caInfo.getCertificate();

        final int numEntries = 500;

        while (true) {
            List<Integer> certIds;
            try {
                certIds = certstore.getPublishQueueEntries(caCert, publisher.getName(), numEntries);
            } catch (OperationException ex) {
                LogUtil.error(LOG, ex);
                return false;
            }

            if (CollectionUtil.isEmpty(certIds)) {
                break;
            }

            for (Integer certId : certIds) {
                X509CertificateInfo certInfo;

                try {
                    certInfo = certstore.getCertificateInfoForId(caCert, certId);
                } catch (OperationException | CertificateException ex) {
                    LogUtil.error(LOG, ex);
                    return false;
                }

                boolean successful = publisher.certificateAdded(certInfo);
                if (!successful) {
                    LOG.error("republishing certificate id={} failed", certId);
                    return false;
                }

                try {
                    certstore.removeFromPublishQueue(publisher.getName(), certId);
                } catch (OperationException ex) {
                    LogUtil.warn(LOG, ex, "could not remove republished cert id=" + certId
                            + " and publisher=" + publisher.getName());
                    continue;
                }
            } // end for
        } // end while

        return true;
    } // method publishCertsInQueue

    private boolean publishCrl(final X509CRL crl) {
        X509Cert caCert = caInfo.getCertificate();
        if (!certstore.addCrl(caCert, crl)) {
            return false;
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            try {
                publisher.crlAdded(caCert, crl);
            } catch (RuntimeException ex) {
                LogUtil.error(LOG, ex, "could not publish CRL to the publisher "
                        + publisher.getName());
            }
        } // end for

        return true;
    } // method publishCrl

    public X509CertWithRevocationInfo revokeCertificate(final BigInteger serialNumber,
            final CrlReason reason, final Date invalidityTime) throws OperationException {
        if (caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serialNumber)) {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "insufficient permission to revoke CA certificate");
        }

        CrlReason tmpReason = reason;
        if (tmpReason == null) {
            tmpReason = CrlReason.UNSPECIFIED;
        }

        switch (tmpReason) {
        case CA_COMPROMISE:
        case AA_COMPROMISE:
        case REMOVE_FROM_CRL:
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "Insufficient permission revoke certificate with reason "
                    + tmpReason.getDescription());
        case UNSPECIFIED:
        case KEY_COMPROMISE:
        case AFFILIATION_CHANGED:
        case SUPERSEDED:
        case CESSATION_OF_OPERATION:
        case CERTIFICATE_HOLD:
        case PRIVILEGE_WITHDRAWN:
            break;
        default:
            throw new RuntimeException("unknown CRL reason " + tmpReason);
        } // switch (reason)

        return doRevokeCertificate(serialNumber, reason, invalidityTime, false);
    } // method revokeCertificate

    public X509CertWithDbId unrevokeCertificate(final BigInteger serialNumber)
    throws OperationException {
        if (caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serialNumber)) {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "insufficient permission unrevoke CA certificate");
        }

        return doUnrevokeCertificate(serialNumber, false);
    } // method unrevokeCertificate

    public X509CertWithDbId removeCertificate(final BigInteger serialNumber)
    throws OperationException {
        if (caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serialNumber)) {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "insufficient permission remove CA certificate");
        }

        return doRemoveCertificate(serialNumber);
    } // method removeCertificate

    private X509CertWithDbId doRemoveCertificate(final BigInteger serialNumber)
    throws OperationException {
        X509CertWithRevocationInfo certWithRevInfo =
                certstore.getCertWithRevocationInfo(caInfo.getCertificate(), serialNumber);
        if (certWithRevInfo == null) {
            return null;
        }

        boolean successful = true;
        X509CertWithDbId certToRemove = certWithRevInfo.getCert();
        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            boolean singleSuccessful;
            try {
                singleSuccessful = publisher.certificateRemoved(caInfo.getCertificate(),
                        certToRemove);
            } catch (RuntimeException ex) {
                singleSuccessful = false;
                LogUtil.warn(LOG, ex, "could not remove certificate to the publisher "
                        + publisher.getName());
            }

            if (singleSuccessful) {
                continue;
            }

            successful = false;
            X509Certificate cert = certToRemove.getCert();
            if (LOG.isErrorEnabled()) {
                LOG.error("removing certificate issuer='{}', serial={}, subject='{}' from publisher"
                    + " {} failed.", X509Util.getRfc4519Name(cert.getIssuerX500Principal()),
                    LogUtil.formatCsn(cert.getSerialNumber()),
                    X509Util.getRfc4519Name(cert.getSubjectX500Principal()), publisher.getName());
            }
        } // end for

        if (!successful) {
            return null;
        }

        certstore.removeCertificate(caInfo.getCertificate(), serialNumber);
        return certToRemove;
    } // method doRemoveCertificate

    private X509CertWithRevocationInfo doRevokeCertificate(final BigInteger serialNumber,
            final CrlReason reason, final Date invalidityTime, final boolean force)
    throws OperationException {
        ParamUtil.requireNonNull("reason", reason);
        LOG.info(
            "     START revokeCertificate: ca={}, serialNumber={}, reason={}, invalidityTime={}",
            caInfo.getName(), LogUtil.formatCsn(serialNumber), reason.getDescription(),
            invalidityTime);

        X509CertWithRevocationInfo revokedCert = null;

        CertRevocationInfo revInfo = new CertRevocationInfo(reason, new Date(), invalidityTime);
        revokedCert = certstore.revokeCertificate(caInfo.getCertificate(), serialNumber, revInfo,
                force, shouldPublishToDeltaCrlCache());
        if (revokedCert == null) {
            return null;
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            if (!publisher.isAsyn()) {
                boolean successful;
                try {
                    successful = publisher.certificateRevoked(caInfo.getCertificate(),
                            revokedCert.getCert(), revokedCert.getCertprofile(),
                            revokedCert.getRevInfo());
                } catch (RuntimeException ex) {
                    successful = false;
                    LogUtil.error(LOG, ex,
                            "could not publish revocation of certificate to the publisher "
                            + publisher.getName());
                }

                if (successful) {
                    continue;
                }
            } // end if

            Integer certId = revokedCert.getCert().getCertId();
            try {
                certstore.addToPublishQueue(publisher.getName(), certId.intValue(),
                        caInfo.getCertificate());
            } catch (Throwable th) {
                LogUtil.error(LOG, th, "could not add entry to PublishQueue");
            }
        } // end for

        String resultText = (revokedCert == null) ? "CERT_NOT_EXIST" : "REVOKED";
        if (LOG.isInfoEnabled()) {
            LOG.info("SUCCESSFUL revokeCertificate: ca={}, serialNumber={}, reason={},"
                + " invalidityTime={}, revocationResult={}",
                caInfo.getName(), LogUtil.formatCsn(serialNumber), reason.getDescription(),
                invalidityTime, resultText);
        }

        return revokedCert;
    } // method doRevokeCertificate

    private X509CertWithRevocationInfo doRevokeSuspendedCert(final BigInteger serialNumber,
            final CrlReason reason)
    throws OperationException {
        ParamUtil.requireNonNull("serialNumber", serialNumber);
        ParamUtil.requireNonNull("reason", reason);
        if (LOG.isInfoEnabled()) {
            LOG.info("     START revokeSuspendedCert: ca={}, serialNumber={}, reason={}",
                caInfo.getName(), LogUtil.formatCsn(serialNumber), reason.getDescription());
        }

        X509CertWithRevocationInfo revokedCert = certstore.revokeSuspendedCert(
                caInfo.getCertificate(), serialNumber, reason, shouldPublishToDeltaCrlCache());
        if (revokedCert == null) {
            return null;
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            if (!publisher.isAsyn()) {
                boolean successful;
                try {
                    successful = publisher.certificateRevoked(caInfo.getCertificate(),
                            revokedCert.getCert(), revokedCert.getCertprofile(),
                            revokedCert.getRevInfo());
                } catch (RuntimeException ex) {
                    successful = false;
                    LogUtil.error(LOG, ex,
                            "could not publish revocation of certificate to the publisher "
                            + publisher.getName());
                }

                if (successful) {
                    continue;
                }
            } // end if

            Integer certId = revokedCert.getCert().getCertId();
            try {
                certstore.addToPublishQueue(publisher.getName(), certId.intValue(),
                        caInfo.getCertificate());
            } catch (Throwable th) {
                LogUtil.error(LOG, th, "could not add entry to PublishQueue");
            }
        } // end for

        if (LOG.isInfoEnabled()) {
            LOG.info("SUCCESSFUL revokeSuspendedCert: ca={}, serialNumber={}, reason={}",
                caInfo.getName(), LogUtil.formatCsn(serialNumber), reason.getDescription());
        }

        return revokedCert;
    } // method doRevokeSuspendedCert

    private X509CertWithDbId doUnrevokeCertificate(final BigInteger serialNumber,
            final boolean force) throws OperationException {
        String hexSerial = LogUtil.formatCsn(serialNumber);
        LOG.info("     START unrevokeCertificate: ca={}, serialNumber={}", caInfo.getName(),
                hexSerial);

        X509CertWithDbId unrevokedCert = null;

        unrevokedCert = certstore.unrevokeCertificate(
                caInfo.getCertificate(), serialNumber, force, shouldPublishToDeltaCrlCache());
        if (unrevokedCert == null) {
            return null;
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            if (!publisher.isAsyn()) {
                boolean successful;
                try {
                    successful = publisher.certificateUnrevoked(caInfo.getCertificate(),
                            unrevokedCert);
                } catch (RuntimeException ex) {
                    successful = false;
                    LogUtil.error(LOG, ex,
                            "could not publish unrevocation of certificate to the publisher "
                            + publisher.getName());
                }

                if (successful) {
                    continue;
                }
            } // end if

            Integer certId = unrevokedCert.getCertId();
            try {
                certstore.addToPublishQueue(publisher.getName(), certId.intValue(),
                        caInfo.getCertificate());
            } catch (Throwable th) {
                LogUtil.error(LOG, th, "could not add entry to PublishQueue");
            }
        } // end for

        String resultText = (unrevokedCert == null) ? "CERT_NOT_EXIST" : "UNREVOKED";
        LOG.info("SUCCESSFUL unrevokeCertificate: ca={}, serialNumber={}, revocationResult={}",
                caInfo.getName(), hexSerial, resultText);

        return unrevokedCert;
    } // doUnrevokeCertificate

    private boolean shouldPublishToDeltaCrlCache() {
        X509CrlSignerEntryWrapper crlSigner = getCrlSigner();
        if (crlSigner == null) {
            return false;
        }

        CrlControl control = crlSigner.getCrlControl();
        if (control.getUpdateMode() == UpdateMode.onDemand) {
            return false;
        }

        int deltaCrlInterval = control.getDeltaCrlIntervals();
        if (deltaCrlInterval == 0 || deltaCrlInterval >= control.getFullCrlIntervals()) {
            return false;
        }

        return true;
    } // method shouldPublishToDeltaCrlCache

    public void revoke(final CertRevocationInfo revocationInfo) throws OperationException {
        ParamUtil.requireNonNull("revocationInfo", revocationInfo);

        caInfo.setRevocationInfo(revocationInfo);
        if (caInfo.isSelfSigned()) {
            doRevokeCertificate(caInfo.getSerialNumber(), revocationInfo.getReason(),
                revocationInfo.getInvalidityTime(), true);
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            try {
                boolean successful = publisher.caRevoked(caInfo.getCertificate(),
                        revocationInfo);
                if (!successful) {
                    throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                            "publishing CA revocation failed");
                }
            } catch (RuntimeException ex) {
                final String message = "could not publish revocation of CA to the publisher "
                        + publisher.getName();
                LogUtil.error(LOG, ex, message);
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, message);
            }
        } // end for
    } // method revoke

    public void unrevoke() throws OperationException {
        caInfo.setRevocationInfo(null);
        if (caInfo.isSelfSigned()) {
            doUnrevokeCertificate(caInfo.getSerialNumber(), true);
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            try {
                boolean successful = publisher.caUnrevoked(caInfo.getCertificate());
                if (!successful) {
                    throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                            "publishing CA revocation failed");
                }
            } catch (RuntimeException ex) {
                final String message = "could not publish revocation of CA to the publisher "
                        + publisher.getName();
                LogUtil.error(LOG, ex, message);
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, message);
            }
        } // end for
    } // method unrevoke

    private List<IdentifiedX509CertPublisher> getPublishers() {
        return caManager.getIdentifiedPublishersForCa(caInfo.getName());
    }

    private X509CertificateInfo doGenerateCertificate(final CertTemplateData certTemplate,
            final boolean requestedByRa, final RequestorInfo requestor, final String user,
            final boolean keyUpdate, final RequestType reqType, final byte[] transactionId)
    throws OperationException {
        ParamUtil.requireNonNull("certTemplate", certTemplate);
        if (caInfo.getRevocationInfo() != null) {
            throw new OperationException(ErrorCode.NOT_PERMITTED, "CA is revoked");
        }

        IdentifiedX509Certprofile certprofile = getX509Certprofile(
                certTemplate.getCertprofileName());

        if (certprofile == null) {
            throw new OperationException(ErrorCode.UNKNOWN_CERT_PROFILE,
                    "unknown cert profile " + certTemplate.getCertprofileName());
        }

        ConcurrentContentSigner signer = caInfo.getSigner(certprofile.getSignatureAlgorithms());
        if (signer == null) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                    "CA does not support any signature algorithm restricted by the cert profile");
        }

        final String certprofileName = certprofile.getName();
        if (certprofile.getVersion() != X509CertVersion.v3) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                    "unknown cert version " + certprofile);
        }

        if (certprofile.isOnlyForRa() && !requestedByRa) {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "profile " + certprofileName + " not applied to non-RA");
        }

        X500Name requestedSubject = removeEmptyRdns(certTemplate.getSubject());

        if (!certprofile.isSerialNumberInReqPermitted()) {
            RDN[] rdns = requestedSubject.getRDNs(ObjectIdentifiers.DN_SN);
            if (rdns != null && rdns.length > 0) {
                throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE,
                        "subjectDN SerialNumber in request is not permitted");
            }
        }

        Date grantedNotBefore = certprofile.getNotBefore(certTemplate.getNotBefore());
        if (grantedNotBefore == null) {
            grantedNotBefore = new Date();
        }

        if (certprofile.hasMidnightNotBefore()) {
            grantedNotBefore = setToMidnight(grantedNotBefore, certprofile.getTimezone());
        }

        if (grantedNotBefore.before(caInfo.getNotBefore())) {
            grantedNotBefore = caInfo.getNotBefore();
            if (certprofile.hasMidnightNotBefore()) {
                grantedNotBefore = setToMidnight(new Date(grantedNotBefore.getTime() + DAY_IN_MS),
                        certprofile.getTimezone());
            }
        }

        long time = caInfo.getNoNewCertificateAfter();
        if (grantedNotBefore.getTime() > time) {
            throw new OperationException(ErrorCode.NOT_PERMITTED,
                    "CA is not permitted to issue certifate after " + new Date(time));
        }

        SubjectPublicKeyInfo grantedPublicKeyInfo;
        try {
            grantedPublicKeyInfo = X509Util.toRfc3279Style(certTemplate.getPublicKeyInfo());
        } catch (InvalidKeySpecException ex) {
            throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE,
                    "invalid SubjectPublicKeyInfo");
        }

        // public key
        try {
            grantedPublicKeyInfo = certprofile.checkPublicKey(grantedPublicKeyInfo);
        } catch (BadCertTemplateException ex) {
            throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, ex);
        }

        Date gsmckFirstNotBefore = null;
        if (certprofile.getSpecialCertprofileBehavior()
                == SpecialX509CertprofileBehavior.gematik_gSMC_K) {
            gsmckFirstNotBefore = grantedNotBefore;

            RDN[] cnRdns = requestedSubject.getRDNs(ObjectIdentifiers.DN_CN);
            if (cnRdns != null && cnRdns.length > 0) {
                String requestedCn = X509Util.rdnValueToString(cnRdns[0].getFirst().getValue());
                Long gsmckFirstNotBeforeInSecond =
                        certstore.getNotBeforeOfFirstCertStartsWithCommonName(requestedCn,
                                certprofileName);
                if (gsmckFirstNotBeforeInSecond != null) {
                    gsmckFirstNotBefore = new Date(gsmckFirstNotBeforeInSecond * MS_PER_SECOND);
                }

                // append the commonName with '-' + yyyyMMdd
                SimpleDateFormat dateF = new SimpleDateFormat("yyyyMMdd");
                dateF.setTimeZone(new SimpleTimeZone(0, "Z"));
                String yyyyMMdd = dateF.format(gsmckFirstNotBefore);
                String suffix = "-" + yyyyMMdd;

                // append the -yyyyMMdd to the commonName
                RDN[] rdns = requestedSubject.getRDNs();
                for (int i = 0; i < rdns.length; i++) {
                    if (ObjectIdentifiers.DN_CN.equals(rdns[i].getFirst().getType())) {
                        rdns[i] = new RDN(ObjectIdentifiers.DN_CN,
                                new DERUTF8String(requestedCn + suffix));
                    }
                }
                requestedSubject = new X500Name(rdns);
            } // end if
        } // end if

        // subject
        SubjectInfo subjectInfo;
        try {
            subjectInfo = certprofile.getSubject(requestedSubject);
        } catch (CertprofileException ex) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                    "exception in cert profile " + certprofileName);
        } catch (BadCertTemplateException ex) {
            throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, ex);
        }

        X500Name grantedSubject = subjectInfo.getGrantedSubject();

        // make sure that empty subject is not permitted
        ASN1ObjectIdentifier[] attrTypes = grantedSubject.getAttributeTypes();
        if (attrTypes == null || attrTypes.length == 0) {
            throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE,
                    "empty subject is not permitted");
        }

        // make sure that the grantedSubject does not equal the CA's subject
        if (X509Util.canonicalizName(grantedSubject).equals(
                caInfo.getPublicCaInfo().getC14nSubject())) {
            throw new OperationException(ErrorCode.ALREADY_ISSUED,
                    "certificate with the same subject as CA is not allowed");
        }

        boolean duplicateKeyPermitted = caInfo.isDuplicateKeyPermitted();
        if (duplicateKeyPermitted && !certprofile.isDuplicateKeyPermitted()) {
            duplicateKeyPermitted = false;
        }

        boolean duplicateSubjectPermitted = caInfo.isDuplicateSubjectPermitted();
        if (duplicateSubjectPermitted && !certprofile.isDuplicateSubjectPermitted()) {
            duplicateSubjectPermitted = false;
        }

        long fpSubject = X509Util.fpCanonicalizedName(grantedSubject);
        String grandtedSubjectText = X509Util.getRfc4519Name(grantedSubject);

        byte[] subjectPublicKeyData = grantedPublicKeyInfo.getPublicKeyData().getBytes();
        long fpPublicKey = FpIdCalculator.hash(subjectPublicKeyData);

        if (keyUpdate) {
            CertStatus certStatus = certstore.getCertStatusForSubject(caInfo.getCertificate(),
                    grantedSubject);
            if (certStatus == CertStatus.Revoked) {
                throw new OperationException(ErrorCode.CERT_REVOKED);
            } else if (certStatus == CertStatus.Unknown) {
                throw new OperationException(ErrorCode.UNKNOWN_CERT);
            }
        } else {
            if (!duplicateKeyPermitted) {
                if (certstore.isCertForKeyIssued(caInfo.getCertificate(), fpPublicKey)) {
                    throw new OperationException(ErrorCode.ALREADY_ISSUED,
                            "certificate for the given public key already issued");
                }
            }

            if (!duplicateSubjectPermitted) {
                final boolean incSerial = certprofile.incSerialNumberIfSubjectExists();
                final boolean certIssued = certstore.isCertForSubjectIssued(caInfo.getCertificate(),
                            fpSubject);
                if (certIssued && !incSerial) {
                    throw new OperationException(ErrorCode.ALREADY_ISSUED,
                            "certificate for the given subject " + grandtedSubjectText
                            + " already issued");
                }

                if (certIssued) {
                    String latestSn;
                    try {
                        Object[] objs = incSerialNumber(certprofile, grantedSubject, null);
                        latestSn = certstore.getLatestSerialNumber((X500Name) objs[0]);
                    } catch (BadFormatException ex) {
                        throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
                    }

                    boolean foundUniqueSubject = false;
                    // maximal 100 tries
                    for (int i = 0; i < 100; i++) {
                        try {
                            Object[] objs = incSerialNumber(certprofile, grantedSubject, latestSn);
                            grantedSubject = (X500Name) objs[0];
                            latestSn = (String) objs[1];
                        } catch (BadFormatException ex) {
                            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
                        }

                        foundUniqueSubject = !certstore.isCertForSubjectIssued(
                                caInfo.getCertificate(),
                                X509Util.fpCanonicalizedName(grantedSubject));
                        if (foundUniqueSubject) {
                            break;
                        }
                    }

                    if (!foundUniqueSubject) {
                        throw new OperationException(ErrorCode.ALREADY_ISSUED,
                            "certificate for the given subject " + grandtedSubjectText
                            + " and profile " + certprofileName
                            + " already issued, and could not create new unique serial number");
                    }
                } // end if (certIssued)
            }
        } // end if(keyUpdate)

        try {
            boolean addedCertInProcess = certstore.addCertInProcess(fpPublicKey, fpSubject);
            if (!addedCertInProcess) {
                throw new OperationException(ErrorCode.ALREADY_ISSUED,
                        "certificate with the given subject " + grandtedSubjectText
                        + " and/or public key already in process");
            }

            StringBuilder msgBuilder = new StringBuilder();

            if (subjectInfo.getWarning() != null) {
                msgBuilder.append(", ").append(subjectInfo.getWarning());
            }

            CertValidity validity = certprofile.getValidity();

            if (validity == null) {
                validity = caInfo.getMaxValidity();
            } else if (validity.compareTo(caInfo.getMaxValidity()) > 0) {
                validity = caInfo.getMaxValidity();
            }

            Date maxNotAfter = validity.add(grantedNotBefore);
            if (maxNotAfter.getTime() > MAX_CERT_TIME_MS) {
                maxNotAfter = new Date(MAX_CERT_TIME_MS);
            }

            // CHECKSTYLE:SKIP
            Date origMaxNotAfter = maxNotAfter;

            if (certprofile.getSpecialCertprofileBehavior()
                    == SpecialX509CertprofileBehavior.gematik_gSMC_K) {
                String str = certprofile.getParameter(
                        SpecialX509CertprofileBehavior.PARAMETER_MAXLIFTIME);
                long maxLifetimeInDays = Long.parseLong(str);
                Date maxLifetime = new Date(gsmckFirstNotBefore.getTime()
                        + maxLifetimeInDays * DAY_IN_MS - MS_PER_SECOND);
                if (maxNotAfter.after(maxLifetime)) {
                    maxNotAfter = maxLifetime;
                }
            }

            Date grantedNotAfter = certTemplate.getNotAfter();
            if (grantedNotAfter != null) {
                if (grantedNotAfter.after(maxNotAfter)) {
                    grantedNotAfter = maxNotAfter;
                    msgBuilder.append(", NotAfter modified");
                }
            } else {
                grantedNotAfter = maxNotAfter;
            }

            if (grantedNotAfter.after(caInfo.getNotAfter())) {
                ValidityMode mode = caInfo.getValidityMode();
                if (mode == ValidityMode.CUTOFF) {
                    grantedNotAfter = caInfo.getNotAfter();
                } else if (mode == ValidityMode.STRICT) {
                    throw new OperationException(ErrorCode.NOT_PERMITTED,
                            "notAfter outside of CA's validity is not permitted");
                } else if (mode == ValidityMode.LAX) {
                    // permitted
                } else {
                    throw new RuntimeException(
                            "should not reach here, unknown CA ValidityMode " + mode);
                } // end if (mode)
            } // end if (notAfter)

            if (certprofile.hasMidnightNotBefore() && !maxNotAfter.equals(origMaxNotAfter)) {
                Calendar cal = Calendar.getInstance(certprofile.getTimezone());
                cal.setTime(new Date(grantedNotAfter.getTime() - DAY_IN_MS));
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                cal.set(Calendar.MILLISECOND, 0);
                grantedNotAfter = cal.getTime();
            }

            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    caInfo.getPublicCaInfo().getX500Subject(), caInfo.nextSerial(),
                    grantedNotBefore, grantedNotAfter, grantedSubject, grantedPublicKeyInfo);

            X509CertificateInfo ret;

            try {
                X509CrlSignerEntryWrapper crlSigner = getCrlSigner();
                X509Certificate crlSignerCert = (crlSigner == null) ? null : crlSigner.getCert();

                ExtensionValues extensionTuples = certprofile.getExtensions(requestedSubject,
                        certTemplate.getExtensions(), grantedPublicKeyInfo,
                        caInfo.getPublicCaInfo(), crlSignerCert, grantedNotBefore, grantedNotAfter);
                if (extensionTuples != null) {
                    for (ASN1ObjectIdentifier extensionType : extensionTuples.getExtensionTypes()) {
                        ExtensionValue extValue = extensionTuples.getExtensionValue(extensionType);
                        certBuilder.addExtension(extensionType, extValue.isCritical(),
                                extValue.getValue());
                    }
                }

                Certificate bcCert = buildCert(signer, certBuilder);

                byte[] encodedCert = bcCert.getEncoded();
                int maxCertSize = certprofile.getMaxCertSize();
                if (maxCertSize > 0) {
                    int certSize = encodedCert.length;
                    if (certSize > maxCertSize) {
                        throw new OperationException(ErrorCode.NOT_PERMITTED,
                            String.format("certificate exceeds the maximal allowed size: %d > %d",
                                certSize, maxCertSize));
                    }
                }

                X509Certificate cert = (X509Certificate) cf.engineGenerateCertificate(
                        new ByteArrayInputStream(encodedCert));
                if (!verifySignature(cert)) {
                    throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                            "could not verify the signature of generated certificate");
                }

                X509CertWithDbId certWithMeta = new X509CertWithDbId(cert, encodedCert);

                ret = new X509CertificateInfo(certWithMeta, caInfo.getCertificate(),
                        subjectPublicKeyData, certprofileName);
                ret.setUser(user);
                ret.setRequestor(requestor);
                ret.setReqType(reqType);
                ret.setTransactionId(transactionId);
                ret.setRequestedSubject(requestedSubject);

                if (doPublishCertificate(ret) == 1) {
                    throw new OperationException(ErrorCode.SYSTEM_FAILURE,
                            "could not save certificate");
                }
            } catch (BadCertTemplateException ex) {
                throw new OperationException(ErrorCode.BAD_CERT_TEMPLATE, ex);
            } catch (Throwable th) {
                LogUtil.error(LOG, th, "could not generate certificate");
                throw new OperationException(ErrorCode.SYSTEM_FAILURE, th);
            }

            if (msgBuilder.length() > 2) {
                ret.setWarningMessage(msgBuilder.substring(2));
            }

            return ret;
        } finally {
            try {
                certstore.delteCertInProcess(fpPublicKey, fpSubject);
            } catch (OperationException ex) {
                LOG.error("could not delete CertInProcess (fpPublicKey={}, fpSubject={}): {}",
                        fpPublicKey, fpSubject, ex.getMessage());
            }
        }
    } // method doGenerateCertificate

    private Certificate buildCert(final ConcurrentContentSigner signer,
            final X509v3CertificateBuilder certBuilder) throws OperationException {
        ParamUtil.requireNonNull("signer",signer);
        try {
            return signer.build(certBuilder).toASN1Structure();
        } catch (NoIdleSignerException ex) {
            throw new OperationException(ErrorCode.SYSTEM_FAILURE, ex);
        }
    }

    public IdentifiedX509Certprofile getX509Certprofile(final String certprofileName) {
        if (certprofileName == null) {
            return null;
        }

        Set<String> profileNames = caManager.getCertprofilesForCa(caInfo.getName());
        if (profileNames == null || !profileNames.contains(certprofileName)) {
            return null;
        }

        return caManager.getIdentifiedCertprofile(certprofileName);
    } // method getX509Certprofile

    public boolean supportsCertProfile(final String certprofileLocalName) {
        ParamUtil.requireNonNull("certprofileLocalName", certprofileLocalName);
        Set<String> profileNames = caManager.getCertprofilesForCa(caInfo.getName());
        return profileNames.contains(certprofileLocalName);
    }

    public CmpRequestorInfo getRequestor(final X500Name requestorSender) {
        if (requestorSender == null) {
            return null;
        }

        Set<CaHasRequestorEntry> requestorEntries =
                caManager.getCmpRequestorsForCa(caInfo.getName());
        if (CollectionUtil.isEmpty(requestorEntries)) {
            return null;
        }

        for (CaHasRequestorEntry m : requestorEntries) {
            CmpRequestorEntryWrapper entry = caManager.getCmpRequestorWrapper(m.getRequestorName());
            if (entry.getCert().getSubjectAsX500Name().equals(requestorSender)) {
                return new CmpRequestorInfo(m, entry.getCert());
            }
        }

        return null;
    } // method getRequestor

    public CaManagerImpl getCaManager() {
        return caManager;
    }

    private Date getCrlNextUpdate(final Date thisUpdate) {
        ParamUtil.requireNonNull("thisUpdate", thisUpdate);
        CrlControl control = getCrlSigner().getCrlControl();
        if (control.getUpdateMode() != UpdateMode.interval) {
            return null;
        }

        int intervalsTillNextCrl = 0;
        for (int i = 1;; i++) {
            if (i % control.getFullCrlIntervals() == 0) {
                intervalsTillNextCrl = i;
                break;
            } else if (!control.isExtendedNextUpdate() && control.getDeltaCrlIntervals() > 0) {
                if (i % control.getDeltaCrlIntervals() == 0) {
                    intervalsTillNextCrl = i;
                    break;
                }
            }
        }

        Date nextUpdate;
        if (control.getIntervalMinutes() != null) {
            int minutesTillNextUpdate = intervalsTillNextCrl * control.getIntervalMinutes()
                    + control.getOverlapMinutes();
            nextUpdate = new Date(MS_PER_SECOND * (thisUpdate.getTime() / MS_PER_SECOND / 60
                    + minutesTillNextUpdate) * 60);
        } else {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setTime(thisUpdate);
            cal.add(Calendar.DAY_OF_YEAR, intervalsTillNextCrl);
            cal.set(Calendar.HOUR_OF_DAY, control.getIntervalDayTime().getHour());
            cal.set(Calendar.MINUTE, control.getIntervalDayTime().getMinute());
            cal.add(Calendar.MINUTE, control.getOverlapMinutes());
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            nextUpdate = cal.getTime();
        }

        return nextUpdate;
    } // method getCrlNextUpdate

    private int removeExpirtedCerts(Date expiredAtTime) throws OperationException {
        ParamUtil.requireNonNull("expiredtime", expiredAtTime);
        if (!masterMode) {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "CA could not remove expired certificates at slave mode");
        }

        final String caName = caInfo.getName();
        final int numEntries = 100;

        X509Cert caCert = caInfo.getCertificate();
        final long expiredAt = expiredAtTime.getTime() / 1000;

        int sum = 0;
        while (true) {
            List<BigInteger> serials = certstore.getExpiredCertSerials(caCert, expiredAt,
                    numEntries);
            if (CollectionUtil.isEmpty(serials)) {
                return sum;
            }

            for (BigInteger serial : serials) {
                // don'd delete CA's own certificate
                if ((caInfo.isSelfSigned() && caInfo.getSerialNumber().equals(serial))) {
                    continue;
                }

                boolean removed = false;
                try {
                    removed = doRemoveCertificate(serial) != null;
                } catch (Throwable th) {
                    LogUtil.error(LOG, th, "could not remove expired certificate");
                    if (!removed) {
                        return sum;
                    }
                } finally {
                    AuditEvent auditEvent = newAuditEvent();
                    auditEvent.setLevel(removed ? AuditLevel.INFO : AuditLevel.ERROR);
                    auditEvent.setStatus(removed ? AuditStatus.SUCCESSFUL : AuditStatus.FAILED);
                    auditEvent.addEventData(new AuditEventData("CA", caName));
                    auditEvent.addEventData(
                            new AuditEventData("serialNumber", LogUtil.formatCsn(serial)));
                    auditEvent.addEventData(new AuditEventData("eventType", "REMOVE_EXPIRED_CERT"));
                    getAuditService().logEvent(auditEvent);
                } // end finally
            } // end for
        } // end while (true)
    } // method removeExpirtedCerts

    private int revokeSuspendedCerts() throws OperationException {
        if (!masterMode) {
            throw new OperationException(ErrorCode.INSUFFICIENT_PERMISSION,
                    "CA could not remove expired certificates at slave mode");
        }

        final String caName = caInfo.getName();
        final int numEntries = 100;

        X509Cert caCert = caInfo.getCertificate();

        CertValidity val = caInfo.getRevokeSuspendedCertsControl().getUnchangedSince();
        long ms;
        switch (val.getUnit()) {
        case DAY:
            ms = val.getValidity() * DAY_IN_MS;
            break;
        case HOUR:
            ms = val.getValidity() * DAY_IN_MS / 24;
            break;
        case YEAR:
            ms = val.getValidity() * 365 * DAY_IN_MS;
            break;
        default:
            throw new RuntimeException("should not reach here, unknown Valditiy Unit "
                    + val.getUnit());
        }
        final long latestLastUpdatedAt = (System.currentTimeMillis() - ms) / 1000; // seconds
        final CrlReason reason = caInfo.getRevokeSuspendedCertsControl().getTargetReason();

        int sum = 0;
        while (true) {
            List<BigInteger> serials = certstore.getSuspendedCertSerials(caCert,
                    latestLastUpdatedAt, numEntries);
            if (CollectionUtil.isEmpty(serials)) {
                return sum;
            }

            for (BigInteger serial : serials) {
                boolean revoked = false;
                try {
                    revoked = doRevokeSuspendedCert(serial, reason) != null;
                    if (revoked) {
                        sum++;
                    }
                } catch (Throwable th) {
                    LogUtil.error(LOG, th, "could not remove expired certificate");
                    if (!revoked) {
                        return sum;
                    }
                } finally {
                    AuditEvent auditEvent = newAuditEvent();
                    auditEvent.setLevel(revoked ? AuditLevel.INFO : AuditLevel.ERROR);
                    auditEvent.setStatus(revoked ? AuditStatus.SUCCESSFUL : AuditStatus.FAILED);
                    auditEvent.addEventData(new AuditEventData("CA", caName));
                    auditEvent.addEventData(
                            new AuditEventData("serialNumber", LogUtil.formatCsn(serial)));
                    auditEvent.addEventData(
                            new AuditEventData("eventType", "REVOKE_SUSPENDED_CERT"));
                    getAuditService().logEvent(auditEvent);
                } // end finally
            } // end for
        } // end while (true)
    } // method removeExpirtedCerts

    public HealthCheckResult healthCheck() {
        HealthCheckResult result = new HealthCheckResult("X509CA");

        boolean healthy = true;

        ConcurrentContentSigner signer = caInfo.getSigner(null);
        if (signer != null) {
            boolean caSignerHealthy = signer.isHealthy();
            healthy &= caSignerHealthy;

            HealthCheckResult signerHealth = new HealthCheckResult("Signer");
            signerHealth.setHealthy(caSignerHealthy);
            result.addChildCheck(signerHealth);
        }

        boolean databaseHealthy = certstore.isHealthy();
        healthy &= databaseHealthy;

        HealthCheckResult databaseHealth = new HealthCheckResult("Database");
        databaseHealth.setHealthy(databaseHealthy);
        result.addChildCheck(databaseHealth);

        X509CrlSignerEntryWrapper crlSigner = getCrlSigner();
        if (crlSigner != null && crlSigner.getSigner() != null) {
            boolean crlSignerHealthy = crlSigner.getSigner().isHealthy();
            healthy &= crlSignerHealthy;

            HealthCheckResult crlSignerHealth = new HealthCheckResult("CRLSigner");
            crlSignerHealth.setHealthy(crlSignerHealthy);
            result.addChildCheck(crlSignerHealth);
        }

        for (IdentifiedX509CertPublisher publisher : getPublishers()) {
            boolean ph = publisher.isHealthy();
            healthy &= ph;

            HealthCheckResult publisherHealth = new HealthCheckResult("Publisher");
            publisherHealth.setHealthy(publisher.isHealthy());
            result.addChildCheck(publisherHealth);
        }

        result.setHealthy(healthy);

        return result;
    } // method healthCheck

    public void setAuditServiceRegister(final AuditServiceRegister auditServiceRegister) {
        this.auditServiceRegister = ParamUtil.requireNonNull("auditServiceRegister",
                auditServiceRegister);
    }

    private AuditService getAuditService() {
        return auditServiceRegister.getAuditService();
    }

    private AuditEvent newAuditEvent() {
        AuditEvent ae = new AuditEvent(new Date());
        ae.setApplicationName("CA");
        ae.setName("SYSTEM");
        return ae;
    }

    private boolean verifySignature(final X509Certificate cert) {
        ParamUtil.requireNonNull("cert", cert);
        PublicKey caPublicKey = caInfo.getCertificate().getCert().getPublicKey();
        try {
            final String provider = XiSecurityConstants.PROVIDER_NAME_NSS;

            if (tryNssToVerify == null) {
                // Not for ECDSA
                if (caPublicKey instanceof ECPublicKey) {
                    tryNssToVerify = Boolean.FALSE;
                } else if (Security.getProvider(provider) == null) {
                    LOG.info("security provider {} is not registered", provider);
                    tryNssToVerify = Boolean.FALSE;
                } else {
                    byte[] tbs = cert.getTBSCertificate();
                    byte[] signatureValue = cert.getSignature();
                    String sigAlgName = cert.getSigAlgName();
                    try {
                        Signature verifier = Signature.getInstance(sigAlgName, provider);
                        verifier.initVerify(caPublicKey);
                        verifier.update(tbs);
                        boolean sigValid = verifier.verify(signatureValue);

                        LOG.info("use {} to verify {} signature", provider, sigAlgName);
                        tryNssToVerify = Boolean.TRUE;
                        return sigValid;
                    } catch (Exception ex) {
                        LOG.info("could not use {} to verify {} signature", provider, sigAlgName);
                        tryNssToVerify = Boolean.FALSE;
                    }
                }
            }

            if (tryNssToVerify) {
                byte[] tbs = cert.getTBSCertificate();
                byte[] signatureValue = cert.getSignature();
                String sigAlgName = cert.getSigAlgName();
                Signature verifier = Signature.getInstance(sigAlgName, provider);
                verifier.initVerify(caPublicKey);
                verifier.update(tbs);
                return verifier.verify(signatureValue);
            } else {
                cert.verify(caPublicKey);
                return true;
            }
        } catch (SignatureException | InvalidKeyException | CertificateException
                | NoSuchAlgorithmException | NoSuchProviderException ex) {
            LOG.debug("{} while verifying signature: {}", ex.getClass().getName(), ex.getMessage());
            return false;
        }
    } // method verifySignature

    private X509CrlSignerEntryWrapper getCrlSigner() {
        String crlSignerName = caInfo.getCrlSignerName();
        X509CrlSignerEntryWrapper crlSigner = (crlSignerName == null) ? null
                : caManager.getCrlSignerWrapper(crlSignerName);
        return crlSigner;
    }

    void shutdown() {
        if (crlGenerationService != null) {
            crlGenerationService.cancel(false);
            crlGenerationService = null;
        }

        if (expiredCertsRemover != null) {
            expiredCertsRemover.cancel(false);
            expiredCertsRemover = null;
        }

        if (suspendedCertsRevoker != null) {
            suspendedCertsRevoker.cancel(false);
            suspendedCertsRevoker = null;
        }

        ScheduledThreadPoolExecutor executor = caManager.getScheduledThreadPoolExecutor();
        if (executor != null) {
            executor.purge();
        }
    }

    private static Extension createReasonExtension(final int reasonCode) {
        org.bouncycastle.asn1.x509.CRLReason crlReason =
                org.bouncycastle.asn1.x509.CRLReason.lookup(reasonCode);

        try {
            return new Extension(Extension.reasonCode, false, crlReason.getEncoded());
        } catch (IOException ex) {
            throw new IllegalArgumentException("error encoding reason: " + ex.getMessage(), ex);
        }
    }

    private static Extension createInvalidityDateExtension(final Date invalidityDate) {
        try {
            ASN1GeneralizedTime asnTime = new ASN1GeneralizedTime(invalidityDate);
            return new Extension(Extension.invalidityDate, false, asnTime.getEncoded());
        } catch (IOException ex) {
            throw new IllegalArgumentException("error encoding reason: " + ex.getMessage(), ex);
        }
    }

    private static Extension createCertificateIssuerExtension(final X500Name certificateIssuer) {
        try {
            GeneralName generalName = new GeneralName(certificateIssuer);
            return new Extension(Extension.certificateIssuer, true,
                    new GeneralNames(generalName).getEncoded());
        } catch (IOException ex) {
            throw new IllegalArgumentException("error encoding reason: " + ex.getMessage(), ex);
        }
    }

    // remove the RDNs with empty content
    private static X500Name removeEmptyRdns(final X500Name name) {
        RDN[] rdns = name.getRDNs();
        List<RDN> tmpRdns = new ArrayList<RDN>(rdns.length);
        boolean changed = false;
        for (RDN rdn : rdns) {
            String textValue = X509Util.rdnValueToString(rdn.getFirst().getValue());
            if (StringUtil.isBlank(textValue)) {
                changed = true;
            } else {
                tmpRdns.add(rdn);
            }
        }

        return changed ? new X500Name(tmpRdns.toArray(new RDN[0])) : name;
    } // method removeEmptyRdns

    private static Object[] incSerialNumber(final IdentifiedX509Certprofile profile,
            final X500Name origName, final String latestSn) throws BadFormatException {
        RDN[] rdns = origName.getRDNs();

        int commonNameIndex = -1;
        int serialNumberIndex = -1;
        for (int i = 0; i < rdns.length; i++) {
            RDN rdn = rdns[i];
            ASN1ObjectIdentifier type = rdn.getFirst().getType();
            if (ObjectIdentifiers.DN_CN.equals(type)) {
                commonNameIndex = i;
            } else if (ObjectIdentifiers.DN_SERIALNUMBER.equals(type)) {
                serialNumberIndex = i;
            }
        }

        String newSerialNumber = profile.incSerialNumber(latestSn);
        RDN serialNumberRdn = new RDN(ObjectIdentifiers.DN_SERIALNUMBER,
                new DERPrintableString(newSerialNumber));

        X500Name newName;
        if (serialNumberIndex != -1) {
            rdns[serialNumberIndex] = serialNumberRdn;
            newName = new X500Name(rdns);
        } else {
            List<RDN> newRdns = new ArrayList<>(rdns.length + 1);

            if (commonNameIndex == -1) {
                newRdns.add(serialNumberRdn);
            }

            for (int i = 0; i < rdns.length; i++) {
                newRdns.add(rdns[i]);
                if (i == commonNameIndex) {
                    newRdns.add(serialNumberRdn);
                }
            }

            newName = new X500Name(newRdns.toArray(new RDN[0]));
        }

        return new Object[]{newName, newSerialNumber};
    } // method incSerialNumber

    private static Date setToMidnight(final Date date, final TimeZone timezone) {
        Calendar cal = Calendar.getInstance(timezone);
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

}
