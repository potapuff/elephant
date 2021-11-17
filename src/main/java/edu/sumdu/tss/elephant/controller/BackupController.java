package edu.sumdu.tss.elephant.controller;

import edu.sumdu.tss.elephant.helper.Keys;
import edu.sumdu.tss.elephant.helper.UserRole;
import edu.sumdu.tss.elephant.helper.ViewHelper;
import edu.sumdu.tss.elephant.model.Backup;
import edu.sumdu.tss.elephant.model.BackupService;
import io.javalin.Javalin;
import io.javalin.http.Context;

public class BackupController extends AbstractController {

    public static final String BASIC_PAGE = "/database/{database}/point/";

    public BackupController(Javalin app) {
        super(app);
    }

    public static void restore(Context context) {
        Backup point = setupPoint(context);
        BackupService.restore(currentUser(context).getUsername(), point.getDatabase(), point.getPoint());
        context.sessionAttribute(Keys.INFO_KEY, "Restore performed successfully");
        context.redirect(BASIC_PAGE.replace("{database}", point.getDatabase()));
    }

    public static void create(Context context) {
        String dbName = currentDB(context).getName();
        var currentUser = currentUser(context);
        int currentBackupCount = BackupService.list(dbName).size();
        if (currentBackupCount >= currentUser.role().maxBackupsPerDB()) {
            ViewHelper.softError("You limit reached",context);
            return;
        }

        String point = context.formParam("point");
        if (point == null) {
            point = context.pathParam("point");
        }
        if (point==null || point.isBlank()){
            ViewHelper.softError("Point name can't be empty", context);
            return;
        }

        BackupService.perform(currentUser.getUsername(), dbName, point);
        context.sessionAttribute(Keys.INFO_KEY, "Backup created successfully");
        context.redirect(BASIC_PAGE.replace("{database}", dbName));
    }

    public static void delete(Context context) {
        Backup point = setupPoint(context);
        BackupService.delete(currentUser(context).getUsername(), point.getDatabase(), point.getPoint());
        context.redirect(BASIC_PAGE.replace("{database}", point.getDatabase()));
    }

    public static void index(Context context) {
        var model = currentModel(context);
        var dbName = currentDB(context).getName();
        model.put("points", BackupService.list(dbName));
        ViewHelper.breadcrumb(context).add("Backups");
        context.render("/velocity/point/index.vm", model);
    }

    private static Backup setupPoint(Context context) {
        String dbName = currentDB(context).getName();
        String point = context.formParam("point");
        if (point == null) {
            point = context.pathParam("point");
        }
        return BackupService.byName(dbName, point);
    }

    public void register(Javalin app) {
        app.get(BASIC_PAGE, BackupController::index, UserRole.AUTHED);
        app.post(BASIC_PAGE, BackupController::create, UserRole.AUTHED);
        app.post(BASIC_PAGE + "{point}/create", BackupController::create, UserRole.AUTHED);
        app.post(BASIC_PAGE + "{point}/reset", BackupController::restore, UserRole.AUTHED);
        app.post(BASIC_PAGE + "{point}/delete", BackupController::delete, UserRole.AUTHED);
    }
}
