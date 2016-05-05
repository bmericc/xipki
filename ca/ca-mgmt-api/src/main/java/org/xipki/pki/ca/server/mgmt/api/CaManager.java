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

package org.xipki.pki.ca.server.mgmt.api;

import java.math.BigInteger;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.xipki.commons.security.CertRevocationInfo;
import org.xipki.commons.security.CrlReason;
import org.xipki.pki.ca.server.mgmt.api.x509.ScepEntry;
import org.xipki.pki.ca.server.mgmt.api.x509.X509CaEntry;
import org.xipki.pki.ca.server.mgmt.api.x509.X509ChangeCrlSignerEntry;
import org.xipki.pki.ca.server.mgmt.api.x509.X509CrlSignerEntry;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public interface CaManager {

    String NULL = "NULL";

    CaSystemStatus getCaSystemStatus();

    boolean unlockCa();

    boolean notifyCaChange()
    throws CaMgmtException;

    boolean publishRootCa(@Nonnull String caName, @Nonnull String certprofile)
    throws CaMgmtException;

    boolean republishCertificates(@Nullable String caName, @Nullable List<String> publisherNames)
    throws CaMgmtException;

    boolean clearPublishQueue(@Nullable String caName, @Nullable List<String> publisherNames)
    throws CaMgmtException;

    boolean removeCa(@Nonnull String caName)
    throws CaMgmtException;

    boolean restartCaSystem();

    boolean addCaAlias(@Nonnull String aliasName, @Nonnull String caName)
    throws CaMgmtException;

    boolean removeCaAlias(@Nonnull String aliasName)
    throws CaMgmtException;

    Set<String> getAliasesForCa(@Nonnull String caName);

    String getCaNameForAlias(@Nonnull String aliasName);

    Set<String> getCaAliasNames();

    Set<String> getCertprofileNames();

    Set<String> getPublisherNames();

    Set<String> getCmpRequestorNames();

    Set<String> getCmpResponderNames();

    Set<String> getCrlSignerNames();

    Set<String> getCmpControlNames();

    Set<String> getCaNames();

    boolean addCa(@Nonnull CaEntry caEntry)
    throws CaMgmtException;

    CaEntry getCa(@Nonnull String caName);

    boolean changeCa(@Nonnull ChangeCaEntry changeCAentry)
    throws CaMgmtException;

    boolean removeCertprofileFromCa(@Nonnull String profileName, @Nonnull String caName)
    throws CaMgmtException;

    boolean addCertprofileToCa(@Nonnull String profileName, @Nonnull String caName)
    throws CaMgmtException;

    boolean removePublisherFromCa(@Nonnull String publisherName, @Nonnull String caName)
    throws CaMgmtException;

    boolean addPublisherToCa(@Nonnull String publisherName, @Nonnull String caName)
    throws CaMgmtException;

    Set<String> getCertprofilesForCa(@Nonnull String caName);

    Set<CaHasRequestorEntry> getCmpRequestorsForCa(@Nonnull String caName);

    CmpRequestorEntry getCmpRequestor(@Nonnull String name);

    boolean addCmpRequestor(@Nonnull CmpRequestorEntry dbEntry)
    throws CaMgmtException;

    boolean removeCmpRequestor(@Nonnull String requestorName)
    throws CaMgmtException;

    boolean changeCmpRequestor(@Nonnull String name, @Nonnull String base64Cert)
    throws CaMgmtException;

    boolean removeCmpRequestorFromCa(@Nonnull String requestorName, @Nonnull String caName)
    throws CaMgmtException;

    boolean addCmpRequestorToCa(@Nonnull CaHasRequestorEntry requestor, @Nonnull String caName)
    throws CaMgmtException;

    CertprofileEntry getCertprofile(@Nonnull String profileName);

    boolean removeCertprofile(@Nonnull String profileName)
    throws CaMgmtException;

    boolean changeCertprofile(@Nonnull String name, @Nullable String type, @Nullable String conf)
    throws CaMgmtException;

    boolean addCertprofile(@Nonnull CertprofileEntry dbEntry)
    throws CaMgmtException;

    boolean addCmpResponder(@Nonnull CmpResponderEntry dbEntry)
    throws CaMgmtException;

    boolean removeCmpResponder(@Nonnull String name)
    throws CaMgmtException;

    CmpResponderEntry getCmpResponder(@Nonnull String name);

    boolean changeCmpResponder(@Nonnull String name, @Nullable String type, @Nullable String conf,
            @Nullable String base64Cert)
    throws CaMgmtException;

    boolean addCrlSigner(@Nonnull X509CrlSignerEntry dbEntry)
    throws CaMgmtException;

    boolean removeCrlSigner(@Nonnull String crlSignerName)
    throws CaMgmtException;

    boolean changeCrlSigner(@Nonnull X509ChangeCrlSignerEntry dbEntry)
    throws CaMgmtException;

    X509CrlSignerEntry getCrlSigner(@Nonnull String name);

    boolean addPublisher(@Nonnull PublisherEntry dbEntry)
    throws CaMgmtException;

    List<PublisherEntry> getPublishersForCa(@Nonnull String caName);

    PublisherEntry getPublisher(@Nonnull String publisherName);

    boolean removePublisher(@Nonnull String publisherName)
    throws CaMgmtException;

    boolean changePublisher(@Nonnull String name, @Nullable String type, @Nullable String conf)
    throws CaMgmtException;

    CmpControlEntry getCmpControl(@Nonnull String name);

    boolean addCmpControl(@Nonnull CmpControlEntry dbEntry)
    throws CaMgmtException;

    boolean removeCmpControl(@Nonnull String name)
    throws CaMgmtException;

    boolean changeCmpControl(@Nonnull String name, @Nullable String conf)
    throws CaMgmtException;

    Set<String> getEnvParamNames();

    String getEnvParam(@Nonnull String name);

    boolean addEnvParam(@Nonnull String name, @Nonnull String value)
    throws CaMgmtException;

    boolean removeEnvParam(@Nonnull String envParamName)
    throws CaMgmtException;

    boolean changeEnvParam(@Nonnull String name, @Nonnull String value)
    throws CaMgmtException;

    boolean revokeCa(@Nonnull String caName, @Nonnull CertRevocationInfo revocationInfo)
    throws CaMgmtException;

    boolean unrevokeCa(@Nonnull String caName)
    throws CaMgmtException;

    boolean revokeCertificate(@Nonnull String caName, @Nonnull BigInteger serialNumber,
            @Nonnull CrlReason reason, @Nullable Date invalidityTime)
    throws CaMgmtException;

    boolean unrevokeCertificate(@Nonnull String caName, @Nonnull BigInteger serialNumber)
    throws CaMgmtException;

    boolean removeCertificate(@Nonnull String caName, @Nonnull BigInteger serialNumber)
    throws CaMgmtException;

    X509Certificate generateCertificate(@Nonnull String caName, @Nonnull String profileName,
            @Nullable String user, @Nonnull byte[] encodedPkcs10Request)
    throws CaMgmtException;

    X509Certificate generateRootCa(@Nonnull X509CaEntry caEntry, @Nonnull String certprofileName,
            @Nonnull byte[] p10Req)
    throws CaMgmtException;

    boolean addUser(@Nonnull AddUserEntry userEntry)
    throws CaMgmtException;

    boolean changeUser(@Nonnull String username, @Nullable String password,
            @Nullable String cnRegex)
    throws CaMgmtException;

    boolean removeUser(@Nonnull String username)
    throws CaMgmtException;

    UserEntry getUser(@Nonnull String username)
    throws CaMgmtException;

    X509CRL generateCrlOnDemand(@Nonnull String caName)
    throws CaMgmtException;

    X509CRL getCrl(@Nonnull String caName, @Nonnull BigInteger crlNumber)
    throws CaMgmtException;

    X509CRL getCurrentCrl(@Nonnull String caName)
    throws CaMgmtException;

    boolean addScep(@Nonnull ScepEntry scepEntry)
    throws CaMgmtException;

    boolean removeScep(@Nonnull String name)
    throws CaMgmtException;

    boolean changeScep(@Nonnull ChangeScepEntry scepEntry)
    throws CaMgmtException;

    Set<String> getScepNames();

    ScepEntry getScepEntry(@Nonnull String name)
    throws CaMgmtException;

}
