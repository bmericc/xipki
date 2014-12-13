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

package org.xipki.ca.client.shell;

import java.util.Set;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.xipki.ca.client.api.RemoveExpiredCertsResult;

/**
 * @author Lijun Liao
 */

@Command(scope = "caclient", name = "remove-expired-certs", description="Remove expired certificates")
public class RemoveExpiredCertsCommand extends ClientCommand
{
    @Option(name = "-ca",
            required = false, description = "Required if multiple CAs are configured. CA name")
    protected String caName;

    @Option(name = "-profile",
            required = true, description = "Required. Certificate profile.")
    protected String profile;

    @Option(name = "-user",
            required = false, description = "Username, wildcards '%' and '*' are allowed.\n"
                    + "'all' for all users")
    protected String userLike;

    @Option(name = "-overlap",
            required = false, description = "Overlap in seconds")
    protected Long overlapSeconds = 24L * 60 * 60;

    @Override
    protected Object doExecute()
    throws Exception
    {
        Set<String> caNames = raWorker.getCaNames();
        if(caNames.isEmpty())
        {
            err("No CA is configured");
            return  null;
        }

        if(caName != null && ! caNames.contains(caName))
        {
            err("CA " + caName + " is not within the configured CAs " + caNames);
            return null;
        }

        if(caName == null)
        {
            if(caNames.size() == 1)
            {
                caName = caNames.iterator().next();
            }
            else
            {
                err("No caname is specified, one of " + caNames + " is required");
                return null;
            }
        }

        RemoveExpiredCertsResult result = raWorker.removeExpiredCerts(caName, profile, userLike, overlapSeconds);
        int n = result.getNumOfCerts();

        String prefix;
        if(n == 0)
        {
            prefix = "No certificate";
        }
        else if(n == 1)
        {
            prefix = "One certificate";
        }
        else
        {
            prefix = n + " certificates";
        }

        System.out.println(prefix + " will be deleted according to the given criteria");
        return null;
    }

}
