/*
package com.bro.brorcc.utils;

import android.content.Context;
import androidx.annotation.Nullable;

import com.bro.brorcc.model.BotConfig;
import com.bro.brorcc.utils.BotConfigReader;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

*/
/** Utility to check and read bot configuration file. *//*

public final class ConfigGuard {
    private ConfigGuard() { }

    @Nullable
    public static BotConfig readOrNull(Context context) {
        try {
            File file = BotConfigReader.getConfigFile(context);
            if (file == null) {
                return null;
            }
            if (!file.exists()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject obj = new JSONObject(sb.toString());
            return BotConfig.fromJson(obj);
        } catch (Exception e) {
            DiagLog.e("Error reading config", e);
            return null;
        }
    }

    public static boolean isConfigured(Context context) {
        return readOrNull(context) != null;
    }
}

*/

package com.bro.brorcc.utils;

import android.content.Context;
import androidx.annotation.Nullable;
import com.bro.brorcc.model.BotConfig;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public final class ConfigGuard {
    private ConfigGuard() { }

    @Nullable
    public static BotConfig readOrNull(Context context) {
        try {
            // VERBESSERT: Nutze den Scoped-Storage Pfad statt /sdcard
            File dir = context.getExternalFilesDir(null);
            if (dir == null) return null;

            File file = new File(new File(dir, "BotConfig"), "bot_config.json");

            if (!file.exists()) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject obj = new JSONObject(sb.toString());
            return BotConfig.fromJson(obj);
        } catch (Exception e) {
            // Nur loggen, wenn es kein reiner "File not found" Fehler ist
            DiagLog.e("Error reading config in ConfigGuard", e);
            return null;
        }
    }

    public static boolean isConfigured(Context context) {
        return readOrNull(context) != null;
    }
}
