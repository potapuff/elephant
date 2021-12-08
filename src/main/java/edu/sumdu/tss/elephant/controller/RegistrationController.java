package edu.sumdu.tss.elephant.controller;

import edu.sumdu.tss.elephant.helper.Keys;
import edu.sumdu.tss.elephant.helper.MailService;
import edu.sumdu.tss.elephant.helper.UserRole;
import edu.sumdu.tss.elephant.helper.enums.Lang;
import edu.sumdu.tss.elephant.helper.exception.HttpError500;
import edu.sumdu.tss.elephant.helper.exception.NotFoundException;
import edu.sumdu.tss.elephant.helper.utils.ExceptionUtils;
import edu.sumdu.tss.elephant.helper.utils.MessageBundle;
import edu.sumdu.tss.elephant.helper.utils.ValidatorHelper;
import edu.sumdu.tss.elephant.model.DbUserService;
import edu.sumdu.tss.elephant.model.User;
import edu.sumdu.tss.elephant.model.UserService;
import io.javalin.Javalin;
import io.javalin.http.Context;

import javax.mail.MessagingException;
import java.util.Objects;
import java.util.Optional;


public class RegistrationController extends AbstractController {

    public static final String BASIC_PAGE = "/registration";

    public RegistrationController(Javalin app) {
        super(app);
    }

    public static void show(Context ctx) {
        ctx.render("/velocity/registration/show.vm", currentModel(ctx));
    }

    public static void create(Context context) {
        MessageBundle mb = currentMessages(context);
        try {
            String lang = (String) Optional.ofNullable(context.sessionAttribute(Keys.LANG_KEY)).orElse(Keys.get("DEFAULT_LANG"));
            User newUser = UserService.newDefaultUser();
            var login = context.formParamAsClass("login", String.class)
                    .check(Objects::nonNull, mb.get("validation.mail.empty"))
                    .check(ValidatorHelper::isValidMail, mb.get("validation.mail.invalid"))
                    .get();
            newUser.setLogin(login);
            var password = context.formParamAsClass("password", String.class)
                    .check(it -> it != null && !it.isBlank(), mb.get("validation.password.empty"))
                    .check(ValidatorHelper::isValidPassword, mb.get("validation.password.invalid"))
                    .get();
            newUser.password(password);

            newUser.setLanguage(lang);
            UserService.save(newUser);
            UserService.initUserStorage(newUser.getUsername());
            context.sessionAttribute("currentUser", newUser);
            DbUserService.initUser(newUser.getUsername(), newUser.getDbPassword());
            MailService.sendActivationLink(newUser.getToken(), newUser.getLogin(), Lang.byValue(lang));
        } catch (Exception ex) {
            if (ExceptionUtils.isSQLUniqueException(ex)) {
                context.sessionAttribute(Keys.ERROR_KEY, "Login (email) already taken");
            } else {
                ExceptionUtils.wrapError(context, ex);
            }
            context.redirect(BASIC_PAGE);
            return;
        }
        context.redirect(HomeController.BASIC_PAGE);
    }


    public static void userConformation(Context context) {
        String token = context.pathParam("token");
        User user = UserService.byToken(token);
        if (user == null) {
            throw new NotFoundException("User with this token not found (or token was renewed)");
        }
        user.setRole(UserRole.BASIC_USER.getValue());
        user.resetToken();
        UserService.save(user);
        context.sessionAttribute(Keys.SESSION_CURRENT_USER_KEY, user);
        context.sessionAttribute(Keys.INFO_KEY, "Email approved");
        context.redirect(HomeController.BASIC_PAGE);
    }

    public static void resendUserConformation(Context context) {
        var user = currentUser(context);
        try {
            MailService.sendActivationLink(user.getToken(), user.getLogin(), Lang.EN);
            context.sessionAttribute(Keys.INFO_KEY, "Resend conformation email");
        } catch (MessagingException e) {
            throw new HttpError500(e);
        }
        context.redirect(HomeController.BASIC_PAGE);
    }


    public void register(Javalin app) {
        app.get(BASIC_PAGE + "/confirm/{token}", RegistrationController::userConformation);
        app.get(BASIC_PAGE + "/resend-confirm/", RegistrationController::resendUserConformation, UserRole.AUTHED);
        app.get(BASIC_PAGE, RegistrationController::show, UserRole.ANYONE);
        app.post(BASIC_PAGE, RegistrationController::create, UserRole.ANYONE);
    }

}
