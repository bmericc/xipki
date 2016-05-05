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

package org.xipki.commons.security.pkcs11;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.xipki.commons.security.exception.P11TokenException;
import org.xipki.commons.security.exception.P11UnknownEntityException;
import org.xipki.commons.security.exception.P11UnsupportedMechanismException;
import org.xipki.commons.security.exception.XiSecurityException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public interface P11Slot {

    String getModuleName();

    boolean isReadOnly();

    P11SlotIdentifier getSlotId();

    Set<P11ObjectIdentifier> getIdentityIdentifiers();

    Set<P11ObjectIdentifier> getCertIdentifiers();

    boolean hasIdentity(P11ObjectIdentifier objectId);

    void close();

    Set<Long> getMechanisms();

    boolean supportsMechanism(long mechanism);

    void assertMechanismSupported(long mechanism)
    throws P11UnsupportedMechanismException;

    P11Identity getIdentity(P11ObjectIdentifier objectId)
    throws P11UnknownEntityException;

    void refresh()
    throws P11TokenException;

    P11ObjectIdentifier getObjectIdForId(byte[] id);

    P11ObjectIdentifier getObjectIdForLabel(String label);

    void updateCertificate(@Nonnull P11ObjectIdentifier objectId, @Nonnull X509Certificate newCert)
    throws P11TokenException, XiSecurityException;

    /**
     *
     * @param id id of the objects to be deleted. At least one of id and label must not be null.
     * @param label label of the objects to be deleted
     * @return how many objects have been deleted
     * @throws P11TokenException if PKCS#11 error happens.
     */
    int removeObjects(@Nullable byte[] id, @Nullable String label)
    throws P11TokenException;

    void removeIdentity(@Nonnull P11ObjectIdentifier objectId)
    throws P11TokenException;

    void removeCerts(@Nonnull P11ObjectIdentifier objectId)
    throws P11TokenException;

    P11ObjectIdentifier addCert(@Nonnull X509Certificate cert)
    throws P11TokenException, XiSecurityException;

    void addCert(@Nonnull P11ObjectIdentifier objectId, @Nonnull X509Certificate cert)
    throws P11TokenException, XiSecurityException;

    // CHECKSTYLE:SKIP
    P11ObjectIdentifier generateRSAKeypair(int keysize, @Nonnull BigInteger publicExponent,
            @Nonnull String label)
    throws P11TokenException, XiSecurityException;

    // CHECKSTYLE:SKIP
    P11ObjectIdentifier generateDSAKeypair(int plength, int qlength, @Nonnull String label)
    throws P11TokenException, XiSecurityException;

    // CHECKSTYLE:OFF
    P11ObjectIdentifier generateDSAKeypair(BigInteger p, BigInteger q, BigInteger g,
            @Nonnull String label)
    // CHECKSTYLE:ON
    throws P11TokenException, XiSecurityException;

    // CHECKSTYLE:SKIP
    P11ObjectIdentifier generateECKeypair(@Nonnull String curveNameOrOid, @Nonnull String label)
    throws P11TokenException, XiSecurityException;

    X509Certificate exportCert(@Nonnull P11ObjectIdentifier objectId)
    throws P11TokenException, XiSecurityException;

    void showDetails(@Nonnull OutputStream stream, boolean verbose)
    throws P11TokenException, XiSecurityException, IOException;

}
