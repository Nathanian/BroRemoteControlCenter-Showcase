package com.bro.brorcc.utils;

import android.content.Context;
import android.util.Log;

import com.bro.brorcc.model.DeviceInfo;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/** Utility for reading device information from internal storage. */
public class DeviceInfoReader {
    private static final String TAG = "DeviceInfoReader";

    /** Reads deviceinfo.json from the app's files directory. */
    public static DeviceInfo read(Context context) {
        File info = new File(context.getFilesDir(), "deviceinfo.json");
        try (FileInputStream fis = new FileInputStream(info)) {
            long length = info.length();
            if (length <= 0) {
                Log.e(TAG, "deviceinfo.json is empty");
                return null;
            }

            byte[] data = new byte[(int) length];
            int offset = 0;
            int bytesRead;
            while (offset < data.length && (bytesRead = fis.read(data, offset, data.length - offset)) != -1) {
                offset += bytesRead;
            }
            if (offset != data.length) {
                Log.e(TAG, "Failed to fully read deviceinfo.json");
                return null;
            }

            JSONObject obj = new JSONObject(new String(data));
            return DeviceInfo.fromJson(obj);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read device info", e);
            return null;
        }
    }

    /** Convenience method building the topic from the DeviceInfo file. */
    public static String getMqttTopic(Context context) {
        DeviceInfo info = read(context);
        return info != null ? info.toTopic() : null;
    }
}
