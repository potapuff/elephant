package edu.sumdu.tss.elephant.model;

import edu.sumdu.tss.elephant.helper.DBPool;
import edu.sumdu.tss.elephant.helper.exception.BackupException;
import edu.sumdu.tss.elephant.helper.exception.HttpError500;
import edu.sumdu.tss.elephant.helper.exception.NotFoundException;
import edu.sumdu.tss.elephant.helper.utils.CmdUtil;
import edu.sumdu.tss.elephant.helper.utils.ParameterizedStringFactory;
import org.apache.commons.io.FileUtils;
import org.sql2o.Connection;
import org.sql2o.Sql2oException;

import java.io.File;
import java.util.Date;
import java.util.List;

public class BackupService {

    private static final String LIST_BACKUP_SQL = "SELECT * from backups where database = :database";
    private static final String GET_BY_NAME_SQL = "SELECT * from backups where database = :database and point = :point";
    private static final String INSERT_SQL =
            """
                    insert into backups(database, point, status, "createdAt", "updatedAt")
                    values (:database, :point, :status, :createdAt, :updatedAt)  ON CONFLICT(database, point) DO UPDATE\s
                    SET status = excluded.status,\s
                       "updatedAt" = now();""";
    private static final String DELETE_BACKUP = "DELETE FROM backups WHERE database = :database and point = :point;";
    private static final ParameterizedStringFactory DROP_DB = new ParameterizedStringFactory("DROP DATABASE :database WITH (FORCE);");

    public static List<Backup> list(String dbName) {
        try (Connection con = DBPool.getConnection().open()) {
            return con.createQuery(LIST_BACKUP_SQL).addParameter("database", dbName).executeAndFetch(Backup.class);
        }
    }

    public static Backup byName(String database, String point) {
        try (Connection con = DBPool.getConnection().open()) {
            Backup backup = con.createQuery(GET_BY_NAME_SQL).addParameter("database", database).addParameter("point", point).executeAndFetchFirst(Backup.class);
            if (backup == null) {
                throw new NotFoundException("Point not found");
            }
            return backup;
        }
    }

    public static void perform(String owner, String database, String pointName) throws BackupException {
        try (Connection con = DBPool.getConnection().open()) {
            Backup point;
            try {
                point = byName(database, pointName);
            } catch (NotFoundException ex) {
                point = new Backup();
                point.setDatabase(database);
                point.setPoint(pointName);
                point.setCreatedAt(new Date());
            }
            point.setUpdatedAt(new Date());
            point.setStatus(Backup.BackupState.PERFORMED.name());
            BackupService.save(point);
            BackupService.createBackup(owner, database, pointName);
            point.setUpdatedAt(new Date());
            point.setStatus(Backup.BackupState.DONE.name());
            BackupService.save(point);
        } catch (Sql2oException ex) {
            throw new BackupException(ex);
        }
    }

    private static void save(Backup point) {
        try (Connection con = DBPool.getConnection().open()) {
            con.createQuery(INSERT_SQL, false).bind(point).executeUpdate();
        }
    }

    static public void restore(String owner, String dbName, String pointName) throws BackupException {
        try (Connection con = DBPool.getConnection().open()) {
            Backup point = con.createQuery(GET_BY_NAME_SQL)
                    .addParameter("database", dbName)
                    .addParameter("point", pointName)
                    .executeAndFetchFirst(Backup.class);
            if (point == null) {
                point = new Backup();
                point.setDatabase(dbName);
                point.setPoint(pointName);
                point.setCreatedAt(new Date());
            }
            point.setUpdatedAt(new Date());
            point.setStatus(Backup.BackupState.PERFORMED.name());
            BackupService.save(point);
            BackupService.restoreBackup(owner, dbName, pointName);
            point.setUpdatedAt(new Date());
            point.setStatus(Backup.BackupState.DONE.name());
            BackupService.save(point);
        } catch (Sql2oException ex) {
            throw new BackupException(ex);
        }
    }

    public static void delete(String owner, String database, String point) {
        String path = filePath(owner, database, point);
        DBPool.getConnection().open().createQuery(DELETE_BACKUP, false)
                .addParameter("database", database)
                .addParameter("point", point)
                .executeUpdate();
        if (!new File(path).delete()) {
            throw new RuntimeException("File not deleted");
        }
    }

    private static void createBackup(String owner, String database, String pointName) {
        String path = filePath(owner, database, pointName);
        try {
            FileUtils.forceMkdirParent(new File(path));
        } catch (Exception ex) {
            throw new HttpError500(ex);
        }
        CmdUtil.exec(String.format("pg_dump --format=custom --dbname=%s  --file=\"%s\"", DBPool.dbUtilUrl(database), path));
    }

    private static void restoreBackup(String owner, String database, String pointName) {
        String path = filePath(owner, database, pointName);
        //DBPool.getConnection().open().createQuery(DROP_DB.addParameter("database", database).toString(), false).executeUpdate();
        var recreate = DatabaseService.exists(database) ? "--clean" : "";
        CmdUtil.exec(String.format("pg_restore %s --create --dbname=%s \"%s\"", recreate, DBPool.dbUtilUrl(DBPool.DEFAULT_DATABASE), path));
    }

    public static String filePath(String owner, String database, String pointName) {
        return UserService.userStoragePath(owner) +
                File.separator + "backups" +
                File.separator + database +
                File.separator + pointName;
    }
}
