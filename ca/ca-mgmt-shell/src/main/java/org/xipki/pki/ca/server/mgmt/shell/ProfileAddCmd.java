/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2014 - 2015 Lijun Liao
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

package org.xipki.pki.ca.server.mgmt.shell;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.xipki.pki.ca.server.mgmt.api.CertprofileEntry;
import org.xipki.common.util.IoUtil;

/**
 * @author Lijun Liao
 */

@Command(scope = "xipki-ca", name = "profile-add",
        description = "add certificate profile")
public class ProfileAddCmd extends CaCmd {

    @Option(name = "--name", aliases = "-n",
            required = true,
            description = "profile name\n"
                    + "(required)")
    private String name;

    @Option(name = "--type",
            required = true,
            description = "profile type\n"
                    + "(required)")
    private String type;

    @Option(name = "--conf",
            description = "profile configuration")
    private String conf;

    @Option(name = "--conf-file",
            description = "profile configuration file")
    private String confFile;

    @Override
    protected Object _doExecute()
    throws Exception {
        if (conf == null && confFile != null) {
            conf = new String(IoUtil.read(confFile));
        }

        CertprofileEntry entry = new CertprofileEntry(name, type, conf);
        boolean b = caManager.addCertprofile(entry);
        output(b, "added", "could not add", "certificate profile " + name);
        return null;
    }
}
