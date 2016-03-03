/*
 *
 * This file is part of the XiPKI project.
 * Copyright (c) 2013 - 2016 Lijun Liao
 * Author: Lijun Liao
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License (version 3
 * or later at your option) as published by the Free Software Foundation
 * with the addition of the following permission added to Section 15 as
 * permitted in Section 7(a):
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

package org.xipki.commons.password.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.xipki.commons.common.util.StringUtil;
import org.xipki.commons.password.api.PasswordCallback;
import org.xipki.commons.password.api.PasswordResolverException;

/**
 * @author Lijun Liao
 * @since 2.0.0
 */

public class FilePasswordCallback implements PasswordCallback {

    private String passwordFile;

    @Override
    public char[] getPassword(
            final String prompt)
    throws PasswordResolverException {
        if (passwordFile == null) {
            throw new PasswordResolverException("please initialize me first");
        }

        String passwordHint = null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(expandFilepath(passwordFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (StringUtil.isNotBlank(line) && !line.startsWith("#")) {
                    passwordHint = line;
                    break;
                }
            }
        } catch (IOException ex) {
            throw new PasswordResolverException("could not read file " + passwordFile, ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                }
            }
        }

        if (passwordHint == null) {
            throw new PasswordResolverException("no password is specified in file " + passwordFile);
        }

        if (StringUtil.startsWithIgnoreCase(passwordHint, OBFPasswordServiceImpl.OBFUSCATE)) {
            return OBFPasswordServiceImpl.doDeobfuscate(passwordHint).toCharArray();
        } else {
            return passwordHint.toCharArray();
        }
    } // method getPassword

    @Override
    public void init(
            final String conf)
    throws PasswordResolverException {
        if (StringUtil.isBlank(conf)) {
            throw new PasswordResolverException("conf must not be null or empty");
        }
        passwordFile = expandFilepath(conf);
    }

    private static String expandFilepath(
            final String path) {
        if (path.startsWith("~" + File.separator)) {
            return System.getProperty("user.home") + path.substring(1);
        } else {
            return path;
        }
    }

}
