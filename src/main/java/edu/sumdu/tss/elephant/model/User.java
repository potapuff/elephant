package edu.sumdu.tss.elephant.model;

import edu.sumdu.tss.elephant.helper.UserRole;
import edu.sumdu.tss.elephant.helper.exception.HttpError500;
import edu.sumdu.tss.elephant.helper.utils.StringUtils;
import lombok.Data;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Data
public class User {

    public static final int API_KEY_SIZE = 15;
    public static final int USERNAME_SIZE = 10;
    public static final int DB_PASSWORD_SIZE = 10;
    private Long id;
    private String login;
    private String password;
    private String username; //database user name
    private String dbPassword; //database password
    private Long role;
    private String privateKey;
    private String publicKey;
    private String token;
    private String language;


    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = crypt(password);
    }

    public UserRole role() {
        return UserRole.byValue(role);
    }

    public String crypt(String source) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-384");
        } catch (NoSuchAlgorithmException e) {
            throw new HttpError500("Fail crypt user password", e);
        }
        md.update(login.getBytes());
        byte[] bytes = md.digest();
        StringBuilder sb = new StringBuilder();
        for(int i=0; i< bytes.length ;i++)
        {
            sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public void resetToken() {
        this.token = StringUtils.randomAlphaString(User.API_KEY_SIZE);
    }
}
