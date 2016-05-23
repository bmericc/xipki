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

package org.xipki.pki.ca.server.mgmt.shell;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Properties;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.console.karaf.CmdFailure;
import org.xipki.commons.console.karaf.completer.FilePathCompleter;
import org.xipki.commons.security.CertRevocationInfo;
import org.xipki.pki.ca.server.mgmt.api.CaEntry;
import org.xipki.pki.ca.server.mgmt.api.CertArt;
import org.xipki.pki.ca.server.mgmt.api.x509.X509CaEntry;
import org.xipki.pki.ca.server.mgmt.shell.completer.CaNameCompleter;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

@Command(scope = "xipki-ca", name = "ca-export",
        description = "export CA configuration")
@Service
public class CaExportCmd extends CaCommandSupport {

    static final String KEY_ART = "ART";

    static final String KEY_SN_SIZE = "SN_SIZE";

    static final String KEY_NEXT_CRLNO = "NEXT_CRLNO";

    static final String KEY_STATUS = "STATUS";

    static final String KEY_CACERT_URIS = "CACERT_URIS";

    static final String KEY_CRL_URIS = "CRL_URIS";

    static final String KEY_DELTACRL_URIS = "DELTACRL_URIS";

    static final String KEY_OCSP_URIS = "OCSP_URIS";

    static final String KEY_MAX_VALIDITY = "MAX_VALIDITY";

    static final String KEY_CRLSIGNER_NAME = "CRLSIGNER_NAME";

    static final String KEY_CMPCONTROL_NAME = "CMPCONTROL_NAME";

    static final String KEY_DUPLICATE_KEY = "DUPLICATE_KEY";

    static final String KEY_DUPLICATE_SUBJECT = "DUPLICATE_SUBJECT";

    static final String KEY_VALIDITY_MODE = "VALIDITY_MODE";

    static final String KEY_PERMISSIONS = "PERMISSIONS";

    static final String KEY_NUM_CRLS = "NUM_CRLS";

    static final String KEY_EXPIRATION_PERIOD = "EXPIRATION_PERIOD";

    static final String KEY_KEEP_EXPIRED_CERT_DAYS = "KEEP_EXPIRED_CERT_DAYS";

    static final String KEY_REVOKED = "REVOKED";

    static final String KEY_REV_REASON = "RR";

    static final String KEY_REV_TIME = "RT";

    static final String KEY_REV_INV_TIME = "RIT";

    static final String KEY_SIGNER_TYPE = "SIGNER_TYPE";

    static final String KEY_SIGNER_CONF = "SIGNER_CONF";

    static final String KEY_CERT = "CERT";

    static final String KEY_EXTRA_CONTROL = "EXTRA_CONTROL";

    @Option(name = "--name", aliases = "-n",
            required = true,
            description = "CA name\n"
                    + "(required)")
    @Completion(CaNameCompleter.class)
    private String name;

    @Option(name = "--out", aliases = "-o",
            required = true,
            description = "where to save the CA configuration\n"
                    + "(required)")
    @Completion(FilePathCompleter.class)
    private String confFile;

    @Override
    protected Object doExecute() throws Exception {
        CaEntry entry = caManager.getCa(name);
        if (entry == null) {
            throw new CmdFailure("no CA named " + name + " is defined");
        }

        if (!(entry instanceof X509CaEntry)) {
            throw new CmdFailure("unsupported CAEntry type " + entry.getClass().getName());
        }

        X509CaEntry x509Entry = (X509CaEntry) entry;

        Properties props = new Properties();

        // ART
        propsput(props, KEY_ART, CertArt.X509PKC.name());

        // NEXT_SN
        propsput(props, KEY_SN_SIZE, x509Entry.getSerialNoSize());

        // NEXT_CRLNO
        propsput(props, KEY_NEXT_CRLNO, x509Entry.getNextCrlNumber());

        // STATUS
        propsput(props, KEY_STATUS, x509Entry.getStatus().name());

        // CACERT_URIS
        propsput(props, KEY_CACERT_URIS, x509Entry.getCacertUris());

        // CRL_URIS
        propsput(props, KEY_CRL_URIS, x509Entry.getCrlUrisAsString());

        // DELTACRL_URIS
        propsput(props, KEY_DELTACRL_URIS, x509Entry.getDeltaCrlUrisAsString());

        // OCSP_URIS
        propsput(props, KEY_OCSP_URIS, x509Entry.getOcspUrisAsString());

        // MAX_VALIDITY
        propsput(props, KEY_MAX_VALIDITY, x509Entry.getMaxValidity());

        // CRLSIGNER_NAME
        propsput(props, KEY_CRLSIGNER_NAME, x509Entry.getCrlSignerName());

        // CMPCONTROL_NAME
        propsput(props, KEY_CMPCONTROL_NAME, x509Entry.getCmpControlName());

        // DUPLICATE_KEY
        propsput(props, KEY_DUPLICATE_KEY, Boolean.toString(x509Entry.isDuplicateKeyPermitted()));

        // DUPLICATE_SUBJECT
        propsput(props, KEY_DUPLICATE_SUBJECT,
                Boolean.toString(x509Entry.isDuplicateSubjectPermitted()));

        // VALIDITY_MODE
        propsput(props, KEY_VALIDITY_MODE, x509Entry.getValidityMode().name());

        // PERMISSIONS
        propsput(props, KEY_PERMISSIONS, x509Entry.getPermissionsAsText());

        // NUM_CRLS
        propsput(props, KEY_NUM_CRLS, x509Entry.getNumCrls());

        // EXPIRATION_PERIOD
        propsput(props, KEY_EXPIRATION_PERIOD, x509Entry.getExpirationPeriod());

        // KEEP_EXPIRED_CERT_DAYS
        propsput(props, KEY_KEEP_EXPIRED_CERT_DAYS, x509Entry.getKeepExpiredCertInDays());

        // REVOKED
        CertRevocationInfo revInfo = x509Entry.getRevocationInfo();
        propsput(props, KEY_REVOKED, revInfo != null);
        if (revInfo != null) {
            if (revInfo.getReason() != null) {
                propsput(props, KEY_REV_REASON, revInfo.getReason().getCode());
            }

            if (revInfo.getRevocationTime() != null) {
                propsput(props, KEY_REV_TIME, revInfo.getRevocationTime().getTime() / 1000);
            }

            if (revInfo.getInvalidityTime() != null) {
                propsput(props, KEY_REV_INV_TIME, revInfo.getInvalidityTime().getTime() / 1000);
            }
        }

        // SIGNER_TYPE
        propsput(props, KEY_SIGNER_TYPE, x509Entry.getSignerType());

        // SIGNER_CONF
        propsput(props, KEY_SIGNER_CONF, x509Entry.getSignerConf());

        // CERT
        byte[] bytes = x509Entry.getCertificate().getEncoded();
        propsput(props, KEY_CERT, IoUtil.base64Encode(bytes, false));

        // EXTRA_CONTROL
        propsput(props, KEY_EXTRA_CONTROL, x509Entry.getExtraControl());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.store(out, "CA configuration");
        saveVerbose("saved CA configuration to", new File(confFile), out.toByteArray());
        return null;
    } // method doExecute

    private static void propsput(final Properties props, final String key, final Object value) {
        if (value instanceof String) {
            props.put(key, (String) value);
        } else if (value != null) {
            props.put(key, value.toString());
        }
    }

}
