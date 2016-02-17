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

package org.xipki.dbi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xipki.database.api.DataSourceWrapper;
import org.xipki.dbi.ca.jaxb.CainfoType;
import org.xipki.dbi.ca.jaxb.CertStoreType;
import org.xipki.dbi.ca.jaxb.CertStoreType.Cainfos;
import org.xipki.dbi.ca.jaxb.CertStoreType.Certprofileinfos;
import org.xipki.dbi.ca.jaxb.CertStoreType.CertsFiles;
import org.xipki.dbi.ca.jaxb.CertStoreType.Crls;
import org.xipki.dbi.ca.jaxb.CertStoreType.DeltaCRLCache;
import org.xipki.dbi.ca.jaxb.CertStoreType.PublishQueue;
import org.xipki.dbi.ca.jaxb.CertStoreType.Publisherinfos;
import org.xipki.dbi.ca.jaxb.CertStoreType.Requestorinfos;
import org.xipki.dbi.ca.jaxb.CertStoreType.UsersFiles;
import org.xipki.dbi.ca.jaxb.CertType;
import org.xipki.dbi.ca.jaxb.CertsType;
import org.xipki.dbi.ca.jaxb.CrlType;
import org.xipki.dbi.ca.jaxb.DeltaCRLCacheEntryType;
import org.xipki.dbi.ca.jaxb.NameIdType;
import org.xipki.dbi.ca.jaxb.ObjectFactory;
import org.xipki.dbi.ca.jaxb.ToPublishType;
import org.xipki.dbi.ca.jaxb.UserType;
import org.xipki.dbi.ca.jaxb.UsersType;
import org.xipki.security.common.IoCertUtil;
import org.xipki.security.common.ParamChecker;

/**
 * @author Lijun Liao
 */

class CaCertStoreDbExporter extends DbPorter
{
    private static final Logger LOG = LoggerFactory.getLogger(CaCertStoreDbExporter.class);
    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;
    private final SHA1Digest sha1md = new SHA1Digest();
    private final ObjectFactory objFact = new ObjectFactory();

    private final int numCertsInBundle;
    private final int numCrls;
    private final boolean resume;

    CaCertStoreDbExporter(DataSourceWrapper dataSource,
            Marshaller marshaller, Unmarshaller unmarshaller, String baseDir,
            int numCertsInBundle, int numCrls, boolean resume)
    throws Exception
    {
        super(dataSource, baseDir);
        ParamChecker.assertNotNull("marshaller", marshaller);
        ParamChecker.assertNotNull("unmarshaller", unmarshaller);
        if(numCertsInBundle < 1)
        {
            numCertsInBundle = 1;
        }
        this.numCertsInBundle = numCertsInBundle;

        if(numCrls < 1)
        {
            numCrls = 1;
        }
        this.numCrls = numCrls;

        this.marshaller = marshaller;
        this.unmarshaller = unmarshaller;
        this.resume = resume;
    }

    public void export()
    throws Exception
    {
        CertStoreType certstore;
        if(resume)
        {
            @SuppressWarnings("unchecked")
            JAXBElement<CertStoreType> root = (JAXBElement<CertStoreType>)
                    unmarshaller.unmarshal(new File(baseDir, FILENAME_CA_CertStore));
            certstore = root.getValue();
            if(certstore.getVersion() > VERSION)
            {
                throw new Exception("Cannot continue with CertStore greater than " + VERSION + ": " + certstore.getVersion());
            }
        }
        else
        {
            certstore = new CertStoreType();
            certstore.setVersion(VERSION);
        }

        Exception exception = null;
        System.out.println("Exporting CA certstore from database");
        try
        {
            if(resume == false)
            {
                export_cainfo(certstore);
                export_requestorinfo(certstore);
                export_publisherinfo(certstore);
                export_certprofileinfo(certstore);
                export_user(certstore);
                export_crl(certstore);
                export_publishQueue(certstore);
                export_deltaCRLCache(certstore);
            }
            File processLogFile = new File(baseDir, DbPorter.EXPORT_PROCESS_LOG_FILENAME);
            exception = export_cert(certstore, processLogFile);

            JAXBElement<CertStoreType> root = new ObjectFactory().createCertStore(certstore);
            marshaller.marshal(root, new File(baseDir + File.separator + FILENAME_CA_CertStore));
        }catch(Exception e)
        {
            System.err.println("Error while exporting CA certstore from database");
            exception = e;
        }

        if(exception == null)
        {
            System.out.println(" Exported CA certstore from database");
        }
        else
        {
            throw exception;
        }
    }

