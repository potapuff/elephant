package edu.sumdu.tss.elephant.helper;

import com.google.common.io.Resources;
import edu.sumdu.tss.elephant.helper.enums.Lang;
import edu.sumdu.tss.elephant.helper.utils.MessageBundle;
import edu.sumdu.tss.elephant.helper.utils.StringUtils;
import io.javalin.core.util.JavalinLogger;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

//TODO: reset temaplte for html+password
public class MailService {

    private static final MailService mail = new MailService();
    private final String from;
    private final Session session;

    private MailService() {
        this.from = Keys.get("EMAIL.FROM");

        Properties properties = System.getProperties();
        properties.put("mail.smtp.host", Keys.get("EMAIL.HOST"));
        properties.put("mail.smtp.port", Keys.get("EMAIL.PORT"));
        properties.put("mail.smtp.ssl.enable", "true");
        properties.put("mail.smtp.auth", "true");
        session = Session.getInstance(properties, new javax.mail.Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(Keys.get("EMAIL.USER"), Keys.get("EMAIL.PASSWORD"));
            }
        });
        //session.setDebug(true);
    }

    /**
     * Method to send email with activation link
     *
     * @param token  activation token
     * @param mailTo user email
     */
    public static void sendActivationLink(String token, String mailTo, Lang lang) throws MessagingException {
        Message message = new MimeMessage(mail.session);
        message.setFrom(new InternetAddress(mail.from));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(mailTo));
        message.setSubject(new MessageBundle(lang.toString()).get("mail.conformation"));
        message.setContent(generateContent("mail_conformation", new Object[] {Keys.get("APP.URL"), token}, lang));
        Transport.send(message);
    }

    public static void sendResetLink(String token, String mailTo, Lang lang) throws MessagingException {
        Message message = new MimeMessage(mail.session);
        message.setFrom(new InternetAddress(mail.from));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(mailTo));
        message.setSubject(new MessageBundle(lang.toString()).get("mail.reset"));
        message.setContent(generateContent("password_reset", new Object[] {Keys.get("APP.URL"), token}, lang));
        Transport.send(message);
    }

    private String getResource(String resource, Lang langCode) {
        try {
            String lang = langCode.toString().toLowerCase();
            String resourceName = StringUtils.replaceLast(resource, ".", "_" + lang + ".");
            JavalinLogger.info(resourceName);
            @SuppressWarnings("UnstableApiUsage") URL url = Resources.getResource(resourceName);
            JavalinLogger.info(url.toString());
            @SuppressWarnings("UnstableApiUsage") String result = Resources.toString(url, StandardCharsets.UTF_8);
            JavalinLogger.info(result);
            return result;
        } catch (IOException e) {
            JavalinLogger.info(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static Multipart generateContent(final String template, Object[] params, Lang lang) throws MessagingException {
        Multipart mmp = new MimeMultipart("alternative");

        MimeBodyPart textPart = new MimeBodyPart();
        String text = mail.getResource("i18n/"+template+".txt", lang);
        if (text != null) {
            textPart.setText(String.format(text, params), "utf-8");
            mmp.addBodyPart(textPart);
        }

        MimeBodyPart htmlPart = new MimeBodyPart();
        String html = mail.getResource("i18n/"+template+".html", lang);
        if (html != null) {
            htmlPart.setContent(String.format(html, params), "text/html; charset=utf-8");
            mmp.addBodyPart(htmlPart);
        }
        return mmp;
    }
}


