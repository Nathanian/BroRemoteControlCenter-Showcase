package com.bro.brorcc.model;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import com.bro.brorcc.utils.JsonUtils;

/** Model representing configuration read from bot_config.json. */
public class BotConfig {
    public final String kunde;
    public final String modell;
    public final String androidVersion;
    public final String seriennummer;

    public BotConfig(String kunde, String modell, String androidVersion, String seriennummer) {
        this.kunde = kunde;
        this.modell = modell;
        this.androidVersion = androidVersion;
        this.seriennummer = seriennummer;
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        s = s.replace('/', '-');
        s = s.replace(' ', '_');
        return s;
        }

    @Nullable
    public static BotConfig fromJson(JSONObject obj) {
        String kunde = clean(JsonUtils.optString(obj, "kunde"));
        String modell = clean(JsonUtils.optString(obj, "modell"));
        String androidVersion = clean(JsonUtils.optString(obj, "androidVersion"));
        String seriennummer = clean(JsonUtils.optString(obj, "seriennummer"));
        if (kunde == null || modell == null || androidVersion == null || seriennummer == null) {
            return null;
        }
        return new BotConfig(kunde, modell, androidVersion, seriennummer);
    }

    /** Build MQTT topic representing this configuration. */
    public String toTopic() {
        return clean(kunde) + "/" + clean(modell) + "/" + clean(androidVersion) + "/" + clean(seriennummer);
    }
}

