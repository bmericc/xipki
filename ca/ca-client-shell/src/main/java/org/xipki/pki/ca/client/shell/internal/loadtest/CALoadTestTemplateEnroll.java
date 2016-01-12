/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2014 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

package org.xipki.pki.ca.client.shell.internal.loadtest;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.SchemaFactory;

import org.bouncycastle.asn1.crmf.CertRequest;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.crmf.CertTemplateBuilder;
import org.bouncycastle.asn1.crmf.ProofOfPossession;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.common.InvalidConfException;
import org.xipki.common.LoadExecutor;
import org.xipki.common.util.ParamUtil;
import org.xipki.common.util.XMLUtil;
import org.xipki.pki.ca.client.api.CAClient;
import org.xipki.pki.ca.client.api.CAClientException;
import org.xipki.pki.ca.client.api.CertOrError;
import org.xipki.pki.ca.client.api.EnrollCertResult;
import org.xipki.pki.ca.client.api.PKIErrorException;
import org.xipki.pki.ca.client.api.dto.EnrollCertRequestEntryType;
import org.xipki.pki.ca.client.api.dto.EnrollCertRequestType;
import org.xipki.pki.ca.client.api.dto.EnrollCertRequestType.Type;
import org.xipki.pki.ca.client.shell.internal.loadtest.KeyEntry.DSAKeyEntry;
import org.xipki.pki.ca.client.shell.internal.loadtest.KeyEntry.ECKeyEntry;
import org.xipki.pki.ca.client.shell.internal.loadtest.KeyEntry.RSAKeyEntry;
import org.xipki.pki.ca.client.shell.internal.loadtest.LoadTestEntry.RandomDN;
import org.xipki.pki.ca.client.shell.internal.loadtest.jaxb.EnrollCertType;
import org.xipki.pki.ca.client.shell.internal.loadtest.jaxb.EnrollTemplateType;
import org.xipki.pki.ca.client.shell.internal.loadtest.jaxb.ObjectFactory;
import org.xml.sax.SAXException;

/**
 * @author Lijun Liao
 */

public class CALoadTestTemplateEnroll extends LoadExecutor {

    private final static class CertRequestWithProfile {

        private final String certprofile;

        private final CertRequest certRequest;

        public CertRequestWithProfile(
                final String certprofile,
                final CertRequest certRequest) {
            this.certprofile = certprofile;
            this.certRequest = certRequest;
        }

    } // class CertRequestWithProfile

    class Testor implements Runnable {

        @Override
        public void run() {
            while (!stop() && getErrorAccout() < 1) {
                Map<Integer, CertRequestWithProfile> certReqs = nextCertRequests();
                if (certReqs != null) {
                    boolean successful = testNext(certReqs);
                    int numFailed = successful
                            ? 0
                            : 1;
                    account(1, numFailed);
                } else {
                    account(1, 1);
                }
            }
        }

        private boolean testNext(
                final Map<Integer, CertRequestWithProfile> certRequests) {
            EnrollCertResult result;
            try {
                EnrollCertRequestType request = new EnrollCertRequestType(Type.CERT_REQ);
                for (Integer certId : certRequests.keySet()) {
                    CertRequestWithProfile certRequest = certRequests.get(certId);
                    EnrollCertRequestEntryType requestEntry = new EnrollCertRequestEntryType(
                            "id-" + certId,
                            certRequest.certprofile,
                            certRequest.certRequest,
                            RA_VERIFIED);

                    request.addRequestEntry(requestEntry);
                }

                result = caClient.requestCerts(request, null,
                        userPrefix + System.currentTimeMillis(), null);
            } catch (CAClientException | PKIErrorException e) {
                LOG.warn("{}: {}", e.getClass().getName(), e.getMessage());
                return false;
            } catch (Throwable t) {
                LOG.warn("{}: {}", t.getClass().getName(), t.getMessage());
                return false;
            }

            if (result == null) {
                return false;
            }

            Set<String> ids = result.getAllIds();
            if (ids.size() < certRequests.size()) {
                return false;
            }

            for (String id : ids) {
                CertOrError certOrError = result.getCertificateOrError(id);
                X509Certificate cert = (X509Certificate) certOrError.getCertificate();

                if (cert == null) {
                    return false;
                }
            }

            return true;
        }

    } // class Testor

    private static final Logger LOG = LoggerFactory.getLogger(CALoadTestTemplateEnroll.class);

    private static final ProofOfPossession RA_VERIFIED = new ProofOfPossession();

    private static Object jaxbUnmarshallerLock = new Object();

