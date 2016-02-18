/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013-2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * (version 3 or later at your option)
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

package org.xipki.security;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.NoSuchPaddingException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.CertificationRequest;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.security.api.ConcurrentContentSigner;
import org.xipki.security.api.NoIdleSignerException;
import org.xipki.security.api.PasswordResolver;
import org.xipki.security.api.PasswordResolverException;
import org.xipki.security.api.SecurityFactory;
import org.xipki.security.api.SignerException;
import org.xipki.security.api.p11.P11Control;
import org.xipki.security.api.p11.P11CryptService;
import org.xipki.security.api.p11.P11CryptServiceFactory;
import org.xipki.security.api.p11.P11KeyIdentifier;
import org.xipki.security.api.p11.P11ModuleConf;
import org.xipki.security.api.p11.P11NullPasswordRetriever;
import org.xipki.security.api.p11.P11PasswordRetriever;
import org.xipki.security.api.p11.P11SlotIdentifier;
import org.xipki.security.common.ConfPairs;
import org.xipki.security.common.ConfigurationException;
import org.xipki.security.common.IoCertUtil;
import org.xipki.security.common.LogUtil;
import org.xipki.security.common.ParamChecker;
import org.xipki.security.common.StringUtil;
import org.xipki.security.p11.P11ContentSignerBuilder;
import org.xipki.security.p11.P11PasswordRetrieverImpl;
import org.xipki.security.p11.conf.jaxb.ModuleType;
import org.xipki.security.p11.conf.jaxb.ModulesType;
import org.xipki.security.p11.conf.jaxb.NativeLibraryType;
import org.xipki.security.p11.conf.jaxb.ObjectFactory;
import org.xipki.security.p11.conf.jaxb.PKCS11ConfType;
import org.xipki.security.p11.conf.jaxb.PasswordType;
import org.xipki.security.p11.conf.jaxb.PasswordsType;
import org.xipki.security.p11.conf.jaxb.SlotType;
import org.xipki.security.p11.conf.jaxb.SlotsType;
import org.xml.sax.SAXException;

/**
 * @author Lijun Liao
 */

public class SecurityFactoryImpl implements SecurityFactory
{
    private static final Logger LOG = LoggerFactory.getLogger(SecurityFactoryImpl.class);

    private String pkcs11Provider;
    private int defaultParallelism = 20;

    private P11Control p11Control;

    private P11CryptServiceFactory p11CryptServiceFactory;
    private boolean p11CryptServiciceFactoryInitialized;

    private PasswordResolver passwordResolver;
    private String pkcs11ConfFile;

    @Deprecated
    private String pkcs11Module;
    @Deprecated
    private Set<P11SlotIdentifier> pkcs11IncludeSlots;
    @Deprecated
    private Set<P11SlotIdentifier> pkcs11ExcludeSlots;

