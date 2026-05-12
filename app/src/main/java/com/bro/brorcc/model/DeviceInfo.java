package com.bro.brorcc.model;

import org.json.JSONObject;

/** Model holding device information parsed from deviceinfo.json. */
public class DeviceInfo {
    public final String kunde;
    public final String modell;
    public final String androidVersion;
    public final String seriennummer;

    public DeviceInfo(String kunde, String modell, String androidVersion, String seriennummer) {
        this.kunde = kunde;
        this.modell = modell;
        this.androidVersion = androidVersion;
        this.seriennummer = seriennummer;
    }

    private static String clean(String s) {
        if (s == null) return null;
        s = s.trim();
        // verbiete '/' im Topic: ersetze z. B. durch '-'
        s = s.replace('/', '-');
        // Leerzeichen optional in '_' umwandeln für MQTT-Topics
        s = s.replace(' ', '_');
        return s;
    }

    public static DeviceInfo fromJson(JSONObject obj) {
        String kunde = clean(obj.optString("kunde", null));
        String modell = clean(obj.optString("modell", null));
        String androidVersion = clean(obj.optString("androidVersion", null));
        String seriennummer = clean(obj.optString("seriennummer", null));
        if (kunde == null || modell == null || androidVersion == null || seriennummer == null) {
            return null;
        }
        return new DeviceInfo(kunde, modell, androidVersion, seriennummer);
    }

    /** Returns the MQTT topic representing this device. */
    public String toTopic() {
        return clean(kunde) + "/" + clean(modell) + "/" + clean(androidVersion) + "/" + clean(seriennummer);
    }
}
