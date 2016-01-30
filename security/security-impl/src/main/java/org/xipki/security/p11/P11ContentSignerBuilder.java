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

package org.xipki.security.p11;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.NoSuchPaddingException;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.xipki.common.util.ParamUtil;
import org.xipki.security.DefaultConcurrentContentSigner;
import org.xipki.security.api.ConcurrentContentSigner;
import org.xipki.security.api.SecurityFactory;
import org.xipki.security.api.SignerException;
import org.xipki.security.api.p11.P11CryptService;
import org.xipki.security.api.p11.P11KeyIdentifier;
import org.xipki.security.api.p11.P11SlotIdentifier;
import org.xipki.security.api.util.AlgorithmUtil;
import org.xipki.security.api.util.X509Util;
import org.xipki.security.provider.P11PrivateKey;

/**
 * @author Lijun Liao
 */

public class P11ContentSignerBuilder {

    private final X509Certificate[] certificateChain;

    private final P11CryptService cryptService;

    private final SecurityFactory securityFactory;

    private final P11SlotIdentifier slot;

    private final P11KeyIdentifier keyId;

    public P11ContentSignerBuilder(
            final P11CryptService cryptService,
            final SecurityFactory securityFactory,
            final P11SlotIdentifier slot,
            final P11KeyIdentifier keyId,
            final X509Certificate[] certificateChain)
    throws SignerException {
        ParamUtil.assertNotNull("cryptService", cryptService);
        ParamUtil.assertNotNull("securityFactory", securityFactory);
        ParamUtil.assertNotNull("slot", slot);
        ParamUtil.assertNotNull("keyId", keyId);

        this.securityFactory = securityFactory;

        X509Certificate signerCertInP11 = cryptService.getCertificate(slot, keyId);
        boolean keyExists = (signerCertInP11 != null);
        if (!keyExists) {
            keyExists = (cryptService.getPublicKey(slot, keyId) != null);
        }

        if (!keyExists) {
            throw new SignerException("key with " + keyId + " does not exist");
        }

        this.cryptService = cryptService;
        this.keyId = keyId;
        this.slot = slot;

        Set<Certificate> caCerts = new HashSet<>();

        X509Certificate cert;
        int n = (certificateChain == null)
                ? 0
                : certificateChain.length;

        if (n > 0) {
            cert = certificateChain[0];
            if (n > 1) {
                for (int i = 1; i < n; i++) {
                    caCerts.add(certificateChain[i]);
                }
            }
        } else {
            cert = signerCertInP11;
        }

        Certificate[] certsInKeystore = cryptService.getCertificates(slot, keyId);
        if (certsInKeystore.length > 1) {
            for (int i = 1; i < certsInKeystore.length; i++) {
                caCerts.add(certsInKeystore[i]);
            }
        }

        this.certificateChain = X509Util.buildCertPath(cert, caCerts);
    } // constructor

    public ConcurrentContentSigner createSigner(
            final AlgorithmIdentifier signatureAlgId,
            final int parallelism)
    throws OperatorCreationException, NoSuchPaddingException {
        if (parallelism < 1) {
            throw new IllegalArgumentException("non-positive parallelism is not allowed: "
                    + parallelism);
        }

        PublicKey publicKey = certificateChain[0].getPublicKey();

        if (publicKey instanceof RSAPublicKey) {
            if (!AlgorithmUtil.isRSASignatureAlgoId(signatureAlgId)) {
                throw new OperatorCreationException(
                        "the given algorithm is not a valid RSA signature algorithm '"
                        + signatureAlgId.getAlgorithm().getId() + "'");
            }
        } else if (publicKey instanceof ECPublicKey) {
            if (!AlgorithmUtil.isECSigAlg(signatureAlgId)) {
                throw new OperatorCreationException(
                        "the given algorithm is not a valid EC signature algirthm '"
                        + signatureAlgId.getAlgorithm().getId() + "'");
            }
        } else if (publicKey instanceof DSAPublicKey) {
            if (!AlgorithmUtil.isDSASigAlg(signatureAlgId)) {
                throw new OperatorCreationException(
                        "the given algorithm is not a valid DSA signature algirthm '"
                        + signatureAlgId.getAlgorithm().getId() + "'");
            }
        } else {
            throw new OperatorCreationException("unsupported key "
                    + publicKey.getClass().getName());
        }

        List<ContentSigner> signers = new ArrayList<>(parallelism);

        try {
            for (int i = 0; i < parallelism; i++) {
                ContentSigner signer;
                if (publicKey instanceof RSAPublicKey) {
                    if (PKCSObjectIdentifiers.id_RSASSA_PSS.equals(signatureAlgId.getAlgorithm())) {
                        signer = new P11RSAPSSContentSigner(cryptService, slot, keyId,
                                signatureAlgId, securityFactory.getRandom4Sign());
                    } else {
                        signer = new P11RSAContentSigner(cryptService, slot, keyId,
                                signatureAlgId);
                    }
                } else if (publicKey instanceof ECPublicKey) {
                    if (AlgorithmUtil.isDSAPlainSigAlg(signatureAlgId)) {
                        signer = new P11ECDSAPlainContentSigner(cryptService, slot, keyId,
                                signatureAlgId);
                    } else {
                        signer = new P11ECDSAX962ContentSigner(cryptService, slot, keyId,
                                signatureAlgId);
                    }
                } else if (publicKey instanceof DSAPublicKey) {
                    if (AlgorithmUtil.isDSAPlainSigAlg(signatureAlgId)) {
                        signer = new P11DSAPlainContentSigner(cryptService, slot, keyId,
                                signatureAlgId);
                    } else {
                        signer = new P11DSAX962ContentSigner(cryptService, slot, keyId,
                                signatureAlgId);
                    }
                } else {
                    throw new OperatorCreationException("unsupported key "
                            + publicKey.getClass().getName());
                }
                signers.add(signer);
            } // end for
        } catch (NoSuchAlgorithmException e) {
            throw new OperatorCreationException("no such algorithm", e);
        }

        PrivateKey privateKey;
        try {
            privateKey = new P11PrivateKey(cryptService, slot, keyId);
        } catch (InvalidKeyException e) {
            throw new OperatorCreationException(
                    "could not construct P11PrivateKey: " + e.getMessage(), e);
        }

        DefaultConcurrentContentSigner concurrentSigner =
                new DefaultConcurrentContentSigner(signers, privateKey);
        concurrentSigner.setCertificateChain(certificateChain);

        return concurrentSigner;
    } // method createSigner

}
