/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013-2016 Lijun Liao
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

package org.xipki.ca.server.mgmt;

import java.util.Date;
import java.util.TimeZone;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.xipki.ca.api.profile.CertProfile;
import org.xipki.ca.api.profile.ExtensionOccurrence;
import org.xipki.ca.api.profile.ExtensionTuples;
import org.xipki.ca.api.profile.SpecialCertProfileBehavior;
import org.xipki.ca.api.profile.SubjectInfo;
import org.xipki.ca.common.BadCertTemplateException;
import org.xipki.ca.common.BadFormatException;
import org.xipki.ca.common.CertProfileException;
import org.xipki.security.common.EnvironmentParameterResolver;
import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

public class IdentifiedCertProfile
{
    private final String name;
    private final CertProfile certProfile;

    public IdentifiedCertProfile(String name, CertProfile certProfile)
    {
        ParamChecker.assertNotEmpty("name", name);
        ParamChecker.assertNotNull("certProfile", certProfile);

        this.name = name;
        this.certProfile = certProfile;
    }

    public String getName()
    {
        return name;
    }

    public void initialize(String data)
    throws CertProfileException
    {
        certProfile.initialize(data);
    }

    public SpecialCertProfileBehavior getSpecialCertProfileBehavior()
    {
        return certProfile.getSpecialCertProfileBehavior();
    }

    public void setEnvironmentParameterResolver(EnvironmentParameterResolver parameterResolver)
    {
        certProfile.setEnvironmentParameterResolver(parameterResolver);
    }

    public Date getNotBefore(Date notBefore)
    {
        return certProfile.getNotBefore(notBefore);
    }

    public Integer getValidity()
    {
        return certProfile.getValidity();
    }

    public boolean hasMidnightNotBefore()
    {
        return certProfile.hasMidnightNotBefore();
    }

    public TimeZone getTimezone()
    {
        return certProfile.getTimezone();
    }

    public SubjectInfo getSubject(X500Name requestedSubject)
    throws CertProfileException, BadCertTemplateException
    {
        return certProfile.getSubject(requestedSubject);
    }

    public ExtensionTuples getExtensions(X500Name requestedSubject, Extensions requestedExtensions)
    throws CertProfileException, BadCertTemplateException
    {
        return certProfile.getExtensions(requestedSubject, requestedExtensions);
    }

    public boolean isOnlyForRA()
    {
        return certProfile.isOnlyForRA();
    }

    public ExtensionOccurrence getOccurenceOfAuthorityKeyIdentifier()
    {
        return certProfile.getOccurenceOfAuthorityKeyIdentifier();
    }

    public ExtensionOccurrence getOccurenceOfSubjectKeyIdentifier()
    {
        return certProfile.getOccurenceOfSubjectKeyIdentifier();
    }

    public ExtensionOccurrence getOccurenceOfCRLDistributinPoints()
    {
        return certProfile.getOccurenceOfCRLDistributinPoints();
    }

    public ExtensionOccurrence getOccurenceOfAuthorityInfoAccess()
    {
        return certProfile.getOccurenceOfAuthorityInfoAccess();
    }

    public void checkPublicKey(SubjectPublicKeyInfo publicKey)
    throws BadCertTemplateException
    {
        certProfile.checkPublicKey(publicKey);
    }

    public boolean incSerialNumberIfSubjectExists()
    {
        return certProfile.incSerialNumberIfSubjectExists();
    }

    public void shutdown()
    {
        certProfile.shutdown();
    }

    public boolean includeIssuerAndSerialInAKI()
    {
        return certProfile.includeIssuerAndSerialInAKI();
    }

    public String incSerialNumber(String currentSerialNumber)
    throws BadFormatException
    {
        return certProfile.incSerialNumber(currentSerialNumber);
    }

    public ExtensionOccurrence getOccurenceOfFreshestCRL()
    {
        return certProfile.getOccurenceOfFreshestCRL();
    }

    public ExtensionOccurrence getOccurenceOfIssuerAltName()
    {
        return certProfile.getOccurenceOfIssuerAltName();
    }

    public boolean isDuplicateKeyPermitted()
    {
        return certProfile.isDuplicateKeyPermitted();
    }

    public boolean isDuplicateSubjectPermitted()
    {
        return certProfile.isDuplicateSubjectPermitted();
    }

    public boolean isSerialNumberInReqPermitted()
    {
        return certProfile.isSerialNumberInReqPermitted();
    }

    public  String getParameter(String paramName)
    {
        return certProfile.getParameter(paramName);
    }

}
