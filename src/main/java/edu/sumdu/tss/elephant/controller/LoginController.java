package edu.sumdu.tss.elephant.controller;

import edu.sumdu.tss.elephant.helper.Keys;
import edu.sumdu.tss.elephant.helper.MailService;
import edu.sumdu.tss.elephant.helper.UserRole;
import edu.sumdu.tss.elephant.helper.ViewHelper;
import edu.sumdu.tss.elephant.helper.enums.Lang;
import edu.sumdu.tss.elephant.helper.exception.HttpError500;
import edu.sumdu.tss.elephant.helper.exception.NotFoundException;
import edu.sumdu.tss.elephant.helper.utils.ExceptionUtils;
import edu.sumdu.tss.elephant.helper.utils.MessageBundle;
import edu.sumdu.tss.elephant.helper.utils.ResponseUtils;
import edu.sumdu.tss.elephant.helper.utils.ValidatorHelper;
import edu.sumdu.tss.elephant.model.User;
import edu.sumdu.tss.elephant.model.UserService;
import io.javalin.Javalin;
import io.javalin.core.util.JavalinLogger;
import io.javalin.http.Context;

import javax.mail.MessagingException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class LoginController extends AbstractController {

    public static final String BASIC_PAGE = "/login";

    public LoginController(Javalin app) {
        super(app);
    }

    public static void show(Context context) {
        context.render("/velocity/login/show.vm", currentModel(context));
    }

    public static void create(Context context) {
        MessageBundle mb = currentMessages(context);
        User user = null;
        String password = null;
        try {
            var login = context.formParamAsClass("login", String.class)
                    .check(Objects::nonNull, mb.get("validation.mail.empty"))
                    .check(ValidatorHelper::isValidMail, mb.get("validation.mail.invalid"))
                    .get();
            password = context.formParamAsClass("password", String.class)
                    .check(it -> it != null && !it.isBlank(), mb.get("validation.password.empty"))
                    .get();
            user = UserService.byLogin(login);
        } catch (NotFoundException ex) {
            context.sessionAttribute(Keys.ERROR_KEY, mb.get("validation.user.not_found"));
        } catch (Exception ex) {
            ExceptionUtils.wrapError(context, ex);
        }
        if (user != null && user.getPassword().equals(user.crypt(password))) {
            context.sessionAttribute(Keys.SESSION_CURRENT_USER_KEY, user);
            context.redirect(HomeController.BASIC_PAGE);
            return;
        } else {
            context.sessionAttribute(Keys.ERROR_KEY, mb.get("validation.user.not_found"));
        }
        context.redirect(BASIC_PAGE, 302);
    }

    public static void resetLink(Context context) {
        MessageBundle mb = currentMessages(context);
        try {
            if (context.method().equals("POST")) {
                var email = context.formParamAsClass("email", String.class)
                        .check(Objects::nonNull, mb.get("validation.mail.empty"))
                        .check(ValidatorHelper::isValidMail, mb.get("validation.mail.invalid"))
                        .get();
                JavalinLogger.info(email);
                var user = UserService.byLogin(email);
                var lang = Lang.byValue(user.getLanguage());
                MailService.sendResetLink(user.getToken(), user.getLogin(), lang);
                context.sessionAttribute(Keys.INFO_KEY, mb.get("login.reset.success_send"));
                context.redirect(BASIC_PAGE, 302);
                return;
            }
        } catch (MessagingException ex) {
            throw new HttpError500(ex);
        } catch (NotFoundException ex) {
            context.sessionAttribute(Keys.ERROR_KEY, mb.get("validation.user.not_found"));
        } catch (Exception ex) {
            ExceptionUtils.wrapError(context, ex);
        }

        Map<String, Object> model = currentModel(context);
        model.put(Keys.ERROR_KEY, context.sessionAttribute(Keys.ERROR_KEY));
        model.put(Keys.INFO_KEY, context.sessionAttribute(Keys.INFO_KEY));
        ResponseUtils.flush_flash(context);
        context.render("/velocity/login/reset-link.vm", model);
    }

    public static void resetPassword(Context context) {
        MessageBundle mb = currentMessages(context);
        try {
            if (context.method().equals("POST")) {
                String token = context.formParamAsClass("token", String.class)
                        .check(it -> it != null && !it.isBlank(), mb.get("validation.token.empty"))
                        .get();
                var password = context.formParamAsClass("password", String.class)
                        .check(it -> it != null && !it.isBlank(), mb.get("validation.password.empty"))
                        .check(ValidatorHelper::isValidPassword, mb.get("validation.invalid.empty"))
                        .get();
                var user = UserService.byToken(token);
                user.password(password);
                user.resetToken();
                UserService.save(user);
                context.sessionAttribute(Keys.INFO_KEY, mb.get("login.reset.success_reset"));
                context.redirect(BASIC_PAGE);
                return;
            }
        } catch (NotFoundException ex) {
            context.sessionAttribute(Keys.ERROR_KEY, mb.get("validation.user.not_found_token"));
            context.redirect(BASIC_PAGE);
        } catch (Exception ex) {
            ExceptionUtils.wrapError(context, ex);
        }

        var token = context.queryParamAsClass("token", String.class).getOrDefault("");
        Map<String, Object> model = currentModel(context);
        model.put("token", token);
        model.put(Keys.ERROR_KEY, context.sessionAttribute(Keys.ERROR_KEY));
        model.put(Keys.INFO_KEY, context.sessionAttribute(Keys.INFO_KEY));
        ResponseUtils.flush_flash(context);
        context.render("/velocity/login/reset.vm", model);
    }

    public static void destroy(Context context) {
        ResponseUtils.flush_flash(context);
        context.sessionAttribute(Keys.SESSION_CURRENT_USER_KEY, null);
        context.redirect(BASIC_PAGE);
    }

    public static void lang(Context context) {
        String lang = null;
        try {
            lang = Lang.byValue(context.pathParam("lang")).toString();
        } catch (RuntimeException ex) {
            context.sessionAttribute(Keys.ERROR_KEY, ex.getMessage());
            ex.printStackTrace();
        }
        context.sessionAttribute(Keys.LANG_KEY, Optional.ofNullable(lang).orElse(Keys.get("DEFAULT_LANG")));
        ViewHelper.redirectBack(context);
    }

    public void register(Javalin app) {
        app.get(BASIC_PAGE, LoginController::show, UserRole.ANYONE);
        app.post(BASIC_PAGE, LoginController::create, UserRole.ANYONE);
        app.get(BASIC_PAGE + "/reset-password", LoginController::resetLink, UserRole.ANYONE);
        app.post(BASIC_PAGE + "/reset-password", LoginController::resetLink, UserRole.ANYONE);
        app.get(BASIC_PAGE + "/reset", LoginController::resetPassword, UserRole.ANYONE);
        app.post(BASIC_PAGE + "/reset", LoginController::resetPassword, UserRole.ANYONE);
        app.get(BASIC_PAGE + "/lang/{lang}", LoginController::lang, UserRole.ANYONE);
        app.get("/logout", LoginController::destroy, UserRole.AUTHED);
    }

}
