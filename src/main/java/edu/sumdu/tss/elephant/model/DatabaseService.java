package edu.sumdu.tss.elephant.model;

import edu.sumdu.tss.elephant.helper.DBPool;
import edu.sumdu.tss.elephant.helper.exception.AccessRestrictedException;
import edu.sumdu.tss.elephant.helper.exception.NotFoundException;
import edu.sumdu.tss.elephant.helper.utils.ParameterizedStringFactory;

import java.util.List;

public class DatabaseService {

    private static final String GET_BY_NAME_SQL = "select * from databases where name = :name";
    private static final String GET_BY_OWNER_SQL = "select * from databases where owner = :owner";
    private static final String DB_SIZE = "SELECT pg_database_size(:database)";
    private static final ParameterizedStringFactory CREATE_DATABASE_SQL = new ParameterizedStringFactory("CREATE database :name WITH OWNER :owner TABLESPACE :tablespace");
    private static final String REGISTER_DATABASE_SQL = "insert into databases(name, owner) values(:name, :owner)";

    /**
     *  Return Database with given name owned by user
     *
     * If user try act with DB owned to other user code throw AccessRestrictedException
     * @return Database owned to current user
    */

    public static Database activeDatabase(String owner, String dbName) {
        Database database = DatabaseService.byName(dbName);
        if (database.getOwner().equals(owner)) {
            return database;
        }
        throw new AccessRestrictedException("Database is inaccessible for this user");
    }


    private static final String PG_DB_SQL = "select datname from pg_database where datname=:name";
    public static boolean exists(String dbName){
        var database = DBPool.getConnection().open().createQuery(PG_DB_SQL).addParameter("name", dbName).executeScalar(String.class);
        return database != null;
    }

    public static Database byName(String dbName) {
        var database = DBPool.getConnection().open().createQuery(GET_BY_NAME_SQL).addParameter("name", dbName).executeAndFetchFirst(Database.class);
        if (database == null) {
            throw new NotFoundException(String.format("Database with name %s not found", dbName));
        }

        return database;
    }

    public static List<Database> forUser(String owner) {
        return DBPool.getConnection().open().createQuery(GET_BY_OWNER_SQL).addParameter("owner", owner).executeAndFetch(Database.class);
    }

    public static Integer size(String database) {
        return DBPool.getConnection().open().createQuery(DB_SIZE).addParameter("database", database).executeScalar(Integer.class);
    }

    public static void create(String dbName, String owner, String tablespace) {
        var connection = DBPool.getConnection().open();

        String query = CREATE_DATABASE_SQL.addParameter("name", dbName).addParameter("tablespace", tablespace).addParameter("owner", owner).toString();
        System.out.println(query);
        connection.createQuery(query, false)
                .executeUpdate();
        connection.createQuery(REGISTER_DATABASE_SQL)
                .addParameter("name", dbName)
                .addParameter("owner", owner)
                .executeUpdate();
    }

    private static final ParameterizedStringFactory DROP_DATABASE_SQL = new ParameterizedStringFactory("DROP DATABASE :name WITH (FORCE);");
    private static final String UNREGISTER_DATABASE_SQL = "delete from databases where name=:name and owner= :owner";

    public static void drop(Database database) {
        var connection = DBPool.getConnection().open();
        for (Backup point : BackupService.list(database.getName())) {
            BackupService.delete(database.getOwner(), point.getDatabase(), point.getPoint());
        }
        for (Script script : ScriptService.list(database.getName())) {
           ScriptService.destroy(script);
        }
        String query = DROP_DATABASE_SQL.addParameter("name", database.getName()).toString();
        connection.createQuery(query, false)
                .executeUpdate();
        connection.createQuery(UNREGISTER_DATABASE_SQL)
                .addParameter("name", database.getName())
                .addParameter("owner", database.getOwner())
                .executeUpdate();
    }

}