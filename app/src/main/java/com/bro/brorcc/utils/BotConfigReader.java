package com.bro.brorcc.utils;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

/** Utility for reading the BotConfig and building the MQTT topic. */
public class BotConfigReader {
    private static final String CONFIG_DIR_NAME = "BotConfig";
    private static final String CONFIG_FILE_NAME = "bot_config.json";
    private static String cachedTopic;

    private BotConfigReader() {
        // utility class
    }

    /**
     * Copies the bundled bot_config.json from the assets directory into the
     * app-specific BotConfig directory, overwriting any existing copy.
     */
    public static void installConfigFromAssets(Context context) {
        File configFile = getConfigFile(context);
        if (configFile == null) {
            DiagLog.e("Unable to resolve external config path");
            return;
        }
        File parent = configFile.getParentFile();
        if (parent != null) {
            if (parent.exists() && !parent.isDirectory()) {
                DiagLog.e("Config path is not a directory: " + parent.getAbsolutePath());
                return;
            }
            if (!parent.exists() && !parent.mkdirs()) {
                DiagLog.e("Failed to create config directory: " + parent.getAbsolutePath());
                return;
            }
        }
        try (InputStream in = context.getAssets().open(CONFIG_FILE_NAME);
                FileOutputStream out = new FileOutputStream(configFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            cachedTopic = null;
            DiagLog.i("Installed bot_config.json from assets to "
                    + configFile.getAbsolutePath());
        } catch (IOException | SecurityException e) {
            DiagLog.e("Failed to install config from assets to "
                    + configFile.getAbsolutePath(), e);
        }
    }

    /**
     * Reads bot_config.json from the app-specific BotConfig directory and builds
     * the MQTT topic in the form kunde/modell/androidVersion/seriennummer.
     */
    public static synchronized String getMqttTopic(Context context) {
        if (cachedTopic != null) {
            return cachedTopic;
        }
        try {
            File file = getConfigFile(context);
            if (file == null) {
                DiagLog.e("Unable to resolve external config path");
            } else if (file.exists()) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                JSONObject obj = new JSONObject(sb.toString());
                String kunde = JsonUtils.optString(obj, "kunde");
                String modell = JsonUtils.optString(obj, "modell");
                String androidVersion = JsonUtils.optString(obj, "androidVersion");
                String seriennummer = JsonUtils.optString(obj, "seriennummer");
                if (kunde != null && modell != null && androidVersion != null && seriennummer != null) {
                    cachedTopic = kunde + "/" + modell + "/" + androidVersion + "/" + seriennummer;
                }
            } else {
                DiagLog.e("Config file does not exist at " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            DiagLog.e("Error reading config", e);
        }
        if (cachedTopic == null) {
            String fallback = DeviceInfoReader.getMqttTopic(context);
            if (fallback != null) {
                cachedTopic = fallback;
            }
        }
        return cachedTopic;
    }

    /** Returns the configuration file within the app-specific external storage directory. */
    public static File getConfigFile(Context context) {
        File appSpecificDir = context.getExternalFilesDir(null);
        if (appSpecificDir == null) {
            return null;
        }
        File configDir = new File(appSpecificDir, CONFIG_DIR_NAME);
        return new File(configDir, CONFIG_FILE_NAME);
    }

    /**
     * Returns a human-readable description of where the configuration file should be located.
     */
    public static String describeConfigLocation(Context context) {
        File file = getConfigFile(context);
        if (file == null) {
            return "app storage/BotConfig/bot_config.json";
        }
        return file.getAbsolutePath();
    }
}
