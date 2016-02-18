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

package org.xipki.ca.server.mgmt.shell;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Completion;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.xipki.ca.server.mgmt.shell.completer.ProfileNameCompleter;
import org.xipki.console.karaf.FilePathCompleter;
import org.xipki.security.common.IoCertUtil;

/**
 * @author Lijun Liao
 */

@Command(scope = "ca", name = "profile-update", description="Update certificate profile")
@Service
public class ProfileUpdateCommand extends CaCommand
{

    @Option(name = "-name",
                description = "Required. Profile name",
                required = true, multiValued = false)
    @Completion(ProfileNameCompleter.class)
    protected String name;

    @Option(name = "-type",
            description = "Profile type")
    protected String type;

    @Option(name = "-conf",
            description = "Profile configuration or 'NULL'")
    protected String conf;

    @Option(name = "-confFile",
            description = "Profile configuration file")
    @Completion(FilePathCompleter.class)
    protected String confFile;

    @Override
    protected Object doExecute()
    throws Exception
    {
        if(type == null && conf == null && confFile == null)
        {
            System.out.println("Nothing to update");
            return null;
        }

        if(conf == null && confFile != null)
        {
            conf = new String(IoCertUtil.read(confFile));
        }

        caManager.changeCertProfile(name, type, conf);
        out("updated certificate profile " + name);
        return null;
    }
}
