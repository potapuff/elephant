package edu.sumdu.tss.elephant.controller;

import edu.sumdu.tss.elephant.helper.DBPool;
import edu.sumdu.tss.elephant.helper.Pair;
import edu.sumdu.tss.elephant.helper.ViewHelper;
import edu.sumdu.tss.elephant.helper.exception.AccessRestrictedException;
import edu.sumdu.tss.elephant.helper.exception.HttpError500;
import edu.sumdu.tss.elephant.helper.exception.NotFoundException;
import edu.sumdu.tss.elephant.helper.sql.ScriptReader;
import edu.sumdu.tss.elephant.helper.utils.StringUtils;
import edu.sumdu.tss.elephant.model.Script;
import edu.sumdu.tss.elephant.model.ScriptService;
import edu.sumdu.tss.elephant.model.UserService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class ScriptsController extends AbstractController {

    public static final String BASIC_PAGE = "/database/{database}/script/";

    public ScriptsController(Javalin app) {
        super(app);
    }

    public static void create(Context context) {
        var database = currentDB(context);
        var currentUser = currentUser(context);
        int currentScriptCount = ScriptService.list(database.getName()).size();
        if (currentScriptCount >= currentUser.role().maxScriptsPerDB()) {
            ViewHelper.softError("You limit reached",context);
            return;
        }

        var file = context.uploadedFile("file");
        var description = context.formParamAsClass("description",String.class).getOrDefault("");
        String path = UserService.userStoragePath(currentUser.getUsername()) +
                File.separator + "scripts" +
                File.separator + database.getName() +
                File.separator + StringUtils.randomAlphaString(20);
        var destinationFile = new File(path);
        try {
            assert file != null;
            FileUtils.forceMkdirParent(destinationFile);
            FileUtils.copyInputStreamToFile(file.getContent(), destinationFile);
        } catch (IOException ex) {
            throw new HttpError500(ex);
        }
        Script script = new Script();
        script.setFilename(file.getFilename());
        script.setDescription(description);
        script.setSize(file.getSize());
        script.setPath(path);
        script.setDatabase(database.getName());
        ScriptService.save(script);

        context.redirect(BASIC_PAGE.replace("{database}", database.getName()));
    }

    public static void show(Context context) {
        String scriptId = context.pathParam("script");
        Script script = ScriptService.byId(Integer.valueOf(scriptId));

        if (!script.getDatabase().equals(currentDB(context).getName())) {
            throw new AccessRestrictedException("Script has other owner");
        }
        try {
            context.result(new FileInputStream(script.getPath()));
        } catch (FileNotFoundException e) {
            throw new HttpError500("File not found", e);
        }
    }

    public static void index(Context context) {
        var model = currentModel(context);
        model.put("scripts", ScriptService.list(currentDB(context).getName()));
        ViewHelper.breadcrumb(context).add("Scripts");
        context.render("/velocity/script/index.vm", model);
    }

    public static void run(Context context) {
        Statement statement = null;
        Connection connection = null;
        var list = new ArrayList<Pair<String, String>>(500);
        try {
            String database = context.pathParam("database");
            String scriptId = context.pathParam("script");
            Script script = ScriptService.byId(Integer.valueOf(scriptId));
            var sr = new ScriptReader(new BufferedReader(new FileReader(script.getPath())));
            String line;
            String result;
            connection = DBPool.getConnection(database).open().getJdbcConnection();
            statement = connection.createStatement();
            while ((line = sr.readStatement()) != null) {
                try {
                    statement.executeQuery(line);
                    result = "ok";
                } catch (SQLException ex) {
                    result =
                            ex.getSQLState() +
                                    ex.getErrorCode() +
                                    ex.getMessage();
                }
                list.add(new Pair<>(line, result));
            }
        } catch (FileNotFoundException ex) {
            throw new HttpError500(ex);
        } catch (SQLException ex) {
            throw new HttpError500("Problem with database connection", ex);
        } finally {
            if (statement != null) try {
                statement.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (connection != null) try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        var model = currentModel(context);
        model.put("executeResults", list);
        context.render("/velocity/script/run.vm", model);
    }

    public static void delete(Context context) {
        String scriptId = context.pathParam("script");
        String dbName = currentDB(context).getName();
        Script script = ScriptService.byId(Integer.valueOf(scriptId));
        if (!script.getDatabase().equals(dbName)) {
            throw new NotFoundException("Script not found");
        }
        ScriptService.destroy(script);
        context.redirect(BASIC_PAGE.replace("{database}", dbName));
    }

    public void register(Javalin app) {
        app.get(BASIC_PAGE, ScriptsController::index);
        app.post(BASIC_PAGE, ScriptsController::create);
        app.get(BASIC_PAGE + "{script}", ScriptsController::show);
        app.post(BASIC_PAGE + "{script}", ScriptsController::run);
        app.post(BASIC_PAGE + "{script}/delete", ScriptsController::delete);
    }

}
