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

package org.xipki.ca.server.mgmt.shell;

import java.security.cert.X509Certificate;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.ca.server.mgmt.api.CrlSignerEntry;
import org.xipki.ca.server.mgmt.shell.completer.CrlSignerTypeCompleter;
import org.xipki.console.karaf.FilePathCompleter;
import org.xipki.security.api.SecurityFactory;
import org.xipki.security.common.IoCertUtil;

/**
 * @author Lijun Liao
 */

@Command(scope = "ca", name = "crlsigner-add", description="Add CRL signer")
@Service
public class CrlSignerAddCommand extends CaCommand
{
    @Option(name = "-name",
            description = "Required. CRL signer name",
            required = true, multiValued = false)
    protected String name;

    @Option(name = "-signerType",
            description = "Required. CRL signer type, use 'CA' to sign the CRL by the CA itself",
            required = true)
    @Completion(CrlSignerTypeCompleter.class)
    protected String signerType;

    @Option(name = "-signerConf",
            description = "CRL signer configuration")
    protected String signerConf;

    @Option(name = "-cert",
            description = "CRL signer's certificate file")
    @Completion(FilePathCompleter.class)
    protected String signerCertFile;

    @Option(name = "-crlControl",
            required = true, description = "Required. CRL control")
    protected String crlControl;

    @Reference
    private SecurityFactory securityFactory;

    @Override
    protected Object doExecute()
    throws Exception
    {
        X509Certificate signerCert = null;
        if("CA".equalsIgnoreCase(signerType) == false)
        {
            if(signerCertFile != null)
            {
                signerCert = IoCertUtil.parseCert(signerCertFile);
            }

            if(signerConf != null)
            {
                if("PKCS12".equalsIgnoreCase(signerType) || "JKS".equalsIgnoreCase(signerType))
                {
                    signerConf = ShellUtil.canonicalizeSignerConf(signerType,
                            signerConf, securityFactory.getPasswordResolver());
                }
            }
            // check whether we can initialize the signer
            securityFactory.createSigner(signerType, signerConf, signerCert);
        }

        CrlSignerEntry entry = new CrlSignerEntry(name, signerType, signerConf, crlControl);
        if(signerCert != null)
        {
            entry.setCertificate(signerCert);
        }
        caManager.addCrlSigner(entry);
        out("added CRL signer " + name);
        return null;
    }

}