    private void export_crl(CertStoreType certstore)
    throws Exception
    {
        System.out.println("Exporting table CRL");
        Crls crls = new Crls();
        Statement stmt = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT ID, CAINFO_ID FROM CRL";
            ResultSet rs = stmt.executeQuery(sql);

            Map<Integer, List<Integer>> idMap = new HashMap<>();

            while(rs.next())
            {
                int id = rs.getInt("ID");
                int cainfo_id = rs.getInt("CAINFO_ID");
                List<Integer> ids = idMap.get(cainfo_id);
                if(ids == null)
                {
                    ids = new LinkedList<>();
                    idMap.put(cainfo_id, ids);
                }
                ids.add(id);
            }
            rs.close();

            Set<Integer> cainfo_ids = idMap.keySet();
            for(Integer cainfo_id : cainfo_ids)
            {
                List<Integer> ids = idMap.get(cainfo_id);
                if(ids.isEmpty())
                {
                    continue;
                }

                Collections.sort(ids);
                int startIndex = Math.max(0, ids.size() - numCrls);
                for(int i = startIndex; i < ids.size(); i++)
                {
                    int id = ids.get(i);
                    rs = stmt.executeQuery("SELECT CRL FROM CRL WHERE ID=" + id);
                    if(rs.next() == false)
                    {
                        continue;
                    }

                    String b64Crl = rs.getString("CRL");
                    rs.close();
                    byte[] encodedCrl = Base64.decode(b64Crl);

                    String fp = fp(encodedCrl);
                    File f = new File(baseDir, "CRL" + File.separator + fp + ".crl");
                    IoCertUtil.save(f, encodedCrl);

                    CrlType crl = new CrlType();

                    crl.setId(id);
                    crl.setCainfoId(cainfo_id);
                    crl.setCrlFile("CRL/" + fp + ".crl");

                    crls.getCrl().add(crl);
                }
            }

            rs.close();
            rs = null;
        }finally
        {
            releaseResources(stmt, null);
        }

