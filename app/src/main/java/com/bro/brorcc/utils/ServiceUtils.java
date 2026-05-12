package com.bro.brorcc.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Utility methods related to Android services. */
public class ServiceUtils {
    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if a process with the given PID appears to be alive. */
    public static boolean isProcessAlive(Context context, int pid) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        for (ActivityManager.RunningAppProcessInfo proc : manager.getRunningAppProcesses()) {
            if (proc.pid == pid) return true;
        }
        return false;
    }

    /**
     * Start the given service using startForegroundService on API 26+ and
     * startService on older versions.
     */
    public static void startForegroundServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
