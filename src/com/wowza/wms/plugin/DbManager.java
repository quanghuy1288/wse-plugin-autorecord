package com.wowza.wms.plugin;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;

public class DbManager {
    private static String CLASSNAME = ModuleAutoRecord.CLASSNAME + "." + DbManager.class.getSimpleName();
    private WMSLogger logger = WMSLoggerFactory.getLogger(DbManager.class);
    //private final static String DATABASE_URL = "jdbc:h2:mem:account";
    //private final static String DATABASE_URL = "jdbc:mysql:ormlite:channelrecord.db";
    private static String DATABASE_URL;

    private static Dao<FileRecording, Integer> fileRecordingDao;

    public DbManager(String databaseUrl){
        logger.warn(CLASSNAME + " get DbManager with DATABASE_URL: " + databaseUrl);
        if(databaseUrl != null)
            DATABASE_URL = databaseUrl;
    }

    public static DbManager getInstance(String databaseUrl){
        DbManager instance_ = new DbManager(databaseUrl);
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(databaseUrl);
            instance_.setupDatabase(connectionSource);
            instance_.createDatabase(connectionSource);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return instance_;
    }

    public static void main(String[] args){
        DbManager db = DbManager.getInstance("jdbc:mysql:ormlite:autorecord.db");
        try {
            System.out.println(db.getRecordingFileListByStreamName("test"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupDatabase(ConnectionSource connectionSource) throws Exception {
        fileRecordingDao = com.j256.ormlite.dao.DaoManager.createDao(connectionSource, FileRecording.class);
    }

    private void createDatabase(ConnectionSource connectionSource) throws Exception {
        TableUtils.createTableIfNotExists(connectionSource, FileRecording.class);
    }

    private void testReadWriteData(){
        FileRecording fileRecording = new FileRecording(null);
        try {
            fileRecordingDao.create(fileRecording);
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public FileRecording createFileRecording(FileRecording file) throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            if (fileRecordingDao.create(file) > 0) {
                logger.warn(CLASSNAME + " createFileRecording: " + file.toString());
                return file;
            }
            logger.warn(CLASSNAME + " createFileRecording failed: " + file.toString());
            return null;
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }

    public FileRecording updateFileRecording(FileRecording file) throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            if (fileRecordingDao.update(file) > 0) {
                logger.warn(CLASSNAME + " updateFileRecording: " + file.toString());
                return file;
            }
            logger.warn(CLASSNAME + " updateFileRecording failed: " + file.toString());
            return null;
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }

    public boolean deleteFileRecording(FileRecording file) throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            if (fileRecordingDao.delete(file) > 0)
                return true;
            return false;
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }

    public FileRecording getFileRecording(String filePath) throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            QueryBuilder<FileRecording, Integer> qb = fileRecordingDao.queryBuilder();
            qb.where().eq("file_path", filePath);
            qb.orderBy("id", false);
            List<FileRecording> files = qb.query();
            if (files.isEmpty())
                return null;

            FileRecording newestFile = files.get(0);
            logger.warn(CLASSNAME + " getFileRecording: " + newestFile.toString());
            return newestFile;
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }

    public List<FileRecording> getRecordingFileListByStreamName(String streamName) throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            QueryBuilder<FileRecording, Integer> qb = fileRecordingDao.queryBuilder();
            qb.where().eq("stream_name", streamName);
            qb.orderBy("id", false);
            logger.warn(CLASSNAME + " getRecordingFileListByStreamName: " + qb.query().size());
            return qb.query();
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }

    public List<FileRecording> getRecordingFileListBySourceStream(String sourceStreamName) throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            QueryBuilder<FileRecording, Integer> qb = fileRecordingDao.queryBuilder();
            qb.where().eq("source_stream_name", sourceStreamName);
            qb.orderBy("id", false);
            logger.warn(CLASSNAME + " getRecordingFileListBySourceStream: " + qb.query().size());
            return qb.query();
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }

    public List<FileRecording> getAllRecordingFileList() throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            QueryBuilder<FileRecording, Integer> qb = fileRecordingDao.queryBuilder();
            qb.orderBy("id", false);
            logger.warn(CLASSNAME + " getRecordingFileList all: " + qb.query().size());
            return qb.query();
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }

    public List<FileRecording> getRecordingFileByUuid(String uuid) throws Exception {
        ConnectionSource connectionSource = null;
        try {
            connectionSource = new JdbcConnectionSource(DATABASE_URL);
            Map<String, Object> filter = new HashMap<String, Object>();
            filter.put("uuid", uuid);

            return fileRecordingDao.queryForFieldValues(filter);
        } finally {
            if (connectionSource != null) {
                connectionSource.close();
            }
        }
    }
}


