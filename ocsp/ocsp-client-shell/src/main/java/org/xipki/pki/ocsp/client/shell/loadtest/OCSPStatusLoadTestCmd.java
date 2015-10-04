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

package org.xipki.pki.ocsp.client.shell.loadtest;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.xipki.console.karaf.IllegalCmdParamException;
import org.xipki.pki.ocsp.client.api.RequestOptions;
import org.xipki.pki.ocsp.client.shell.AbstractOCSPStatusCmd;
import org.xipki.security.api.util.X509Util;

/**
 * @author Lijun Liao
 */

@Command(scope = "xipki-ocsp", name = "loadtest-status",
        description = "OCSP Load test")
public class OCSPStatusLoadTestCmd extends AbstractOCSPStatusCmd {
    @Option(name = "--serial",
            required = true,
            description = "serial numbers, comma-separated serial numbers or ranges\n"
                    + "required")
    private String serialNumbers;

    @Option(name = "--duration",
            description = "duration in seconds")
    private int durationInSecond = 30;

    @Option(name = "--thread",
            description = "number of threads")
    private Integer numThreads = 5;

    @Option(name = "--url",
            required = true,
            description = "OCSP responder URL\n"
                    + "required")
    private String serverURL;

    @Override
    protected Object _doExecute()
    throws Exception {
        List<Long> serialNumbers = new LinkedList<>();

        try {
            List<String> tokens = split(this.serialNumbers, ",");
            for (String token : tokens) {
                List<String> subtokens = split(token.trim(), "- ");
                int countTokens = subtokens.size();
                if (countTokens == 1) {
                    serialNumbers.add(Long.parseLong(subtokens.get(0)));
                } else if (countTokens == 2) {
                    int startSerial = Integer.parseInt(subtokens.get(0).trim());
                    int endSerial = Integer.parseInt(subtokens.get(1).trim());
                    if (startSerial < 1 || endSerial < 1 || startSerial > endSerial) {
                        throw new IllegalCmdParamException(
                                "invalid serial number " + this.serialNumbers);
                    }
                    for (long i = startSerial; i <= endSerial; i++) {
                        serialNumbers.add(i);
                    }
                } else {
                    throw new IllegalCmdParamException(
                            "invalid serial number " + this.serialNumbers);
                }
            }
        } catch (Exception e) {
            throw new IllegalCmdParamException("invalid serial numbers " + this.serialNumbers);
        }

        if (numThreads < 1) {
            throw new IllegalCmdParamException("invalid number of threads " + numThreads);
        }

        URL serverUrl;
        try {
            serverUrl = new URL(serverURL);
        } catch (MalformedURLException e) {
            throw new RuntimeException("invalid URL: " + serverURL);
        }

        StringBuilder description = new StringBuilder();
        description.append("serial numbers: ").append(this.serialNumbers).append("\n");
        description.append("issuer cert: ").append(issuerCertFile).append("\n");
        description.append("server URL: ").append(serverUrl.toString()).append("\n");
        description.append("hash: ").append(hashAlgo);

        X509Certificate issuerCert = X509Util.parseCert(issuerCertFile);

        RequestOptions options = getRequestOptions();

        OcspLoadTest loadTest = new OcspLoadTest(requestor, serialNumbers,
                issuerCert, serverUrl, options, description.toString());
        loadTest.setDuration(durationInSecond);
        loadTest.setThreads(numThreads);
        loadTest.test();

        return null;
    }

}