        certstore.setCrls(crls);
        System.out.println(" Exported table CRL");
    }

    private void export_cainfo(CertStoreType certstore)
    throws SQLException
    {
        System.out.println("Exporting table CAINFO");
        Cainfos cainfos = new Cainfos();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT ID, CERT FROM CAINFO";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                int id = rs.getInt("ID");
                String cert = rs.getString("CERT");

                CainfoType cainfo = new CainfoType();
                cainfo.setId(id);
                cainfo.setCert(cert);

                cainfos.getCainfo().add(cainfo);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        certstore.setCainfos(cainfos);
        System.out.println(" Exported table CAINFO");
    }

    private void export_requestorinfo(CertStoreType certstore)
    throws SQLException
    {
        System.out.println("Exporting table REQUESTORINFO");
        Requestorinfos infos = new Requestorinfos();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT ID, NAME FROM REQUESTORINFO";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                int id = rs.getInt("ID");
                String name = rs.getString("NAME");

                NameIdType info = createNameId(name, id);
                infos.getRequestorinfo().add(info);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        certstore.setRequestorinfos(infos);
        System.out.println(" Exported table REQUESTORINFO");
    }

    private void export_publisherinfo(CertStoreType certstore)
    throws SQLException
    {
        System.out.println("Exporting table PUBLISHERINFO");
        Publisherinfos infos = new Publisherinfos();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT ID, NAME FROM PUBLISHERINFO";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                int id = rs.getInt("ID");
                String name = rs.getString("NAME");

                NameIdType info = createNameId(name, id);
                infos.getPublisherinfo().add(info);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        certstore.setPublisherinfos(infos);
        System.out.println(" Exported table PUBLISHERINFO");
    }

    private void export_user(CertStoreType certstore)
    throws SQLException, JAXBException
    {
        System.out.println("Exporting table USERNAME");
        UsersFiles usersFiles = new UsersFiles();

        String tableName = "USERNAME";

        final int minId = (int) getMin(tableName, "ID");
        final int maxId = (int) getMax(tableName, "ID");

        String sql = "SELECT ID, NAME FROM " + tableName +
                " WHERE ID >= ? AND ID < ?" +
                " ORDER BY ID ASC";

        PreparedStatement ps = prepareStatement(sql);

        int numUsersInCurrentFile = 0;
        UsersType usersInCurrentFile = new UsersType();

        int sum = 0;
        final int n = 100;

        int minIdOfCurrentFile = -1;
        int maxIdOfCurrentFile = -1;

        try
        {
            for(int i = minId; i <= maxId; i += n)
            {
                ps.setInt(1, i);
                ps.setInt(2, i + n);

                ResultSet rs = ps.executeQuery();

                while(rs.next())
                {
                    int id = rs.getInt("ID");

                    if(minIdOfCurrentFile == -1)
                    {
                        minIdOfCurrentFile = id;
                    }
                    else if(minIdOfCurrentFile > id)
                    {
                        minIdOfCurrentFile = id;
                    }

                    if(maxIdOfCurrentFile == -1)
                    {
                        maxIdOfCurrentFile = id;
                    }
                    else if(maxIdOfCurrentFile < id)
                    {
                        maxIdOfCurrentFile = id;
                    }

                    String name = rs.getString("NAME");

                    UserType user = new UserType();
                    user.setId(id);
                    user.setName(name);
                    usersInCurrentFile.getUser().add(user);

                    numUsersInCurrentFile ++;
                    sum ++;

                    if(numUsersInCurrentFile == numCertsInBundle * 10)
                    {
                        String currentCertsFilename = DbiUtil.buildFilename("users_", ".xml",
                                minIdOfCurrentFile, maxIdOfCurrentFile, maxId);

                        JAXBElement<UsersType> root = new ObjectFactory().createUsers(usersInCurrentFile);
                        marshaller.marshal(root, new File(baseDir + File.separator + currentCertsFilename));

                        usersFiles.getUsersFile().add(currentCertsFilename);

                        System.out.println(" Exported " + numUsersInCurrentFile + " users in " + currentCertsFilename);
                        System.out.println(" Exported " + sum + " users ...");

                        // reset
                        usersInCurrentFile = new UsersType();
                        numUsersInCurrentFile = 0;
                        minIdOfCurrentFile = -1;
                        maxIdOfCurrentFile = -1;
                    }
                }

                try
                {
                    rs.close();
                } catch(SQLException e)
                {
                }
            }

            if(numUsersInCurrentFile > 0)
            {
                String currentCertsFilename = DbiUtil.buildFilename("users_", ".xml",
                        minIdOfCurrentFile, maxIdOfCurrentFile, maxId);

                JAXBElement<UsersType> root = new ObjectFactory().createUsers(usersInCurrentFile);
                marshaller.marshal(root, new File(baseDir + File.separator + currentCertsFilename));

                usersFiles.getUsersFile().add(currentCertsFilename);

                System.out.println(" Exported " + numUsersInCurrentFile + " users in " + currentCertsFilename);
            }

        }finally
        {
            releaseResources(ps, null);
        }

        certstore.setUsersFiles(usersFiles);
        System.out.println(" Exported " + sum + " users from table USERNAME");
    }

    private void export_certprofileinfo(CertStoreType certstore)
    throws SQLException
    {
        System.out.println("Exporting table CERTPROFILEINFO");
        Certprofileinfos infos = new Certprofileinfos();

        Statement stmt = null;
        ResultSet rs = null;
        try
        {
            stmt = createStatement();
            String sql = "SELECT ID, NAME FROM CERTPROFILEINFO";
            rs = stmt.executeQuery(sql);

            while(rs.next())
            {
                int id = rs.getInt("ID");
                String name = rs.getString("NAME");

                NameIdType info = createNameId(name, id);
                infos.getCertprofileinfo().add(info);
            }
        }finally
        {
            releaseResources(stmt, rs);
        }

        certstore.setCertprofileinfos(infos);
        System.out.println(" Exported table CERTPROFILEINFO");
    }

    private Exception export_cert(CertStoreType certstore, File processLogFile)
    {
        try
        {
            do_export_cert(certstore, processLogFile);
            return null;
        }catch(Exception e)
        {
            // delete the temporary files
            deleteTmpFiles(baseDir, "tmp-certs-");
            System.err.println("\nExporting table CERT and RAWCERT has been cancelled due to error,\n"
                    + "please continue with the option '-resume'");
            LOG.error("Exception", e);
            return e;
        }
    }

    private void do_export_cert(CertStoreType certstore, File processLogFile)
    throws SQLException, IOException, JAXBException
    {
        CertsFiles certsFiles = certstore.getCertsFiles();
        int numProcessedBefore = 0;
        if(certsFiles == null)
        {
            certsFiles = new CertsFiles();
            certstore.setCertsFiles(certsFiles);
        }
        else
        {
            numProcessedBefore = (int) certsFiles.getCountCerts();
        }

        Integer minId = null;
        if(processLogFile.exists())
        {
            byte[] content = IoCertUtil.read(processLogFile);
            if(content != null && content.length > 0)
            {
                minId = Integer.parseInt(new String(content).trim());
                minId++;
            }
        }

        if(minId == null)
        {
            minId = (int) getMin("CERT", "ID");
        }

        System.out.println("Exporting tables CERT and RAWCERT from ID " + minId);

        final int maxId = (int) getMax("CERT", "ID");
        long total = getCount("CERT") - numProcessedBefore;
        if(total < 1)
        {
            total = 1; // to avoid exception
        }

        String certSql = "SELECT ID, CAINFO_ID, CERTPROFILEINFO_ID," +
                " REQUESTORINFO_ID, LAST_UPDATE, REVOKED," +
                " REV_REASON, REV_TIME, REV_INVALIDITY_TIME, USER_ID" +
                " FROM CERT" +
                " WHERE ID >= ? AND ID < ?" +
                " ORDER BY ID ASC";

        PreparedStatement ps = prepareStatement(certSql);

        String rawCertSql = "SELECT CERT_ID, CERT FROM RAWCERT WHERE CERT_ID >= ? AND CERT_ID < ?";
        PreparedStatement rawCertPs = prepareStatement(rawCertSql);

        int numCertsInCurrentFile = 0;
        CertsType certsInCurrentFile = new CertsType();

        long sum = 0;
        final int n = 100;

        File currentCertsZipFile = new File(baseDir, "tmp-certs-" + System.currentTimeMillis() + ".zip");
        FileOutputStream out = new FileOutputStream(currentCertsZipFile);
        ZipOutputStream currentCertsZip = new ZipOutputStream(out);

        int minIdOfCurrentFile = -1;
        int maxIdOfCurrentFile = -1;

        final long startTime = System.currentTimeMillis();
        printHeader();

        try
        {
            Integer id = null;

            for(int i = minId; i <= maxId; i += n)
            {
                Map<Integer, byte[]> rawCertMaps = new HashMap<>();

                // retrieve raw certificates
                rawCertPs.setInt(1, i);
                rawCertPs.setInt(2, i + n);
                ResultSet rawCertRs = rawCertPs.executeQuery();
                while(rawCertRs.next())
                {
                    int certId = rawCertRs.getInt("CERT_ID");
                    String b64Cert = rawCertRs.getString("CERT");
                    byte[] certBytes = Base64.decode(b64Cert);
                    rawCertMaps.put(certId, certBytes);
                }
                rawCertRs.close();

                ps.setInt(1, i);
                ps.setInt(2, i + n);

                ResultSet rs = ps.executeQuery();

                while(rs.next())
                {
                    id = rs.getInt("ID");

                    if(minIdOfCurrentFile == -1)
                    {
                        minIdOfCurrentFile = id;
                    }
                    else if(minIdOfCurrentFile > id)
                    {
                        minIdOfCurrentFile = id;
                    }

                    if(maxIdOfCurrentFile == -1)
                    {
                        maxIdOfCurrentFile = id;
                    }
                    else if(maxIdOfCurrentFile < id)
                    {
                        maxIdOfCurrentFile = id;
                    }

                    byte[] certBytes = rawCertMaps.remove(id);
                    if(certBytes == null)
                    {
                        String msg = "Found no certificate in table RAWCERT for cert_id '" + id + "'";
                        LOG.error(msg);
                        continue;
                    }

                    String sha1_fp_cert = IoCertUtil.sha1sum(certBytes);

                    ZipEntry certZipEntry = new ZipEntry(sha1_fp_cert + ".der");
                    currentCertsZip.putNextEntry(certZipEntry);
                    try
                    {
                        currentCertsZip.write(certBytes);
                    }finally
                    {
                        currentCertsZip.closeEntry();
                    }

                    CertType cert = new CertType();
                    cert.setId(id);

                    int cainfo_id = rs.getInt("CAINFO_ID");
                    cert.setCainfoId(cainfo_id);

                    int certprofileinfo_id = rs.getInt("CERTPROFILEINFO_ID");
                    cert.setCertprofileinfoId(certprofileinfo_id);

                    int requestorinfo_id = rs.getInt("REQUESTORINFO_ID");
                    if(requestorinfo_id != 0)
                    {
                        cert.setRequestorinfoId(requestorinfo_id);
                    }

                    long last_update = rs.getLong("LAST_UPDATE");
                    cert.setLastUpdate(last_update);

                    boolean revoked = rs.getBoolean("REVOKED");
                    cert.setRevoked(revoked);

                    if(revoked)
                    {
                        int rev_reason = rs.getInt("REV_REASON");
                        long rev_time = rs.getLong("REV_TIME");
                        long rev_invalidity_time = rs.getLong("REV_INVALIDITY_TIME");
                        cert.setRevReason(rev_reason);
                        cert.setRevTime(rev_time);
                        if(rev_invalidity_time != 0)
                        {
                            cert.setRevInvalidityTime(rev_invalidity_time);
                        }
                    }

                    int user_id = rs.getInt("USER_ID");
                    if(user_id != 0)
                    {
                        cert.setUserId(user_id);
                    }
                    cert.setCertFile(sha1_fp_cert + ".der");

                    certsInCurrentFile.getCert().add(cert);
                    numCertsInCurrentFile ++;
                    sum ++;

                    if(numCertsInCurrentFile == numCertsInBundle)
                    {
                        finalizeZip(currentCertsZip, certsInCurrentFile);

                        String currentCertsFilename = DbiUtil.buildFilename("certs_", ".zip",
                                minIdOfCurrentFile, maxIdOfCurrentFile, maxId);
                        currentCertsZipFile.renameTo(new File(baseDir, currentCertsFilename));

                        certsFiles.getCertsFile().add(currentCertsFilename);
                        certsFiles.setCountCerts(numProcessedBefore + sum);
                        echoToFile(Integer.toString(id), processLogFile);
                        printStatus(total, sum, startTime);

                        // reset
                        certsInCurrentFile = new CertsType();
                        numCertsInCurrentFile = 0;
                        minIdOfCurrentFile = -1;
                        maxIdOfCurrentFile = -1;
                        currentCertsZipFile = new File(baseDir, "tmp-certs-" + System.currentTimeMillis() + ".zip");
                        out = new FileOutputStream(currentCertsZipFile);
                        currentCertsZip = new ZipOutputStream(out);
                    }
                }

                rawCertMaps.clear();
                rawCertMaps = null;
            }

            if(numCertsInCurrentFile > 0)
            {
                finalizeZip(currentCertsZip, certsInCurrentFile);

                String currentCertsFilename = DbiUtil.buildFilename("certs_", ".zip",
                        minIdOfCurrentFile, maxIdOfCurrentFile, maxId);
                currentCertsZipFile.renameTo(new File(baseDir, currentCertsFilename));

                certsFiles.getCertsFile().add(currentCertsFilename);
                certsFiles.setCountCerts(numProcessedBefore + sum);
                if(id != null)
                {
                    echoToFile(Integer.toString(id), processLogFile);
                }
                printStatus(total, sum, startTime);
            }
            else
            {
                currentCertsZip.close();
                currentCertsZipFile.delete();
            }

        }finally
        {
            releaseResources(ps, null);
        }

        printTrailer();
        // all successful, delete the processLogFile
        processLogFile.delete();
        System.out.println(" Exported " + sum + " certificates from tables CERT and RAWCERT");
    }

    private void export_publishQueue(CertStoreType certstore)
    throws SQLException, IOException, JAXBException
    {
        System.out.println("Exporting table PUBLISHQUEUE");

        String sql = "SELECT CERT_ID, PUBLISHER_ID, CAINFO_ID" +
                " FROM PUBLISHQUEUE" +
                " WHERE CERT_ID >= ? AND CERT_ID < ?" +
                " ORDER BY CERT_ID ASC";

        final int minId = (int) getMin("PUBLISHQUEUE", "CERT_ID");
        final int maxId = (int) getMax("PUBLISHQUEUE", "CERT_ID");

        PublishQueue queue = new PublishQueue();
        certstore.setPublishQueue(queue);
        if(maxId != 0)
        {
            PreparedStatement ps = prepareStatement(sql);
            ResultSet rs = null;

            List<ToPublishType> list = queue.getTop();
            final int n = 500;

            try
            {
                for(int i = minId; i <= maxId; i += n)
                {
                    ps.setInt(1, i);
                    ps.setInt(2, i + n);

                    rs = ps.executeQuery();

                    while(rs.next())
                    {
                        int cert_id = rs.getInt("CERT_ID");
                        int pub_id = rs.getInt("PUBLISHER_ID");
                        int ca_id = rs.getInt("CAINFO_ID");

                        ToPublishType toPub = new ToPublishType();
                        toPub.setPubId(pub_id);
                        toPub.setCertId(cert_id);
                        toPub.setCaId(ca_id);
                        list.add(toPub);
                    }
                }
            }finally
            {
                releaseResources(ps, rs);
            }
        }

        System.out.println(" Exported table PUBLISHQUEUE");
    }

    private void export_deltaCRLCache(CertStoreType certstore)
    throws SQLException, IOException, JAXBException
    {
        System.out.println("Exporting table DELTACRL_CACHE");

        String sql = "SELECT SERIAL, CAINFO_ID FROM DELTACRL_CACHE";

        DeltaCRLCache deltaCache = new DeltaCRLCache();
        certstore.setDeltaCRLCache(deltaCache);

        PreparedStatement ps = prepareStatement(sql);
        ResultSet rs = null;

        List<DeltaCRLCacheEntryType> list = deltaCache.getEntry();

        try
        {
            rs = ps.executeQuery();

            while(rs.next())
            {
                long serial = rs.getLong("SERIAL");
                int ca_id = rs.getInt("CAINFO_ID");

                DeltaCRLCacheEntryType entry = new DeltaCRLCacheEntryType();
                entry.setCaId(ca_id);
                entry.setSerial(serial);
                list.add(entry);
            }
        }finally
        {
            releaseResources(ps, rs);
        }

        System.out.println(" Exported table DELTACRL_CACHE");
    }

    private void finalizeZip(ZipOutputStream zipOutStream, CertsType certsType)
    throws JAXBException, IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        marshaller.marshal(objFact.createCerts(certsType), bout);
        bout.flush();

        ZipEntry certZipEntry = new ZipEntry("certs.xml");
        zipOutStream.putNextEntry(certZipEntry);
        try
        {
            zipOutStream.write(bout.toByteArray());
        }finally
        {
            zipOutStream.closeEntry();
        }

        zipOutStream.close();
    }

    private String fp(byte[] data)
    {
        synchronized (sha1md)
        {
            sha1md.reset();
            sha1md.update(data, 0, data.length);
            byte[] digestValue = new byte[20];
            sha1md.doFinal(digestValue, 0);
            return Hex.toHexString(digestValue).toUpperCase();
        }
    }

    private static NameIdType createNameId(String name, int id)
    {
        NameIdType info = new NameIdType();
        info.setId(id);
        info.setName(name);
        return info;
    }

}
