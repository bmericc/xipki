/*
 *
 * Copyright (c) 2013 - 2016 Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 *
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

package org.xipki.commons.audit.api;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class AuditChildEvent implements AuditEventInterface {

    /**
     * The data array belonging to the event.
     */
    private final List<AuditEventData> eventDatas = new LinkedList<>();

    /**
     * The AuditLevel this Event belongs to.
     */
    private AuditLevel level;

    private AuditStatus status;

    public AuditChildEvent() {
        this.level = AuditLevel.INFO;
    }

    public AuditLevel getLevel() {
        return level;
    }

    @Override
    public void setLevel(final AuditLevel level) {
        this.level = Objects.requireNonNull(level, "level must not be null");
    }

    @Override
    public List<AuditEventData> getEventDatas() {
        return Collections.unmodifiableList(eventDatas);
    }

    @Override
    public boolean removeEventData(final String eventDataName) {
        Objects.requireNonNull(eventDataName, "eventDataName must not be null");

        AuditEventData tbr = null;
        for (AuditEventData ed : eventDatas) {
            if (ed.getName().equals(eventDataName)) {
                tbr = ed;
            }
        }

        if (tbr != null) {
            eventDatas.remove(tbr);
            return true;
        }

        return false;
    }

    @Override
    public AuditEventData addEventType(String type) {
        return addEventData("eventType", type);
    }

    @Override
    public AuditEventData addEventData(String name, String value) {
        return addEventData(new AuditEventData(name, value));
    }

    @Override
    public AuditEventData addEventData(final AuditEventData eventData) {
        Objects.requireNonNull(eventData, "eventData must not be null");

        int idx = -1;
        for (int i = 0; i < eventDatas.size(); i++) {
            AuditEventData ed = eventDatas.get(i);
            if (ed.getName().equals(eventData.getName())) {
                idx = i;
                break;
            }
        }

        AuditEventData ret = null;
        if (idx != -1) {
            ret = eventDatas.get(idx);
        }
        eventDatas.add(eventData);
        return ret;
    }

    @Override
    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(final AuditStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

}
