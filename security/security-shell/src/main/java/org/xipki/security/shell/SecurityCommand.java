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

package org.xipki.security.shell;

import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.xipki.console.karaf.XipkiOsgiCommandSupport;
import org.xipki.security.api.SecurityFactory;
import org.xipki.security.api.SignerException;
import org.xipki.security.api.p11.P11CryptService;
import org.xipki.security.p11.iaik.IaikExtendedModule;
import org.xipki.security.p11.iaik.IaikP11CryptServiceFactory;
import org.xipki.security.p11.iaik.IaikP11ModulePool;

/**
 * @author Lijun Liao
 */

public abstract class SecurityCommand extends XipkiOsgiCommandSupport
{
    @Reference
    protected SecurityFactory securityFactory;

    protected IaikExtendedModule getModule(String moduleName)
    throws SignerException
    {
        // this call initialize the IaikExtendedModule
        P11CryptService p11CryptService = securityFactory.getP11CryptService(moduleName);
        if(p11CryptService == null)
        {
            throw new SignerException("Could not initialize P11CryptService " + moduleName);
        }

        // the returned object could not be null
        IaikExtendedModule module = IaikP11ModulePool.getInstance().getModule(moduleName);
        if(module == null)
        {
            throw new SignerException("P11KeypairGenerator only works with P11CryptServiceFactory " +
                    IaikP11CryptServiceFactory.class.getName());
        }
        return module;
    }

}
