package edu.sumdu.tss.elephant.helper;

import edu.sumdu.tss.elephant.controller.AbstractController;
import edu.sumdu.tss.elephant.helper.exception.HttpException;
import edu.sumdu.tss.elephant.helper.utils.ExceptionUtils;
import edu.sumdu.tss.elephant.helper.utils.MessageBundle;
import edu.sumdu.tss.elephant.model.Database;
import edu.sumdu.tss.elephant.model.DatabaseService;
import edu.sumdu.tss.elephant.model.User;
import io.javalin.core.util.JavalinLogger;
import io.javalin.http.Context;

import java.util.*;
import java.util.regex.Pattern;

public final class ViewHelper {

    public static final String[] FLASH_KEY = {Keys.ERROR_KEY, Keys.INFO_KEY};

    private static final Pattern DB_NAME_PATTERN = Pattern.compile("database/(\\w+)");


    private ViewHelper() {
    }

    public static void userError(HttpException exception, final Context context) {
        String stacktrace = ExceptionUtils.stacktrace(exception);
        userError(context, exception.getCode(), exception.getMessage(), exception.getIcon(), stacktrace);
    }

    public static void userError(final Context context, final Integer code,
                                 final String message, final String icon, final String stacktrace) {
        var model = AbstractController.currentModel(context);
        model.put("code", code.toString());
        model.put("message", message);
        model.put("icon", icon);
        if (!Keys.isProduction()) {
            model.put("stacktrace", stacktrace);
        }
        context.status(code);
        context.render("/velocity/error.vm", model);
    }

    public static List<String> breadcrumb(Context context) {
        List<String> breadcrumb = context.sessionAttribute(Keys.BREADCRUMB_KEY);
        if (breadcrumb == null) {
            breadcrumb = new ArrayList<>();
            breadcrumb.add("<a href='/home'><ion-icon name=\"home-outline\"></ion-icon></a>");
            context.sessionAttribute(Keys.BREADCRUMB_KEY, breadcrumb);
        }
        return breadcrumb;
    }

    public static void cleanupSession(final Context context) {
        context.sessionAttribute(Keys.MODEL_KEY, null);
        context.sessionAttribute(Keys.DB_KEY, null);
        context.sessionAttribute(Keys.BREADCRUMB_KEY, null);
    }

    public static void defaultVariables(final Context context) {
        Map<String, Object> model = new HashMap<>();
        context.sessionAttribute(Keys.MODEL_KEY, model);

        model.put("csrf", context.sessionAttribute("csrf"));

        User user = context.sessionAttribute(Keys.SESSION_CURRENT_USER_KEY);
        model.put("currentUser", user);
        if (user != null) {
            JavalinLogger.info(user.toString());
        }

        String lang = user == null ? context.sessionAttribute(Keys.LANG_KEY) : user.getLanguage();
        model.put("msg", new MessageBundle(lang));
        model.put(Keys.LANG_KEY, lang);

        var crumbs = breadcrumb(context);
        model.put(Keys.BREADCRUMB_KEY, crumbs);

        String dbName = dbName(context);
        if (user != null && dbName != null && !dbName.isBlank()) {
            Database database = DatabaseService.activeDatabase(user.getUsername(), dbName);
            model.put(Keys.DB_KEY, database);
            context.sessionAttribute(Keys.DB_KEY, database);
            crumbs.add(String.format("<a href='/database/%s/'><ion-icon name=\"server-outline\"></ion-icon>%s</a>", dbName, dbName));
        }
        //One-time message, clear after usage
        for (var key : FLASH_KEY) {
            String message = context.sessionAttribute(key);
            if (message != null) {
                model.put(key, message);
                context.sessionAttribute(key, null);
            }
        }
    }

    public static String pager(int totalPage, int currentPage) {
        StringBuilder pager = new StringBuilder(500);
        pager.append("<nav>\n<ul class=\"pagination\">");
        for (int i = 1; i < totalPage; i++) {
            if (i != currentPage) {
                pager.append(String.format("<li class=\"page-item\"><a class=\"page-link\" href=\"?offset=%d\">%d</a></li>\n", i, i));
            } else {
                pager.append(String.format("<li class=\"page-item active\"><a class=\"page-link\" href=\"#\">%d</a></li>\n", i));
            }
        }
        pager.append("</ul>\n</nav>");
        return pager.toString();
    }

    /**
     * Dirty hack: context.pathParam is not initialized in before filter. Use regexp, luke!
     *
     * @param context
     * @return database name from params
     */
    private static String dbName(Context context) {
        JavalinLogger.info(context.path());
        var matcher = DB_NAME_PATTERN.matcher(context.path());
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     *
     *  Redirect back with error message
     * @param message message for an error
     * @param context request context
     */
    public static void softError(String message, Context context) {
        context.sessionAttribute(Keys.ERROR_KEY, message);
        ViewHelper.redirectBack(context);

    }

    public static void redirectBack(Context context) {
        context.redirect(Optional.ofNullable(context.header("Referer")).orElse("/"));
    }
}
