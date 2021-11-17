package edu.sumdu.tss.elephant.model;

import edu.sumdu.tss.elephant.helper.DBPool;
import edu.sumdu.tss.elephant.helper.Keys;
import edu.sumdu.tss.elephant.helper.UserRole;
import edu.sumdu.tss.elephant.helper.exception.NotFoundException;
import edu.sumdu.tss.elephant.helper.utils.CmdUtil;
import edu.sumdu.tss.elephant.helper.utils.ParameterizedStringFactory;
import edu.sumdu.tss.elephant.helper.utils.StringUtils;
import io.javalin.core.util.JavalinLogger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.sql2o.Connection;

import java.io.File;

public class UserService {


    private static final ParameterizedStringFactory CREATE_TABLESPACE_SQL
            = new ParameterizedStringFactory("CREATE TABLESPACE :name" +
            "    OWNER :name" +
            "    LOCATION ':path'");


    private static final String USER_BY_TOKEN_SQL = "SELECT * FROM users WHERE token = :token";
    private static final String INSERT_SQL =
            "insert into USERS(login, password, role, username, dbPassword, publickey, privatekey, token) " +
                    "values (:login, :password, :role, :username, :dbPassword, :publicKey, :privateKey, :token)";
    private static final String UPDATE_SQL =
            "UPDATE users" +
                    " SET password = :password, role = :role, username= :username, dbPassword=:dbPassword," +
                    " publicKey = :publicKey, privateKey=:privateKey, token=:token, language= :language" +
                    " WHERE id = :id";

    private static final String USER_BY_LOGIN_SQL = "SELECT * FROM users WHERE login = :login";
    public static User byLogin(String login) {
        try (Connection con = DBPool.getConnection().open()) {
            var user = con.createQuery(USER_BY_LOGIN_SQL).addParameter("login", login).executeAndFetchFirst(User.class);
            if (user == null){
                throw new NotFoundException(String.format("User with mail %s not found", login));
            }
            return  user;
        }
    }

    private static final String USER_BY_KEY_SQL = "SELECT * FROM users WHERE publicKey = :publicKey";

    public static void save(User user) {
        try (Connection con = DBPool.getConnection().open()) {
            String query = user.getId() == null ? INSERT_SQL : UPDATE_SQL;
            long id = con.createQuery(query).bind(user).executeUpdate().getKey(Long.class);
            user.setId(id);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }
    private static final String[] SPACE_SCOPES = new String[]{"tablespace", "scripts", "backups"};

    //"    [ WITH ( параметр_табличного_пространства = значение [, ... ] ) ]"
    public static void createTablespace(String owner, String path) {
        String create_tablespace = CREATE_TABLESPACE_SQL.addParameter("name", owner).addParameter("path", path.replace("\\", "\\\\")).toString();
        System.out.println(create_tablespace);
        DBPool.getConnection().open()
                .createQuery(create_tablespace, false)
                .executeUpdate();
    }

    private static final ParameterizedStringFactory CHANGE_OWNER
            = new ParameterizedStringFactory("sudo chown -f :user :path");
    private static final ParameterizedStringFactory CHANGE_MODE
            = new ParameterizedStringFactory("chmod 700 :path");

    public static User byPublicKey(String publicKey) {
        try (Connection con = DBPool.getConnection().open()) {
            return con.createQuery(USER_BY_KEY_SQL).addParameter("publicKey", publicKey).executeAndFetchFirst(User.class);
        }
    }

    public static String userStoragePath(String owner) {
        return Keys.get("DB.LOCAL_PATH") + owner;
    }

    public static void initUserStorage(String owner) {
        try {
            String path = userStoragePath(owner);
            for (var scope : SPACE_SCOPES) {
                File subPath = new File(path + File.separator + scope);
                FileUtils.forceMkdir(subPath);
                if (scope.equals("tablespace") && !SystemUtils.IS_OS_WINDOWS) {
                    /* Under *nix OS tablespace folder must be owned by RDBMS-owner user   */
                    CmdUtil.exec(CHANGE_MODE.addParameter("path", subPath.getPath()).toString());
                    CmdUtil.exec(CHANGE_OWNER.addParameter("user", Keys.get("DB.OS_USER")).addParameter("path", subPath.getPath()).toString());
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static long storageSize(String owner) {
        try {
            return FileUtils.sizeOfDirectory(new File(UserService.userStoragePath(owner)));
        } catch (IllegalArgumentException ex) {
            JavalinLogger.error("Folder not found", ex);
            return 0;
        }
    }

    public static User newDefaultUser() {
        User user = new User();
        user.setRole(UserRole.UNCHEKED.getValue());
        user.setPrivateKey(StringUtils.randomAlphaString(User.API_KEY_SIZE));
        user.setPublicKey(StringUtils.randomAlphaString(User.API_KEY_SIZE));
        user.resetToken();
        user.setUsername(StringUtils.randomAlphaString(User.USERNAME_SIZE));
        user.setDbPassword(StringUtils.randomAlphaString(User.DB_PASSWORD_SIZE));
        return user;
    }

    public static User byToken(String token) {
        try (Connection con = DBPool.getConnection().open()) {
            var user=  con.createQuery(USER_BY_TOKEN_SQL).addParameter("token", token).executeAndFetchFirst(User.class);
            if (user == null){
                throw new NotFoundException(String.format("User with token %s not found", token));
            }
            return  user;
        }
    }
}
