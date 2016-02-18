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

package org.xipki.ca.client.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.ca.client.impl.jaxb.CAClientType;
import org.xipki.ca.client.impl.jaxb.CAClientType.CAs;
import org.xipki.ca.client.impl.jaxb.CAClientType.Requestors;
import org.xipki.ca.client.impl.jaxb.CAInfoType;
import org.xipki.ca.client.impl.jaxb.CAInfoType.CertProfiles;
import org.xipki.ca.client.impl.jaxb.CAType;
import org.xipki.ca.client.impl.jaxb.FileOrValueType;
import org.xipki.ca.client.impl.jaxb.RequestorType;
import org.xipki.security.common.ConfigurationException;
import org.xipki.security.common.IoCertUtil;
import org.xipki.security.common.ParamChecker;
import org.xipki.security.common.StringUtil;

/**
 * @author Lijun Liao
 */

class LegacyConfConverter
{
    private static final Logger LOG = LoggerFactory.getLogger(LegacyConfConverter.class);

    static final String DEV_MODE = "dev.mode";

    static final String SIGN_REQUEST = "sign.request";

    /**
     * The certificate of the responder.
     */
    static final String REQUESTOR_CERT = "requestor.cert";

    /**
     * The type of requestorSigner signer
     */
    static final String REQUESTOR_SIGNER_TYPE = "requestor.signer.type";

    /**
     * The configuration of the requestorSigner signer
     */
    static final String REQUESTOR_SIGNER_CONF = "requestor.signer.conf";

    static final String CA_PREFIX = "ca.";

    static final String CA_ENABLED_SUFFIX = ".enabled";

    /**
     * certificate of the given CA
     */
    static final String CA_CERT_SUFFIX = ".cert";

    /**
     * URL of the given CA
     */
    static final String CA_URL_SUFFIX = ".url";

    static final String CA_RESPONDER_SUFFIX = ".responder";

    /**
     * Certificate profiles supported by the given CA
     */
    static final String CA_PROFILES_SUFFIX = ".profiles";

    public static CAClientType convertConf(String filename)
    throws ConfigurationException, IOException
    {
        return convertConf(new FileInputStream(IoCertUtil.expandFilepath(filename)));
    }
    public static CAClientType convertConf(InputStream confStream)
    throws ConfigurationException, IOException
    {
        ParamChecker.assertNotNull("confStream", confStream);
        Properties props = new Properties();
        try
        {
            props.load(confStream);
        }finally
        {
            try
            {
                confStream.close();
            }catch(IOException e)
            {
            }
        }

        CAClientType conf = new CAClientType();
        boolean dev_mode = Boolean.parseBoolean(props.getProperty(DEV_MODE, "false"));
        conf.setDevMode(dev_mode);

        // Requestor
        Requestors requestors = new Requestors();
        conf.setRequestors(requestors);

        RequestorType requestorConf = new RequestorType();
        requestors.getRequestor().add(requestorConf);

        String requestorName = "requestor1";
        requestorConf.setName(requestorName);
        boolean signRequest = Boolean.parseBoolean(props.getProperty(SIGN_REQUEST, "true"));
        requestorConf.setSignRequest(signRequest);

        String s = props.getProperty(REQUESTOR_CERT);
        if(s != null)
        {
            requestorConf.setCert(createFileOrValue(s));
        }

        s = props.getProperty(REQUESTOR_SIGNER_TYPE);
        if(s != null)
        {
            requestorConf.setSignerType(s);
        }

        s = props.getProperty(REQUESTOR_SIGNER_CONF);
        if(s != null)
        {
            requestorConf.setSignerConf(s);
        }

        Set<String> enabledCaNames = new HashSet<>();

        for(Object _propKey : props.keySet())
        {
            String propKey = (String) _propKey;
            if(propKey.startsWith(CA_PREFIX) && propKey.endsWith(CA_URL_SUFFIX))
            {
                String caName = propKey.substring(CA_PREFIX.length(),
                        propKey.length() - CA_URL_SUFFIX.length());

                String enabled = props.getProperty(CA_PREFIX + caName + CA_ENABLED_SUFFIX, "true");
                if(Boolean.parseBoolean(enabled))
                {
                    enabledCaNames.add(caName);
                }
                else
                {
                    LOG.info("CA " + caName + " is disabled");
                }
            }
        }

        CAs cas = new CAs();
        conf.setCAs(cas);

        for(String caName : enabledCaNames)
        {
            CAType ca = new CAType();
            ca.setEnabled(true);
            ca.setName(caName);
            ca.setRequestor(requestorName);

            s = props.getProperty(CA_PREFIX + caName + CA_URL_SUFFIX);
            ca.setUrl(s);

            CAInfoType caInfo = new CAInfoType();
            ca.setCAInfo(caInfo);

            s = props.getProperty(CA_PREFIX + caName + CA_CERT_SUFFIX);
            caInfo.setCert(createFileOrValue(s));

            s = props.getProperty(CA_PREFIX + caName + CA_RESPONDER_SUFFIX);
            caInfo.setResponder(createFileOrValue(s));

            String propKey = CA_PREFIX + caName + CA_PROFILES_SUFFIX;
            CertProfiles certProfiles = new CertProfiles();
            caInfo.setCertProfiles(certProfiles);

            if(props.containsKey(propKey))
            {
                s = props.getProperty(propKey);
                if(s != null && s.isEmpty() == false)
                {
                    Set<String> profiles = StringUtil.splitAsSet(s, ", ");
                    if(profiles.isEmpty() == false)
                    {
                        certProfiles.getCertProfile().addAll(profiles);
                    }
                }
            }

            cas.getCA().add(ca);
        }

        return conf;
    }

    private static FileOrValueType createFileOrValue(String filename)
    {
        FileOrValueType ret = new FileOrValueType();
        ret.setFile(filename);
        return ret;
    }
}
