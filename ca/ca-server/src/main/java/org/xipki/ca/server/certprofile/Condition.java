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

package org.xipki.ca.server.certprofile;

import java.util.ArrayList;
import java.util.List;

import org.xipki.ca.server.certprofile.jaxb.ConditionType;
import org.xipki.ca.server.certprofile.jaxb.EnvParamType;
import org.xipki.ca.server.certprofile.jaxb.OperatorType;
import org.xipki.security.common.EnvironmentParameterResolver;

/**
 * @author Lijun Liao
 */

class Condition
{
    private static class EnvParamConditionEntry
    {
        private final String name;
        private final String value;

        EnvParamConditionEntry(String name, String value)
        {
            this.name = name;
            this.value = value;
        }
    }

    private final OperatorType operator;
    private final List<EnvParamConditionEntry> entries;

    Condition(ConditionType type)
    {
        operator = type.getOperator() == null ? OperatorType.AND : type.getOperator();
        List<EnvParamType> envParams = type.getEnvParam();
        entries = new ArrayList<>(envParams.size());

        for(EnvParamType envParam : envParams)
        {
            entries.add(new EnvParamConditionEntry(envParam.getName(), envParam.getValue()));
        }
    }

    boolean satisfy(EnvironmentParameterResolver pr)
    {
        if(pr == null)
        {
            return false;
        }

        if(operator == OperatorType.OR || operator == null)
        {
            for(EnvParamConditionEntry e : entries)
            {
                String value = pr.getParameterValue(e.name);
                if(value.equalsIgnoreCase(e.value))
                {
                    return true;
                }
            }
            return false;
        }
        else if(operator == OperatorType.AND)
        {
            for(EnvParamConditionEntry e : entries)
            {
                String value = pr.getParameterValue(e.name);
                if(value.equalsIgnoreCase(e.value) == false)
                {
                    return false;
                }
            }
            return true;
        }
        else
        {
            throw new RuntimeException("should not reach here");
        }
    }
}
