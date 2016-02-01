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

package org.xipki.pki.ca.client.api;

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.asn1.crmf.CertRequest;
import org.bouncycastle.asn1.crmf.ProofOfPossession;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.x500.X500Name;
import org.xipki.commons.common.HealthCheckResult;
import org.xipki.commons.common.RequestResponseDebug;
import org.xipki.pki.ca.client.api.dto.EnrollCertRequestType;
import org.xipki.pki.ca.client.api.dto.RevokeCertRequestType;
import org.xipki.pki.ca.client.api.dto.UnrevokeOrRemoveCertRequestType;

/**
 * @author Lijun Liao
 */

public interface CAClient {

    Set<String> getCaNames();

    Set<CertprofileInfo> getCertprofiles(
            String caName);

    EnrollCertResult requestCert(
            CertificationRequest p10Request,
            String profile,
            String caName,
            String username,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    EnrollCertResult requestCerts(
            EnrollCertRequestType request,
            String caName,
            String username,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    CertIdOrError revokeCert(
            X500Name issuer,
            BigInteger serial,
            int reason,
            Date invalidityTime,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    CertIdOrError revokeCert(
            X509Certificate cert,
            int reason,
            Date invalidityTime,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    Map<String, CertIdOrError> revokeCerts(
            RevokeCertRequestType request,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    X509CRL downloadCRL(
            String caName,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    X509CRL downloadCRL(
            String caName,
            BigInteger crlNumber,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    X509CRL generateCRL(
            String caName,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    String getCaNameByIssuer(
            X500Name issuer)
    throws CAClientException;

    byte[] envelope(
            CertRequest certRequest,
            ProofOfPossession popo,
            String profileName,
            String caName,
            String username)
    throws CAClientException;

    byte[] envelopeRevocation(
            X500Name issuer,
            BigInteger serial,
            int reason)
    throws CAClientException;

    byte[] envelopeRevocation(
            X509Certificate cert,
            int reason)
    throws CAClientException;

    CertIdOrError unrevokeCert(
            X500Name issuer,
            BigInteger serial,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    CertIdOrError unrevokeCert(
            X509Certificate cert,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    Map<String, CertIdOrError> unrevokeCerts(
            UnrevokeOrRemoveCertRequestType request,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    CertIdOrError removeCert(
            X500Name issuer,
            BigInteger serial,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    CertIdOrError removeCert(
            X509Certificate cert,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    Map<String, CertIdOrError> removeCerts(
            UnrevokeOrRemoveCertRequestType request,
            RequestResponseDebug debug)
    throws CAClientException, PKIErrorException;

    HealthCheckResult getHealthCheckResult(
            String caName)
    throws CAClientException;

}