    private static Unmarshaller jaxbUnmarshaller;

    private final CAClient caClient;

    private final String userPrefix = "LOADTEST-";

    private final List<LoadTestEntry> loadtestEntries;

    private final AtomicLong index;

    public CALoadTestTemplateEnroll(
            final CAClient caClient,
            final EnrollTemplateType template,
            final String description)
    throws Exception {
        super(description);

        ParamUtil.assertNotNull("caClient", caClient);
        ParamUtil.assertNotNull("template", template);

        this.caClient = caClient;

        Calendar baseTime = Calendar.getInstance(Locale.UK);
        baseTime.set(Calendar.YEAR, 2014);
        baseTime.set(Calendar.MONTH, 0);
        baseTime.set(Calendar.DAY_OF_MONTH, 1);

        this.index = new AtomicLong(getSecureIndex());

        List<EnrollCertType> list = template.getEnrollCert();
        loadtestEntries = new ArrayList<>(list.size());

        for (EnrollCertType entry : list) {
            KeyEntry keyEntry;
            if (entry.getEcKey() != null) {
                keyEntry = new ECKeyEntry(entry.getEcKey().getCurve());
            } else if (entry.getRsaKey() != null) {
                keyEntry = new RSAKeyEntry(entry.getRsaKey().getModulusLength());
            } else if (entry.getDsaKey() != null) {
                keyEntry = new DSAKeyEntry(entry.getDsaKey().getPLength());
            } else {
                throw new RuntimeException("should not reach here, unknown child of KeyEntry");
            }

            String randomDNStr = entry.getRandomDN();
            RandomDN randomDN = RandomDN.getInstance(randomDNStr);
            if (randomDN == null) {
                throw new InvalidConfException("invalid randomDN " + randomDNStr);
            }

            LoadTestEntry loadtestEntry = new LoadTestEntry(entry.getCertprofile(),
                    keyEntry, entry.getSubject(), randomDN);
            loadtestEntries.add(loadtestEntry);
        }
    }

    @Override
    protected Runnable getTestor()
    throws Exception {
        return new Testor();
    }

    public int getNumberOfCertsInOneRequest() {
        return loadtestEntries.size();
    }

    private Map<Integer, CertRequestWithProfile> nextCertRequests() {
        Map<Integer, CertRequestWithProfile> certRequests = new HashMap<>();
        final int n = loadtestEntries.size();
        for (int i = 0; i < n; i++) {
            LoadTestEntry loadtestEntry = loadtestEntries.get(i);
            final int certId = i + 1;
            CertTemplateBuilder certTempBuilder = new CertTemplateBuilder();

            long thisIndex = index.getAndIncrement();
            certTempBuilder.setSubject(loadtestEntry.getX500Name(thisIndex));

            SubjectPublicKeyInfo spki = loadtestEntry.getSubjectPublicKeyInfo(thisIndex);
            if (spki == null) {
                return null;
            }

            certTempBuilder.setPublicKey(spki);

            CertTemplate certTemplate = certTempBuilder.build();
            CertRequest certRequest = new CertRequest(certId, certTemplate, null);
            CertRequestWithProfile requestWithCertprofile = new CertRequestWithProfile(
                    loadtestEntry.getCertprofile(), certRequest);
            certRequests.put(certId, requestWithCertprofile);
        }
        return certRequests;
    }

    public static EnrollTemplateType parse(
            final InputStream configStream)
    throws InvalidConfException {
        synchronized (jaxbUnmarshallerLock) {
            Object root;
            try {
                if (jaxbUnmarshaller == null) {
                    JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
                    jaxbUnmarshaller = context.createUnmarshaller();

                    final SchemaFactory schemaFact = SchemaFactory.newInstance(
                            javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
                    URL url = ObjectFactory.class.getResource("/xsd/loadtest.xsd");
                    jaxbUnmarshaller.setSchema(schemaFact.newSchema(url));
                }

                root = jaxbUnmarshaller.unmarshal(configStream);
            } catch (SAXException e) {
                throw new InvalidConfException(
                        "parse profile failed, message: " + e.getMessage(),
                        e);
            } catch (JAXBException e) {
                throw new InvalidConfException(
                        "parse profile failed, message: " + XMLUtil.getMessage((JAXBException) e),
                        e);
            }

            if (root instanceof JAXBElement) {
                return (EnrollTemplateType) ((JAXBElement<?>) root).getValue();
            } else {
                throw new InvalidConfException("invalid root element type");
            }
        }
    }

}
