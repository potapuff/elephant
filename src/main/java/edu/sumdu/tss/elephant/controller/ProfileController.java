package edu.sumdu.tss.elephant.controller;

import edu.sumdu.tss.elephant.helper.Keys;
import edu.sumdu.tss.elephant.helper.UserRole;
import edu.sumdu.tss.elephant.helper.enums.Lang;
import edu.sumdu.tss.elephant.helper.utils.StringUtils;
import edu.sumdu.tss.elephant.model.DbUserService;
import edu.sumdu.tss.elephant.model.User;
import edu.sumdu.tss.elephant.model.UserService;
import io.javalin.Javalin;
import io.javalin.core.util.JavalinLogger;
import io.javalin.http.Context;

public class ProfileController extends AbstractController {

    public static final String BASIC_PAGE = "/profile";

    public ProfileController(Javalin app) {
        super(app);
    }

    public static void show(Context context) {
        context.render("/velocity/profile/show.vm", currentModel(context));
    }

    public static void language(Context context) {
        User user = currentUser(context);
        var lang = context.queryParam("lang");
        user.setLanguage(Lang.byValue(lang).toString());
        UserService.save(user);
        context.redirect(BASIC_PAGE);
    }

    public static void resetDbPassword(Context context) {
        User user = currentUser(context);
        //TODO add password validation
        JavalinLogger.info(user.toString());
        user.setDbPassword(context.formParam("db-password"));
        JavalinLogger.info(user.toString());
        UserService.save(user);
        DbUserService.dbUserPasswordReset(user.getUsername(), user.getDbPassword());
        context.sessionAttribute(Keys.INFO_KEY, "DB user password was changed");
        context.redirect(BASIC_PAGE);
    }

    public static void resetWebPassword(Context context) {
        User user = currentUser(context);
        //TODO add password validation
        user.password(context.formParam("web-password"));
        UserService.save(user);
        context.sessionAttribute(Keys.INFO_KEY, "Web user password was changed");
        context.redirect(BASIC_PAGE);
    }

    public static void resetApiPassword(Context context) {
        User user = currentUser(context);
        //TODO add password validation
        user.setPrivateKey(StringUtils.randomAlphaString(20));
        user.setPublicKey(StringUtils.randomAlphaString(20));
        UserService.save(user);
        context.sessionAttribute(Keys.INFO_KEY, "API keys was reset successful");
        context.redirect(BASIC_PAGE);
    }

    public static void upgradeUser(Context context) {
        User user = currentUser(context);
        user.setRole(UserRole.valueOf(context.formParam("role")).getValue());
        UserService.save(user);
        context.sessionAttribute(Keys.INFO_KEY, "Role has been changed");
        context.redirect(BASIC_PAGE);
    }

    public static void removeSelf(Context context) {
        User user = currentUser(context);
        DbUserService.dropUser(user.getUsername());
        //TODO: delete all user-specific files
        //TODO: logout
        //TODO: remove web-user from DB
        context.redirect("/");
    }

    public void register(Javalin app) {
        app.get(BASIC_PAGE + "/lang", ProfileController::language, UserRole.AUTHED);
        app.post(BASIC_PAGE + "/reset-password", ProfileController::resetWebPassword, UserRole.AUTHED);
        app.post(BASIC_PAGE + "/reset-db", ProfileController::resetDbPassword, UserRole.AUTHED);
        app.post(BASIC_PAGE + "/reset-api", ProfileController::resetApiPassword, UserRole.AUTHED);
        app.post(BASIC_PAGE + "/upgrade", ProfileController::upgradeUser, UserRole.AUTHED);
        app.post(BASIC_PAGE + "/remove-self", ProfileController::removeSelf, UserRole.AUTHED);
        app.get(BASIC_PAGE, ProfileController::show, UserRole.AUTHED);
    }

}
