package com.bro.brorcc.utils;

import org.json.JSONObject;

/** Simple helpers for JSON access. */
public class JsonUtils {
    public static String optString(JSONObject obj, String key) {
        return obj != null && obj.has(key) ? obj.optString(key, null) : null;
    }
}
