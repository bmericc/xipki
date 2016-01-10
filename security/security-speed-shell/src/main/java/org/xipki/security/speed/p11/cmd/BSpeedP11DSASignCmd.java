/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2014 - 2016 Lijun Liao
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

package org.xipki.security.speed.p11.cmd;

import java.util.LinkedList;
import java.util.List;

import org.apache.karaf.shell.api.action.Command;
import org.xipki.common.LoadExecutor;
import org.xipki.security.api.p11.P11WritableSlot;
import org.xipki.security.speed.p11.P11DSASignLoadTest;

/**
 * @author Lijun Liao
 */

@Command(scope = "xipki-tk", name = "bspeed-dsa-sign",
        description = "performance test of PKCS#11 DSA signature creation (batch)")
public class BSpeedP11DSASignCmd extends BSpeedP11SignCommandSupport {

    @Override
    protected List<LoadExecutor> getTesters()
    throws Exception {
        List<LoadExecutor> ret = new LinkedList<>();
        int[] pqLens = new int[]{1024, 160, 2048, 224, 2048, 256, 3072, 256};

        P11WritableSlot slot = getP11WritablSlot(moduleName, slotIndex);

        for (int i = 0; i < pqLens.length; i += 2) {
            int pLen = pqLens[i];
            int qLen = pqLens[i + 1];
            if (pLen == 1024) {
                sigAlgo = "SHA1withDSA";
            }

            ret.add(
                    new P11DSASignLoadTest(securityFactory, slot, sigAlgo, pLen, qLen));
        }
        return ret;
    }

}
