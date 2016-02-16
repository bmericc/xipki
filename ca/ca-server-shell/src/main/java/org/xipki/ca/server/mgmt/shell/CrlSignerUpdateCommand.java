/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2014 Lijun Liao
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

package org.xipki.ca.server.mgmt.shell;

import java.io.ByteArrayInputStream;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.bouncycastle.util.encoders.Base64;
import org.xipki.ca.server.mgmt.api.CAManager;
import org.xipki.ca.server.mgmt.shell.completer.CrlSignerNameCompleter;
import org.xipki.console.karaf.FilePathCompleter;
import org.xipki.security.common.IoCertUtil;

/**
 * @author Lijun Liao
 */

@Command(scope = "ca", name = "crlsigner-update", description="Update CRL signer")
@Service
public class CrlSignerUpdateCommand extends CaCommand
{
    @Option(name = "-name",
            description = "Required. CRL signer name",
            required = true, multiValued = false)
    @Completion(CrlSignerNameCompleter.class)
    protected String name;

    @Option(name = "-signerType",
            description = "CRL signer type, use 'CA' to sign the CRL by the CA itself")
    @Completion(CrlSignerNameCompleter.class)
    protected String signerType;

    @Option(name = "-signerConf",
            description = "CRL signer configuration")
    protected String signerConf;

    @Option(name = "-cert",
            description = "CRL signer's certificate file or 'NULL'")
    @Completion(FilePathCompleter.class)
    protected String signerCert;

    @Option(name = "-crlControl",
            description = "CRL control")
    protected String crlControl;

    @Override
    protected Object doExecute()
    throws Exception
    {
        String signerCertConf = null;
        if(CAManager.NULL.equalsIgnoreCase(signerCert))
        {
            signerCertConf = CAManager.NULL;
        }
        else if(signerCert != null)
        {
            byte[] certBytes = IoCertUtil.read(signerCert);
            IoCertUtil.parseCert(new ByteArrayInputStream(certBytes));
            signerCertConf = Base64.toBase64String(certBytes);
        }

        caManager.changeCrlSigner(name, signerType, signerConf, signerCertConf, crlControl);
        out("updated CRL signer " + name);
        return null;
    }
}
