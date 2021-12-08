package edu.sumdu.tss.elephant.helper;

import io.javalin.core.util.JavalinLogger;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Keys {

    public static final String[] PARAMS = {
            "DB.LOCAL_PATH", "DB.PORT", "DB.URL", "DB.NAME", "DB.USERNAME", "DB.OS_USER", "DB.HOST",
            "APP.URL", "APP.PORT",
            "EMAIL.HOST", "EMAIL.PORT", "EMAIL.USER", "EMAIL.FROM", "EMAIL.SSL",
            "DEFAULT_LANG", "ENV"
    };
    public static final String[] SECURED_PARAMS = {"DB.PASSWORD", "EMAIL.PASSWORD"};
    public static final String SESSION_CURRENT_USER_KEY = "currentUser";
    public static final String BREADCRUMB_KEY = "breadcrumb";
    public static final String DB_KEY = "database";
    public static final String MODEL_KEY = "model";
    public static final String ERROR_KEY = "error";
    public static final String INFO_KEY = "info";
    public static final String LANG_KEY = "lang";

    private static HashMap<String, String> keys = null;

    public static void loadParams(File properties) {
        Map<String, String> env = System.getenv();
        keys = new HashMap<>();
        try {
            var is = FileUtils.openInputStream(properties);
            var app_properties = new Properties();
            app_properties.load(is);
            for (String key : PARAMS) {
                String value = env.getOrDefault(key, app_properties.getProperty(key));
                if (value == null) {
                    throw new IllegalArgumentException(String.format("Property %s not found in %s and or system environment", key, properties.getPath()));
                }
                keys.put(key, value);
            }
            for (String key : SECURED_PARAMS) {
                String value = env.get(key);
                if (value == null) {
                    value = app_properties.getProperty(key);
                    if (value != null) {
                        JavalinLogger.warn(String.format("Property %s set in config file. It is insecure", key));
                    } else {
                        throw new IllegalArgumentException(String.format("Property %s not found in %s and or system environment (last - preferable)", key, properties.getPath()));
                    }
                    keys.put(key, value);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String get(String key) {
        if (keys == null) {
            throw new RuntimeException("Add path to config.property to your application on init");
        }

        if (!keys.containsKey(key)) {
            throw new RuntimeException(String.format("Unknown key %s in app properties", key));
        }

        String value = keys.get(key);
        if (value.isEmpty()) {
            throw new RuntimeException(String.format("No value for key %s", key));
        }
        return value;
    }

    public static boolean isProduction() {
        return Keys.get("ENV").equalsIgnoreCase("production");
    }

}