    public SecurityFactoryImpl()
    {
        if(Security.getProvider("BC") == null)
        {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public ConcurrentContentSigner createSigner(String type, String conf, X509Certificate cert)
    throws SignerException
    {
        return createSigner(type, conf,
                (cert == null ? null : new X509Certificate[]{cert}));
    }

    @Override
    public ConcurrentContentSigner createSigner(String type, String confWithoutAlgo, String hashAlgo,
            boolean mgf1, X509Certificate cert)
    throws SignerException
    {
        return createSigner(type, confWithoutAlgo, hashAlgo, mgf1,
                (cert == null ? null : new X509Certificate[]{cert}));
    }

    @Override
    public ConcurrentContentSigner createSigner(String type, String confWithoutAlgo, String hashAlgo,
            boolean mgf1, X509Certificate[] certs)
    throws SignerException
    {
        ConcurrentContentSigner signer = doCreateSigner(type, confWithoutAlgo, hashAlgo, mgf1, certs);
        validateSigner(signer, certs, type, confWithoutAlgo);
        return signer;
    }

    @Override
    public ConcurrentContentSigner createSigner(String type, String conf, X509Certificate[] certificateChain)
    throws SignerException
    {
        ConcurrentContentSigner signer = doCreateSigner(type, conf, null, false, certificateChain);
        validateSigner(signer, certificateChain, type, conf);
        return signer;
    }

    private static void validateSigner(
            ConcurrentContentSigner signer, X509Certificate[] certificateChain,
            String signerType, String signerConf)
    throws SignerException
    {
        X509Certificate cert = signer.getCertificate();
        if(certificateChain == null)
        {
            return;
        }

        String signatureAlgoName = SignerUtil.getSignatureAlgoName(signer.getAlgorithmIdentifier());
        ContentSigner csigner;
        try
        {
            csigner = signer.borrowContentSigner();
        } catch (NoIdleSignerException e)
        {
            throw new SignerException(e);
        }

        try
        {
            byte[] dummyContent = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            Signature verifier = Signature.getInstance(signatureAlgoName, "BC");

            OutputStream signatureStream = csigner.getOutputStream();
            signatureStream.write(dummyContent);
            byte[] signatureValue = csigner.getSignature();

            verifier.initVerify(cert.getPublicKey());
            verifier.update(dummyContent);
            boolean valid = verifier.verify(signatureValue);
            if(valid == false)
            {
                String subject = IoCertUtil.canonicalizeName(cert.getSubjectX500Principal());

                StringBuilder sb = new StringBuilder();
                sb.append("key and certificate not match. ");
                sb.append("key type='").append(signerType).append("'; ");

                ConfPairs keyValues = new ConfPairs(signerConf);
                String pwd = keyValues.getValue("password");
                if(pwd != null)
                {
                    keyValues.putPair("password", "****");
                }
                keyValues.putPair("algo", signatureAlgoName);
                sb.append("conf='").append(keyValues.getEncoded()).append("', ");
                sb.append("certificate subject='").append(subject).append("'");

                throw new SignerException(sb.toString());
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException |
                SignatureException | NoSuchProviderException e)
        {
            throw new SignerException(e.getMessage(), e);
        }
        finally
        {
            if(csigner != null)
            {
                signer.returnContentSigner(csigner);
            }
        }
    }

    private ConcurrentContentSigner doCreateSigner(
            String type, String conf, String hashAlgo, boolean mgf1,
            X509Certificate[] certificateChain)
    throws SignerException
    {
        if("PKCS11".equalsIgnoreCase(type) || "PKCS12".equalsIgnoreCase(type) || "JKS".equalsIgnoreCase(type))
        {
            ConfPairs keyValues = new ConfPairs(conf);

            String s = keyValues.getValue("parallelism");
            int parallelism = defaultParallelism;
            if(s != null)
            {
                try
                {
                    parallelism = Integer.parseInt(s);
                }catch(NumberFormatException e)
                {
                    throw new SignerException("Invalid parallelism " + s);
                }

                if(parallelism < 1)
                {
                    throw new SignerException("Invalid parallelism " + s);
                }
            }

            if("PKCS11".equalsIgnoreCase(type))
            {
                String pkcs11Module = keyValues.getValue("module");
                if(pkcs11Module == null)
                {
                    pkcs11Module = DEFAULT_P11MODULE_NAME;
                }

                s = keyValues.getValue("slot");
                Integer slotIndex = (s == null) ? null : Integer.parseInt(s);

                s = keyValues.getValue("slot-id");
                Long slotId = (s == null) ? null : Long.parseLong(s);

                if((slotIndex == null && slotId == null) || (slotIndex != null && slotId != null))
                {
                    throw new SignerException("Exactly one of slot (index) and slot-id must be specified");
                }
                P11SlotIdentifier slot = new P11SlotIdentifier(slotIndex, slotId);

                String keyLabel = keyValues.getValue("key-label");
                s = keyValues.getValue("key-id");
                byte[] keyId = null;
                if(s != null)
                {
                    keyId = Hex.decode(s);
                }

                if((keyId == null && keyLabel == null) || (keyId != null && keyLabel != null))
                {
                    throw new SignerException("Exactly one of key-id and key-label must be specified");
                }

                P11KeyIdentifier keyIdentifier;
                if(keyId != null)
                {
                    keyIdentifier = new P11KeyIdentifier(keyId);
                }
                else
                {
                    keyIdentifier = new P11KeyIdentifier(keyLabel);
                }

                P11CryptService p11CryptService = getP11CryptService(pkcs11Module);
                P11ContentSignerBuilder signerBuilder = new P11ContentSignerBuilder(
                            p11CryptService, slot, keyIdentifier, certificateChain);

                try
                {
                    AlgorithmIdentifier signatureAlgId;
                    if(hashAlgo == null)
                    {
                        signatureAlgId = getSignatureAlgoId(conf);
                    }
                    else
                    {
                        PublicKey pubKey;
                        try
                        {
                            pubKey = getPkcs11PublicKey(pkcs11Module, slot, keyIdentifier);
                        } catch (InvalidKeyException e)
                        {
                            throw new SignerException("invalid key: " + e.getMessage(), e);
                        }

                        signatureAlgId = getSignatureAlgoId(pubKey, hashAlgo, mgf1);
                    }
                    return signerBuilder.createSigner(signatureAlgId, parallelism);
                } catch (OperatorCreationException | NoSuchPaddingException e)
                {
                    throw new SignerException(e.getMessage());
                }

            }
            else
            {
                String passwordHint = keyValues.getValue("password");
                char[] password;
                if(passwordHint == null)
                {
                    password = null;
                }
                else
                {
                    if(passwordResolver == null)
                    {
                        password = passwordHint.toCharArray();
                    }
                    else
                    {
                        try
                        {
                            password = passwordResolver.resolvePassword(passwordHint);
                        }catch(PasswordResolverException e)
                        {
                            throw new SignerException("Could not resolve password. Message: " + e.getMessage());
                        }
                    }
                }

                s = keyValues.getValue("keystore");
                String keyLabel = keyValues.getValue("key-label");

                InputStream keystoreStream;
                if(s.startsWith("base64:"))
                {
                    keystoreStream = new ByteArrayInputStream(
                            Base64.decode(s.substring("base64:".length())));
                }
                else if(s.startsWith("file:"))
                {
                    String fn = s.substring("file:".length());
                    try
                    {
                        keystoreStream = new FileInputStream(IoCertUtil.expandFilepath(fn));
                    } catch (FileNotFoundException e)
                    {
                        throw new SignerException("File not found: " + fn);
                    }
                }
                else
                {
                    throw new SignerException("Unknown keystore content format");
                }

                SoftTokenContentSignerBuilder signerBuilder;
                try
                {
                    signerBuilder = new SoftTokenContentSignerBuilder(
                            type, keystoreStream, password, keyLabel, password, certificateChain);
                } catch (SignerException e)
                {
                    throw new SignerException(e.getMessage());
                }

                try
                {
                    AlgorithmIdentifier signatureAlgId;
                    if(hashAlgo == null)
                    {
                        signatureAlgId = getSignatureAlgoId(conf);
                    }
                    else
                    {
                        PublicKey pubKey = signerBuilder.getCert().getPublicKey();
                        signatureAlgId = getSignatureAlgoId(pubKey, hashAlgo, mgf1);
                    }

                    return signerBuilder.createSigner(
                            signatureAlgId, parallelism);
                } catch (OperatorCreationException | NoSuchPaddingException e)
                {
                    throw new SignerException(e.getMessage());
                }
            }
        }
        else if(type.toLowerCase().startsWith("java:"))
        {
            if(hashAlgo == null)
            {
                ConcurrentContentSigner contentSigner;
                String classname = type.substring("java:".length());
                try
                {
                    Class<?> clazz = Class.forName(classname);
                    contentSigner = (ConcurrentContentSigner) clazz.newInstance();
                }catch(Exception e)
                {
                    throw new SignerException(e.getMessage(), e);
                }
                contentSigner.initialize(conf, passwordResolver);

                if(certificateChain != null)
                {
                    contentSigner.setCertificateChain(certificateChain);
                }

                return contentSigner;
            }
            else
            {
                throw new SignerException("unknwon type: " + type);
            }
        }
        else
        {
            throw new SignerException("unknwon type: " + type);
        }
    }

    private AlgorithmIdentifier getSignatureAlgoId(String signerConf)
    throws SignerException
    {
        ConfPairs keyValues = new ConfPairs(signerConf);
        String algoS = keyValues.getValue("algo");
        if(algoS == null)
        {
            throw new SignerException("algo is not specified");
        }
        algoS = algoS.replaceAll("-", "");

        AlgorithmIdentifier signatureAlgId;
        if("SHA1withRSAandMGF1".equalsIgnoreCase(algoS) || "SHA224withRSAandMGF1".equalsIgnoreCase(algoS) ||
                "SHA256withRSAandMGF1".equalsIgnoreCase(algoS) || "SHA384withRSAandMGF1".equalsIgnoreCase(algoS) ||
                "SHA512withRSAandMGF1".equalsIgnoreCase(algoS))
        {
            ASN1ObjectIdentifier hashAlgo;
            if("SHA1withRSAandMGF1".equalsIgnoreCase(algoS))
            {
                hashAlgo = X509ObjectIdentifiers.id_SHA1;
            }
            else if("SHA224withRSAandMGF1".equalsIgnoreCase(algoS))
            {
                hashAlgo = NISTObjectIdentifiers.id_sha224;
            }
            else if("SHA256withRSAandMGF1".equalsIgnoreCase(algoS))
            {
                hashAlgo = NISTObjectIdentifiers.id_sha256;
            }
            else if("SHA384withRSAandMGF1".equalsIgnoreCase(algoS))
            {
                hashAlgo = NISTObjectIdentifiers.id_sha384;
            }
            else if("SHA512withRSAandMGF1".equalsIgnoreCase(algoS))
            {
                hashAlgo = NISTObjectIdentifiers.id_sha512;
            }
            else
            {
                throw new RuntimeException("should not reach here");
            }

            try
            {
                signatureAlgId = SignerUtil.buildRSAPSSAlgorithmIdentifier(hashAlgo);
            } catch (NoSuchAlgorithmException e)
            {
                throw new SignerException(e.getMessage(), e);
            }
        }
        else
        {
            boolean withNullParam = false;
            ASN1ObjectIdentifier algOid;
            if("SHA1withRSA".equalsIgnoreCase(algoS) || "RSAwithSHA1".equalsIgnoreCase(algoS) ||
                    PKCSObjectIdentifiers.sha1WithRSAEncryption.getId().equals(algoS))
            {
                algOid = PKCSObjectIdentifiers.sha1WithRSAEncryption;
                withNullParam = true;
            }
            else if("SHA224withRSA".equalsIgnoreCase(algoS) || "RSAwithSHA224".equalsIgnoreCase(algoS) ||
                    PKCSObjectIdentifiers.sha224WithRSAEncryption.getId().equals(algoS))
            {
                algOid = PKCSObjectIdentifiers.sha224WithRSAEncryption;
                withNullParam = true;
            }
            else if("SHA256withRSA".equalsIgnoreCase(algoS) || "RSAwithSHA256".equalsIgnoreCase(algoS) ||
                    PKCSObjectIdentifiers.sha256WithRSAEncryption.getId().equals(algoS))
            {
                algOid = PKCSObjectIdentifiers.sha256WithRSAEncryption;
                withNullParam = true;
            }
            else if("SHA384withRSA".equalsIgnoreCase(algoS) || "RSAwithSHA384".equalsIgnoreCase(algoS) ||
                    PKCSObjectIdentifiers.sha384WithRSAEncryption.getId().equals(algoS))
            {
                algOid = PKCSObjectIdentifiers.sha384WithRSAEncryption;
                withNullParam = true;
            }
            else if("SHA512withRSA".equalsIgnoreCase(algoS) || "RSAwithSHA512".equalsIgnoreCase(algoS) ||
                    PKCSObjectIdentifiers.sha512WithRSAEncryption.getId().equals(algoS))
            {
                algOid = PKCSObjectIdentifiers.sha512WithRSAEncryption;
                withNullParam = true;
            }
            else if("SHA1withECDSA".equalsIgnoreCase(algoS) || "ECDSAwithSHA1".equalsIgnoreCase(algoS) ||
                    X9ObjectIdentifiers.ecdsa_with_SHA1.getId().equals(algoS))
            {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA1;
            }
            else if("SHA224withECDSA".equalsIgnoreCase(algoS) || "ECDSAwithSHA224".equalsIgnoreCase(algoS) ||
                    X9ObjectIdentifiers.ecdsa_with_SHA224.getId().equals(algoS))
            {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA224;
            }
            else if("SHA256withECDSA".equalsIgnoreCase(algoS) || "ECDSAwithSHA256".equalsIgnoreCase(algoS) ||
                    X9ObjectIdentifiers.ecdsa_with_SHA256.getId().equals(algoS))
            {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA256;
            }
            else if("SHA384withECDSA".equalsIgnoreCase(algoS) || "ECDSAwithSHA384".equalsIgnoreCase(algoS) ||
                    X9ObjectIdentifiers.ecdsa_with_SHA384.getId().equals(algoS))
            {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA384;
            }
            else if("SHA512withECDSA".equalsIgnoreCase(algoS) || "ECDSAwithSHA512".equalsIgnoreCase(algoS) ||
                    X9ObjectIdentifiers.ecdsa_with_SHA512.getId().equals(algoS))
            {
                algOid = X9ObjectIdentifiers.ecdsa_with_SHA512;
            }
            else if("SHA1withDSA".equalsIgnoreCase(algoS) || "DSAwithSHA1".equalsIgnoreCase(algoS) ||
                    X9ObjectIdentifiers.id_dsa_with_sha1.getId().equals(algoS))
            {
                algOid = X9ObjectIdentifiers.id_dsa_with_sha1;
            }
            else if("SHA224withDSA".equalsIgnoreCase(algoS) || "DSAwithSHA224".equalsIgnoreCase(algoS) ||
                    NISTObjectIdentifiers.dsa_with_sha224.getId().equals(algoS))
            {
                algOid = NISTObjectIdentifiers.dsa_with_sha224;
            }
            else if("SHA256withDSA".equalsIgnoreCase(algoS) || "DSAwithSHA256".equalsIgnoreCase(algoS) ||
                    NISTObjectIdentifiers.dsa_with_sha256.getId().equals(algoS))
            {
                algOid = NISTObjectIdentifiers.dsa_with_sha256;
            }
            else if("SHA384withDSA".equalsIgnoreCase(algoS) || "DSAwithSHA384".equalsIgnoreCase(algoS) ||
                    NISTObjectIdentifiers.dsa_with_sha384.getId().equals(algoS))
            {
                algOid = NISTObjectIdentifiers.dsa_with_sha384;
            }
            else if("SHA512withDSA".equalsIgnoreCase(algoS) || "DSAwithSHA512".equalsIgnoreCase(algoS) ||
                    NISTObjectIdentifiers.dsa_with_sha512.getId().equals(algoS))
            {
                algOid = NISTObjectIdentifiers.dsa_with_sha512;
            }
            else
            {
                throw new SignerException("Unsupported signature algorithm " + algoS);
            }

            signatureAlgId = withNullParam ? new AlgorithmIdentifier(algOid, DERNull.INSTANCE) :
                new AlgorithmIdentifier(algOid);
        }

        return signatureAlgId;
    }

    private AlgorithmIdentifier getSignatureAlgoId(PublicKey pubKey, String hashAlgo, boolean mgf1)
    throws SignerException
    {
        AlgorithmIdentifier signatureAlgId;
        if(pubKey instanceof RSAPublicKey)
        {
            if(mgf1)
            {
                ASN1ObjectIdentifier hashAlgoOid;
                if("SHA1".equalsIgnoreCase(hashAlgo))
                {
                    hashAlgoOid = X509ObjectIdentifiers.id_SHA1;
                }
                else if("SHA224".equalsIgnoreCase(hashAlgo))
                {
                    hashAlgoOid = NISTObjectIdentifiers.id_sha224;
                }
                else if("SHA256".equalsIgnoreCase(hashAlgo))
                {
                    hashAlgoOid = NISTObjectIdentifiers.id_sha256;
                }
                else if("SHA384".equalsIgnoreCase(hashAlgo))
                {
                    hashAlgoOid = NISTObjectIdentifiers.id_sha384;
                }
                else if("SHA512".equalsIgnoreCase(hashAlgo))
                {
                    hashAlgoOid = NISTObjectIdentifiers.id_sha512;
                }
                else
                {
                    throw new RuntimeException("Unsupported hash algorithm " + hashAlgo);
                }

                try
                {
                    signatureAlgId = SignerUtil.buildRSAPSSAlgorithmIdentifier(hashAlgoOid);
                } catch (NoSuchAlgorithmException e)
                {
                    throw new SignerException(e.getMessage(), e);
                }
            }
            else
            {
                ASN1ObjectIdentifier sigAlgoOid;
                if("SHA1".equalsIgnoreCase(hashAlgo))
                {
                    sigAlgoOid = PKCSObjectIdentifiers.sha1WithRSAEncryption;
                }
                else if("SHA224".equalsIgnoreCase(hashAlgo))
                {
                    sigAlgoOid = PKCSObjectIdentifiers.sha224WithRSAEncryption;
                }
                else if("SHA256".equalsIgnoreCase(hashAlgo))
                {
                    sigAlgoOid = PKCSObjectIdentifiers.sha256WithRSAEncryption;
                }
                else if("SHA384".equalsIgnoreCase(hashAlgo))
                {
                    sigAlgoOid = PKCSObjectIdentifiers.sha384WithRSAEncryption;
                }
                else if("SHA512".equalsIgnoreCase(hashAlgo))
                {
                    sigAlgoOid = PKCSObjectIdentifiers.sha512WithRSAEncryption;
                }
                else
                {
                    throw new RuntimeException("Unsupported hash algorithm " + hashAlgo);
                }

                signatureAlgId = new AlgorithmIdentifier(sigAlgoOid, DERNull.INSTANCE);
            }
        }
        else if(pubKey instanceof ECPublicKey)
        {
            ASN1ObjectIdentifier sigAlgoOid;
            if("SHA1".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = X9ObjectIdentifiers.ecdsa_with_SHA1;
            }
            else if("SHA224".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = X9ObjectIdentifiers.ecdsa_with_SHA224;
            }
            else if("SHA256".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = X9ObjectIdentifiers.ecdsa_with_SHA256;
            }
            else if("SHA384".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = X9ObjectIdentifiers.ecdsa_with_SHA384;
            }
            else if("SHA512".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = X9ObjectIdentifiers.ecdsa_with_SHA512;
            }
            else
            {
                throw new RuntimeException("Unsupported hash algorithm " + hashAlgo);
            }

            signatureAlgId = new AlgorithmIdentifier(sigAlgoOid);
        }
        else if(pubKey instanceof DSAPublicKey)
        {
            ASN1ObjectIdentifier sigAlgoOid;
            if("SHA1".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = X9ObjectIdentifiers.id_dsa_with_sha1;
            }
            else if("SHA224".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = NISTObjectIdentifiers.dsa_with_sha224;
            }
            else if("SHA256".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = NISTObjectIdentifiers.dsa_with_sha256;
            }
            else if("SHA384".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = NISTObjectIdentifiers.dsa_with_sha384;
            }
            else if("SHA512".equalsIgnoreCase(hashAlgo))
            {
                sigAlgoOid = NISTObjectIdentifiers.dsa_with_sha512;
            }
            else
            {
                throw new RuntimeException("Unsupported hash algorithm " + hashAlgo);
            }

            signatureAlgId = new AlgorithmIdentifier(sigAlgoOid);
        }
        else
        {
            throw new SignerException("Unsupported key type " + pubKey.getClass().getName());
        }

        return signatureAlgId;
    }

    @Override
    public ContentVerifierProvider getContentVerifierProvider(PublicKey publicKey)
    throws InvalidKeyException
    {
        try
        {
            return KeyUtil.getContentVerifierProvider(publicKey);
        } catch (OperatorCreationException e)
        {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    public ContentVerifierProvider getContentVerifierProvider(X509Certificate cert)
    throws InvalidKeyException
    {
        try
        {
            return KeyUtil.getContentVerifierProvider(cert);
        } catch (OperatorCreationException e)
        {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    public ContentVerifierProvider getContentVerifierProvider(X509CertificateHolder cert)
    throws InvalidKeyException
    {
        try
        {
            PublicKey pk = KeyUtil.generatePublicKey(cert.getSubjectPublicKeyInfo());
            return KeyUtil.getContentVerifierProvider(pk);
        } catch (OperatorCreationException | NoSuchAlgorithmException | InvalidKeySpecException | IOException e)
        {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    public PublicKey generatePublicKey(SubjectPublicKeyInfo subjectPublicKeyInfo)
    throws InvalidKeyException
    {
        try
        {
            return KeyUtil.generatePublicKey(subjectPublicKeyInfo);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e)
        {
            throw new InvalidKeyException(e);
        }
    }

    @Override
    public boolean verifyPOPO(CertificationRequest p10Req)
    {
        return SignerUtil.verifyPOP(p10Req);
    }

    public void setPkcs11Provider(String pkcs11Provider)
    {
        this.pkcs11Provider = pkcs11Provider;
    }

    public void setDefaultParallelism(int defaultParallelism)
    {
        if(defaultParallelism > 0)
        {
            this.defaultParallelism = defaultParallelism;
        }
    }

    public static String getKeystoreSignerConf(String keystoreFile, String password,
            String signatureAlgorithm, int parallelism)
    {
        return getKeystoreSignerConf(keystoreFile, password, signatureAlgorithm, parallelism, null);
    }

    public static String getKeystoreSignerConf(String keystoreFile, String password,
            String signatureAlgorithm, int parallelism, String keyLabel)
    {
        ParamChecker.assertNotEmpty("keystoreFile", keystoreFile);
        ParamChecker.assertNotEmpty("password", password);
        ParamChecker.assertNotNull("signatureAlgorithm", signatureAlgorithm);

        ConfPairs conf = new ConfPairs("password", password);
        conf.putPair("algo", signatureAlgorithm);
        conf.putPair("parallelism", Integer.toString(parallelism));
        if(keyLabel != null)
        {
            conf.putPair("key-label", keyLabel);
        }
        conf.putPair("keystore", "file:" + keystoreFile);

        return conf.getEncoded();
    }

    public static String getKeystoreSignerConfWithoutAlgo(String keystoreFile, String password, int parallelism)
    {
        return getKeystoreSignerConfWithoutAlgo(keystoreFile, password, parallelism, null);
    }

    public static String getKeystoreSignerConfWithoutAlgo(String keystoreFile, String password,
            int parallelism, String keyLabel)
    {
        ParamChecker.assertNotEmpty("keystoreFile", keystoreFile);
        ParamChecker.assertNotEmpty("password", password);

        ConfPairs conf = new ConfPairs("password", password);
        conf.putPair("parallelism", Integer.toString(parallelism));
        if(keyLabel != null)
        {
            conf.putPair("key-label", keyLabel);
        }
        conf.putPair("keystore", "file:" + keystoreFile);

        return conf.getEncoded();
    }

    public static String getPkcs11SignerConf(String pkcs11ModuleName, P11SlotIdentifier slotId,
            P11KeyIdentifier keyId, String signatureAlgorithm, int parallelism)
    {
        ParamChecker.assertNotNull("algo", signatureAlgorithm);
        ParamChecker.assertNotNull("keyId", keyId);

        ConfPairs conf = new ConfPairs("algo", signatureAlgorithm);
        conf.putPair("parallelism", Integer.toString(parallelism));

        if(pkcs11ModuleName != null && pkcs11ModuleName.length() > 0)
        {
            conf.putPair("module", pkcs11ModuleName);
        }

        if(slotId.getSlotId() != null)
        {
            conf.putPair("slot-id", slotId.getSlotId().toString());
        }
        else
        {
            conf.putPair("slot", slotId.getSlotIndex().toString());
        }

        if(keyId.getKeyId() != null)
        {
            conf.putPair("key-id", Hex.toHexString(keyId.getKeyId()));
        }

        if(keyId.getKeyLabel() != null)
        {
            conf.putPair("key-label", keyId.getKeyLabel());
        }

        return conf.getEncoded();
    }

    public static String getPkcs11SignerConfWithoutAlgo(String pkcs11ModuleName, P11SlotIdentifier slotId,
            P11KeyIdentifier keyId, int parallelism)
    {
        ParamChecker.assertNotNull("keyId", keyId);

        ConfPairs conf = new ConfPairs();
        conf.putPair("parallelism", Integer.toString(parallelism));

        if(pkcs11ModuleName != null && pkcs11ModuleName.length() > 0)
        {
            conf.putPair("module", pkcs11ModuleName);
        }

        if(slotId.getSlotId() != null)
        {
            conf.putPair("slot-id", slotId.getSlotId().toString());
        }
        else
        {
            conf.putPair("slot", slotId.getSlotIndex().toString());
        }

        if(keyId.getKeyId() != null)
        {
            conf.putPair("key-id", Hex.toHexString(keyId.getKeyId()));
        }

        if(keyId.getKeyLabel() != null)
        {
            conf.putPair("key-label", keyId.getKeyLabel());
        }

        return conf.getEncoded();
    }
    @Override
    public P11CryptService getP11CryptService(String moduleName)
    throws SignerException
    {
        initP11CryptServiceFactory();
        return p11CryptServiceFactory.createP11CryptService(
                getRealPkcs11ModuleName(moduleName));
    }

    @Override
    public Set<String> getPkcs11ModuleNames()
    {
        initPkcs11ModuleConf();
        return p11Control == null ? null : p11Control.getModuleNames();
    }

    private synchronized void initP11CryptServiceFactory()
    throws SignerException
    {
        if(p11CryptServiceFactory != null)
        {
            return;
        }

        if(p11CryptServiciceFactoryInitialized)
        {
            throw new SignerException("Initialization of P11CryptServiceFactory has been processed and failed, no retry");
        }

        try
        {
            initPkcs11ModuleConf();

            Object p11Provider;
            try
            {
                Class<?> clazz = Class.forName(pkcs11Provider);
                p11Provider = clazz.newInstance();
            }catch(Exception e)
            {
                throw new SignerException(e.getMessage(), e);
            }

            if(p11Provider instanceof P11CryptServiceFactory)
            {
                P11CryptServiceFactory p11CryptServiceFact = (P11CryptServiceFactory) p11Provider;
                p11CryptServiceFact.init(p11Control);
                this.p11CryptServiceFactory = p11CryptServiceFact;
            }
            else
            {
                throw new SignerException(pkcs11Provider + " is not instanceof " + P11CryptServiceFactory.class.getName());
            }
        }finally
        {
            p11CryptServiciceFactoryInitialized = true;
        }
    }

    private void initPkcs11ModuleConf()
    {
        if(p11Control != null)
        {
            return;
        }

        if(pkcs11ConfFile == null || pkcs11ConfFile.isEmpty())
        {
            Set<P11ModuleConf> confs = new HashSet<>();

            if(pkcs11Provider == null | pkcs11Provider.isEmpty())
            {
            }
            else
            {
                P11ModuleConf conf = new P11ModuleConf(DEFAULT_P11MODULE_NAME, pkcs11Module,
                        P11NullPasswordRetriever.INSTANCE, pkcs11IncludeSlots, pkcs11ExcludeSlots);
                confs.add(conf);
            }
            this.p11Control = new P11Control(DEFAULT_P11MODULE_NAME, confs);
        }
        else
        {
            try
            {
                JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                SchemaFactory schemaFact = SchemaFactory.newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = schemaFact.newSchema(getClass().getResource("/xsd/pkcs11-conf.xsd"));
                unmarshaller.setSchema(schema);
                @SuppressWarnings("unchecked")
                JAXBElement<PKCS11ConfType> rootElement = (JAXBElement<PKCS11ConfType>)
                        unmarshaller.unmarshal(new File(pkcs11ConfFile));
                PKCS11ConfType pkcs11Conf = rootElement.getValue();
                ModulesType modulesType = pkcs11Conf.getModules();

                Map<String, P11ModuleConf> confs = new HashMap<>();
                for(ModuleType moduleType : modulesType.getModule())
                {
                    String name = moduleType.getName();
                    if(DEFAULT_P11MODULE_NAME.equals(name))
                    {
                        throw new ConfigurationException("invald module name " + DEFAULT_P11MODULE_NAME + ", it is reserved");
                    }

                    if(confs.containsKey(name))
                    {
                        throw new ConfigurationException("Multiple modules with the same module name is not permitted");
                    }

                    P11PasswordRetriever pwdRetriever;

                    PasswordsType passwordsType = moduleType.getPasswords();
                    if(passwordsType == null || passwordsType.getPassword().isEmpty())
                    {
                        pwdRetriever = P11NullPasswordRetriever.INSTANCE;
                    }
                    else
                    {
                        pwdRetriever = new P11PasswordRetrieverImpl();
                        ((P11PasswordRetrieverImpl) pwdRetriever).setPasswordResolver(passwordResolver);

                        for(PasswordType passwordType : passwordsType.getPassword())
                        {
                            Set<P11SlotIdentifier> slots = getSlots(passwordType.getSlots());
                            ((P11PasswordRetrieverImpl) pwdRetriever).addPasswordEntry(
                                    slots, new ArrayList<>(passwordType.getSinglePassword()));
                        }
                    }

                    Set<P11SlotIdentifier> includeSlots = getSlots(moduleType.getIncludeSlots());
                    Set<P11SlotIdentifier> excludeSlots = getSlots(moduleType.getExcludeSlots());

                    final String osName = System.getProperty("os.name").toLowerCase();
                    String nativeLibraryPath = null;
                    for(NativeLibraryType library : moduleType.getNativeLibraries().getNativeLibrary())
                    {
                        List<String> osNames = library.getOs();
                        if(osNames == null || osNames.isEmpty())
                        {
                            nativeLibraryPath = library.getPath();
                        }
                        else
                        {
                            for(String entry : osNames)
                            {
                                if(osName.contains(entry.toLowerCase()))
                                {
                                    nativeLibraryPath = library.getPath();
                                    break;
                                }
                            }
                        }

                        if(nativeLibraryPath != null)
                        {
                            break;
                        }
                    }

                    if(nativeLibraryPath == null)
                    {
                        throw new ConfigurationException("Could not find PKCS#11 library for OS " + osName);
                    }

                    File f = new File(nativeLibraryPath);
                    if(f.exists() == false)
                    {
                        throw new ConfigurationException("PKCS#11 library " + f.getAbsolutePath() + " does not exist");
                    }
                    if(f.isFile() == false)
                    {
                        throw new ConfigurationException("PKCS#11 library " + f.getAbsolutePath() +
                                " does not point to a file");
                    }
                    if(f.canRead() == false)
                    {
                        throw new ConfigurationException("No permission to access PKCS#11 library " + f.getAbsolutePath());
                    }

                    P11ModuleConf conf = new P11ModuleConf(name,
                            nativeLibraryPath, pwdRetriever, includeSlots, excludeSlots);
                    confs.put(name, conf);
                }

                final String defaultModuleName = modulesType.getDefaultModule();
                if(confs.containsKey(defaultModuleName) == false)
                {
                    throw new ConfigurationException("Default module " + defaultModuleName + " is not defined");
                }

                this.p11Control = new P11Control(defaultModuleName, new HashSet<>(confs.values()));
            } catch (JAXBException | SAXException | ConfigurationException e)
            {
                final String message = "Invalid configuration file " + pkcs11ConfFile;
                if(LOG.isErrorEnabled())
                {
                    LOG.error(LogUtil.buildExceptionLogFormat(message), e.getClass().getName(), e.getMessage());
                }
                LOG.debug(message, e);

                throw new RuntimeException(message);
            }
        }
    }

    public void setPkcs11ConfFile(String confFile)
    {
        if(confFile != null && confFile.isEmpty())
        {
            this.pkcs11ConfFile = null;
        }
        else
        {
            this.pkcs11ConfFile = confFile;
        }
    }

    @Deprecated
    public void setPkcs11Module(String pkcs11Module)
    {
        this.pkcs11Module = pkcs11Module;
    }

    @Deprecated
    public void setPkcs11IncludeSlots(String indexes)
    {
        this.pkcs11IncludeSlots = getSlots(indexes);
    }

    @Deprecated
    public void setPkcs11ExcludeSlots(String indexes)
    {
        this.pkcs11ExcludeSlots = getSlots(indexes);
    }

    private static Set<P11SlotIdentifier> getSlots(SlotsType type)
    throws ConfigurationException
    {
        if(type == null || type.getSlot().isEmpty())
        {
            return null;
        }

        Set<P11SlotIdentifier> slots = new HashSet<>();
        for(SlotType slotType : type.getSlot())
        {
            Long slotId = null;
            if(slotType.getId() != null)
            {
                String str = slotType.getId().trim();
                try
                {
                    if(str.startsWith("0x") || str.startsWith("0X"))
                    {
                        slotId = Long.parseLong(str.substring(2), 16);
                    }
                    else
                    {
                        slotId = Long.parseLong(str);
                    }
                }catch(NumberFormatException e)
                {
                    String message = "invalid slotId '" + str + "'";
                    LOG.error(message);
                    throw new ConfigurationException(message);
                }
            }
            slots.add(new P11SlotIdentifier(slotType.getIndex(), slotId));
        }

        return slots;
    }

    @Deprecated
    private static Set<P11SlotIdentifier> getSlots(String indexes)
    {
        if(indexes == null || indexes.trim().isEmpty())
        {
            return null;
        }

        Set<String> slotStrs = StringUtil.splitAsSet(indexes.trim(), ", ");
        if(slotStrs.isEmpty())
        {
            return null;
        }

        Set<P11SlotIdentifier> slots = new HashSet<>(slotStrs.size());
        for(String slotStr : slotStrs)
        {
            int slotIndex;
            try
            {
                slotIndex = Integer.parseInt(slotStr);
            }catch(NumberFormatException e)
            {
                throw new RuntimeException("Invalid slot index " + slotStr);
            }
            slots.add(new P11SlotIdentifier(slotIndex, null));
        }

        return Collections.unmodifiableSet(slots);
    }

    private String getRealPkcs11ModuleName(String moduleName)
    {
        if(moduleName == null || DEFAULT_P11MODULE_NAME.equals(moduleName))
        {
            return getDefaultPkcs11ModuleName();
        }
        else
        {
            return moduleName;
        }
    }

    public void setPasswordResolver(PasswordResolver passwordResolver)
    {
        this.passwordResolver = passwordResolver;
    }

    @Override
    public String getDefaultPkcs11ModuleName()
    {
        initPkcs11ModuleConf();
        return p11Control == null ? null : p11Control.getDefaultModuleName();
    }

    @Override
    public PasswordResolver getPasswordResolver()
    {
        return passwordResolver;
    }

    @Override
    public PublicKey getPkcs11PublicKey(String moduleName, P11SlotIdentifier slotId, P11KeyIdentifier keyId)
    throws InvalidKeyException
    {
        try
        {
            P11CryptService p11 = getP11CryptService(moduleName);
            return p11 == null ? null : p11.getPublicKey(slotId, keyId);
        } catch (SignerException e)
        {
            throw new InvalidKeyException(e.getMessage(), e);
        }
    }

}
