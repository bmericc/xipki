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

package org.xipki.ca.server.publisher;

import java.util.ArrayList;
import java.util.List;

import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

class IssuerStore
{
    private final List<IssuerEntry> entries;

    IssuerStore( List<IssuerEntry> entries)
    {
        this.entries = new ArrayList<>(entries.size());

        for(IssuerEntry entry : entries)
        {
            addIdentityEntry(entry);
        }
    }

    void addIdentityEntry(IssuerEntry entry)
    {
        ParamChecker.assertNotNull("entry", entry);

        for(IssuerEntry existingEntry : entries)
        {
            if(existingEntry.getId() == entry.getId())
            {
                throw new IllegalArgumentException("issuer with the same id " + entry.getId() + " already available");
            }
        }

        entries.add(entry);
    }

    Integer getIdForSubject(String subject)
    {
        for(IssuerEntry entry : entries)
        {
            if(entry.getSubject().equals(subject))
            {
                return entry.getId();
            }
        }

        return null;
    }

    Integer getIdForSha1Fp(byte[] sha1Fp_cert)
    {
        for(IssuerEntry entry : entries)
        {
            if(entry.matchSha1Fp(sha1Fp_cert))
            {
                return entry.getId();
            }
        }

        return null;
    }

    Integer getIdForCert(byte[] encodedCert)
    {
        for(IssuerEntry entry : entries)
        {
            if(entry.matchCert(encodedCert))
            {
                return entry.getId();
            }
        }

        return null;
    }
}
