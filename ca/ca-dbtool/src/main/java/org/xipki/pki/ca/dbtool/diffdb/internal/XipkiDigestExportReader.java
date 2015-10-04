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

package org.xipki.pki.ca.dbtool.diffdb.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.datasource.api.DataSourceWrapper;
import org.xipki.datasource.api.exception.DataAccessException;
import org.xipki.pki.ca.dbtool.DbToolBase;
import org.xipki.pki.ca.dbtool.IDRange;

/**
 * @author Lijun Liao
 */

public class XipkiDigestExportReader {
    private static final Logger LOG = LoggerFactory.getLogger(XipkiDigestExportReader.class);

    private class Retriever
    implements Runnable {
        private Connection conn;
        private PreparedStatement selectCertStmt;

        public Retriever()
        throws DataAccessException {
            this.conn = datasource.getConnection();
            try {
                selectCertStmt = datasource.prepareStatement(conn, selectCertSql);
            } catch (DataAccessException e) {
                datasource.returnConnection(conn);
                throw e;
            }
        }

        @Override
        public void run() {
            while (!stop.get()) {
                try {
                    IDRange idRange = inQueue.take();
                    query(idRange);
                } catch (InterruptedException e) {
                    LOG.error("InterruptedException", e);
                }
            }

            DbToolBase.releaseResources(selectCertStmt, null);
            datasource.returnConnection(conn);
            selectCertStmt = null;
        }

        private void query(
                final IDRange idRange) {
            DigestDBEntrySet result = new DigestDBEntrySet(idRange.getFrom());

            ResultSet rs = null;
            try {
                selectCertStmt.setInt(1, idRange.getFrom());
                selectCertStmt.setInt(2, idRange.getTo() + 1);

                rs = selectCertStmt.executeQuery();

                while (rs.next()) {
                    int caId = rs.getInt(dbControl.getColCaId());
                    int id = rs.getInt("ID");
                    String hash = rs.getString(dbControl.getColCerthash());
                    long serial = rs.getLong(dbControl.getColSerialNumber());
                    boolean revoked = rs.getBoolean(dbControl.getColRevoked());

                    Integer revReason = null;
                    Long revTime = null;
                    Long revInvTime = null;

                    if (revoked) {
                        revReason = rs.getInt(dbControl.getColRevReason());
                        revTime = rs.getLong(dbControl.getColRevTime());
                        revInvTime = rs.getLong(dbControl.getColRevInvTime());
                        if (revInvTime == 0) {
                            revInvTime = null;
                        }
                    }

                    DbDigestEntry cert = new DbDigestEntry(serial, revoked, revReason, revTime,
                            revInvTime, hash);
                    IdentifiedDbDigestEntry idCert = new IdentifiedDbDigestEntry(cert, id);
                    idCert.setCaId(caId);

                    result.addEntry(idCert);
                }
            } catch (Exception e) {
                if (e instanceof SQLException) {
                    e = datasource.translate(selectCertSql, (SQLException) e);
                }
                result.setException(e);
            }
            finally {
                outQueue.add(result);
                DbToolBase.releaseResources(null, rs);
            }
        }
    }

    protected final AtomicBoolean stop = new AtomicBoolean(false);
    protected final BlockingDeque<IDRange> inQueue = new LinkedBlockingDeque<>();
    protected final BlockingDeque<DigestDBEntrySet> outQueue = new LinkedBlockingDeque<>();
    private final int numThreads;
    private ExecutorService executor;
    private final List<Retriever> retrievers;
    private final DataSourceWrapper datasource;
    private final XipkiDbControl dbControl;

    private final String selectCertSql;

    public XipkiDigestExportReader(
            final DataSourceWrapper datasource,
            final XipkiDbControl dbControl,
            final int numThreads)
    throws Exception {
        this.datasource = datasource;
        this.numThreads = numThreads;
        this.dbControl = dbControl;
        this.selectCertSql = dbControl.getCertSql();

        retrievers = new ArrayList<>(numThreads);

        for (int i = 0; i < numThreads; i++) {
            Retriever retriever = new Retriever();
            retrievers.add(retriever);
        }

        executor = Executors.newFixedThreadPool(numThreads);
        for (Runnable runnable : retrievers) {
            executor.execute(runnable);
        }
    }

    public List<IdentifiedDbDigestEntry> readCerts(List<IDRange> idRanges)
    throws DataAccessException {
        int n = idRanges.size();
        for (IDRange range : idRanges) {
            inQueue.add(range);
        }

        List<DigestDBEntrySet> results = new ArrayList<>(n);
        int numCerts = 0;
        for (int i = 0; i < n; i++) {
            try {
                DigestDBEntrySet result = outQueue.take();
                numCerts += result.getEntries().size();
                results.add(result);
            } catch (InterruptedException e) {
                throw new DataAccessException("InterruptedException " + e.getMessage(), e);
            }
        }

        Collections.sort(results);
        List<IdentifiedDbDigestEntry> ret = new ArrayList<>(numCerts);

        for (DigestDBEntrySet result : results) {
            if (result.getException() != null) {
                throw new DataAccessException(
                        "error while reading from ID " + result.getStartId()
                            + ": " + result.getException().getMessage(),
                        result.getException());
            }

            ret.addAll(result.getEntries());
        }

        return ret;
    }

    public int getNumThreads() {
        return numThreads;
    }

    public void stop() {
        stop.set(true);
        executor.shutdownNow();
    }
}
