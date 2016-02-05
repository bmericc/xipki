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

package org.xipki.commons.security.p11.keystore;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.commons.common.util.IoUtil;
import org.xipki.commons.common.util.ParamUtil;
import org.xipki.commons.password.api.PasswordResolverException;
import org.xipki.commons.security.api.SignerException;
import org.xipki.commons.security.api.p11.P11Module;
import org.xipki.commons.security.api.p11.P11ModuleConf;
import org.xipki.commons.security.api.p11.P11SlotIdentifier;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class KeystoreP11Module implements P11Module {

    private static final Logger LOG = LoggerFactory.getLogger(KeystoreP11Module.class);

    private final P11ModuleConf moduleConf;

    private Map<P11SlotIdentifier, KeystoreP11Slot> slots = new HashMap<>();

    private List<P11SlotIdentifier> slotIds;

    public KeystoreP11Module(
            final P11ModuleConf moduleConf) {
        ParamUtil.assertNotNull("moduleConf", moduleConf);

        this.moduleConf = moduleConf;

        final String nativeLib = moduleConf.getNativeLibrary();

        File baseDir = new File(IoUtil.expandFilepath(nativeLib));
        File[] children = baseDir.listFiles();

        if (children == null || children.length == 0) {
            LOG.error("found no slots");
            this.slotIds = Collections.emptyList();
            return;
        }

        Set<Integer> allSlotIndexes = new HashSet<>();
        Set<Long> allSlotIdentifiers = new HashSet<>();

        List<P11SlotIdentifier> allSlotIds = new LinkedList<>();

        for (File child : children) {
            if ((child.isDirectory() && child.canRead() && !child.exists())) {
                LOG.warn("ignore path {}, it does not point to a readable exist directory",
                        child.getPath());
                continue;
            }

            String filename = child.getName();
            String[] tokens = filename.split("-");
            if (tokens == null || tokens.length != 2) {
                LOG.warn("ignore dir {}, invalid filename syntax", child.getPath());
                continue;
            }

            int slotIndex;
            long slotId;
            try {
                slotIndex = Integer.parseInt(tokens[0]);
                slotId = Long.parseLong(tokens[1]);
            } catch (NumberFormatException e) {
                LOG.warn("ignore dir {}, invalid filename syntax", child.getPath());
                continue;
            }

            if (allSlotIndexes.contains(slotIndex)) {
                LOG.error("ignore slot dir, the same slot index has been assigned", filename);
                continue;
            }

            if (allSlotIdentifiers.contains(slotId)) {
                LOG.error("ignore slot dir, the same slot identifier has been assigned", filename);
                continue;
            }

            allSlotIndexes.add(slotIndex);
            allSlotIdentifiers.add(slotId);

            allSlotIds.add(new P11SlotIdentifier(slotIndex, slotId));
        } // end for

        List<P11SlotIdentifier> tmpSlotIds = new LinkedList<>();
        for (P11SlotIdentifier slotId : allSlotIds) {
            if (moduleConf.isSlotIncluded(slotId)) {
                tmpSlotIds.add(slotId);
            }
        }

        this.slotIds = Collections.unmodifiableList(tmpSlotIds);
    } // constructor

    @Override
    public KeystoreP11Slot getSlot(
            final P11SlotIdentifier slotId)
    throws SignerException {
        KeystoreP11Slot extSlot = slots.get(slotId);
        if (extSlot != null) {
            return extSlot;
        }

        P11SlotIdentifier localSlotId = null;
        for (P11SlotIdentifier s : slotIds) {
            if (s.getSlotIndex() == slotId.getSlotIndex() || s.getSlotId() == slotId.getSlotId()) {
                localSlotId = s;
                break;
            }
        }

        if (localSlotId == null) {
            throw new SignerException("could not find slot identified by " + slotId);
        }

        List<char[]> pwd;
        try {
            pwd = moduleConf.getPasswordRetriever().getPassword(localSlotId);
        } catch (PasswordResolverException e) {
            throw new SignerException("PasswordResolverException: " + e.getMessage(), e);
        }

        File slotDir = new File(moduleConf.getNativeLibrary(), localSlotId.getSlotIndex() + "-"
                + localSlotId.getSlotId());

        extSlot = new KeystoreP11Slot(slotDir, localSlotId, pwd, moduleConf.getSecurityFactory());

        slots.put(localSlotId, extSlot);
        return extSlot;
    } // method getSlot

    public void destroySlot(
            final long slotId) {
        slots.remove(slotId);
    }

    public void close() {
        slots.clear();
        LOG.info("close", "close pkcs11 module: {}", moduleConf.getName());
    }

    @Override
    public List<P11SlotIdentifier> getSlotIdentifiers() {
        return slotIds;
    }

}
