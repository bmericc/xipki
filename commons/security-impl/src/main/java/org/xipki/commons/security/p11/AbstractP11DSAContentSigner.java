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

package org.xipki.commons.security.p11;

import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.RuntimeCryptoException;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDefaultDigestProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.LogUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.security.api.SignerException;
import org.xipki.commons.security.api.p11.P11CryptService;
import org.xipki.commons.security.api.p11.P11KeyIdentifier;
import org.xipki.commons.security.api.p11.P11SlotIdentifier;
import org.xipki.commons.security.api.util.AlgorithmUtil;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

abstract class AbstractP11DSAContentSigner implements ContentSigner {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractP11DSAContentSigner.class);

    private final AlgorithmIdentifier algorithmIdentifier;

    private final DigestOutputStream outputStream;

    protected final P11CryptService cryptService;

    protected final P11SlotIdentifier slot;

    protected final P11KeyIdentifier keyId;

    protected abstract byte[] CKM_SIGN(
            final byte[] hashValue)
    throws SignerException;

    protected AbstractP11DSAContentSigner(
            final P11CryptService cryptService,
            final P11SlotIdentifier slot,
            final P11KeyIdentifier keyId,
            final AlgorithmIdentifier signatureAlgId)
    throws NoSuchAlgorithmException, OperatorCreationException {
        ParamUtil.assertNotNull("slot", slot);
        ParamUtil.assertNotNull("cryptService", cryptService);
        ParamUtil.assertNotNull("keyId", keyId);
        ParamUtil.assertNotNull("signatureAlgId", signatureAlgId);

        this.slot = slot;
        this.algorithmIdentifier = signatureAlgId;
        this.keyId = keyId;
        this.cryptService = cryptService;

        AlgorithmIdentifier digAlgId = AlgorithmUtil.extractDigesetAlgorithmIdentifier(
                signatureAlgId);

        Digest digest = BcDefaultDigestProvider.INSTANCE.get(digAlgId);

        this.outputStream = new DigestOutputStream(digest);
    }

    @Override
    public AlgorithmIdentifier getAlgorithmIdentifier() {
        return algorithmIdentifier;
    }

    @Override
    public OutputStream getOutputStream() {
        outputStream.reset();
        return outputStream;
    }

    @Override
    public byte[] getSignature() {
        byte[] hashValue = outputStream.digest();
        try {
            return CKM_SIGN(hashValue);
        } catch (SignerException e) {
            LOG.warn("SignerException: {}", e.getMessage());
            LOG.debug("SignerException", e);
            throw new RuntimeCryptoException("SignerException: " + e.getMessage());
        } catch (Throwable t) {
            final String message = "Throwable";
            if (LOG.isWarnEnabled()) {
                LOG.warn(LogUtil.buildExceptionLogFormat(message), t.getClass().getName(),
                        t.getMessage());
            }
            LOG.debug(message, t);
            throw new RuntimeCryptoException(t.getClass().getName() + ": " + t.getMessage());
        }
    }

}
