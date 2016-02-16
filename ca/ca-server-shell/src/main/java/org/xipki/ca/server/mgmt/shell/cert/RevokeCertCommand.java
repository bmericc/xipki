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

package org.xipki.ca.server.mgmt.shell.cert;

import java.math.BigInteger;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.ca.server.mgmt.api.CAEntry;
import org.xipki.ca.server.mgmt.shell.CaCommand;
import org.xipki.ca.server.mgmt.shell.completer.CaNameCompleter;
import org.xipki.ca.server.mgmt.shell.completer.ClientCRLReasonCompleter;
import org.xipki.console.karaf.CmdFailure;
import org.xipki.console.karaf.IllegalCmdParamException;
import org.xipki.security.common.CRLReason;

/**
 * @author Lijun Liao
 */

@Command(scope = "ca", name = "revoke-cert", description="Revoke certificate")
@Service
public class RevokeCertCommand extends CaCommand
{
    @Option(name = "-ca",
            required = true, description = "Required. CA name")
    @Completion(CaNameCompleter.class)
    protected String caName;

    @Option(name = "-serial",
            required = true,
            description = "Serial number")
    protected Long serialNumber;

    @Option(name = "-reason",
            required = true,
            description = "Required. Reason, valid values are \n" +
                    "0: unspecified\n" +
                    "1: keyCompromise\n" +
                    "3: affiliationChanged\n" +
                    "4: superseded\n" +
                    "5: cessationOfOperation\n" +
                    "6: certificateHold\n" +
                    "9: privilegeWithdrawn")
    @Completion(ClientCRLReasonCompleter.class)
    protected String reason;

    @Override
    protected Object doExecute()
    throws Exception
    {
        CAEntry ca = caManager.getCA(caName);
        if(ca == null)
        {
            throw new IllegalCmdParamException("CA " + caName + " not available");
        }

        CRLReason crlReason = CRLReason.getInstance(reason);
        if(crlReason == null)
        {
            throw new IllegalCmdParamException("invalid reason " + reason);
        }

        if(CRLReason.PERMITTED_CLIENT_CRLREASONS.contains(crlReason) == false)
        {
            throw new IllegalCmdParamException("reason " + reason + " is not permitted");
        }

        boolean successful = caManager.revokeCertificate(caName, BigInteger.valueOf(serialNumber), crlReason, null);

        if(successful)
        {
            out("Revoked certificate");
        }
        else
        {
            throw new CmdFailure("Could not revoke certificate");
        }

        return null;
    }

}
