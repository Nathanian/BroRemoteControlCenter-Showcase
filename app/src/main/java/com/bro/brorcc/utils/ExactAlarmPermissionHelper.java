package com.bro.brorcc.utils;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import android.content.pm.ResolveInfo;

import java.util.List;

/** Utility helper for managing exact alarm permissions on Android 12+. */
public final class ExactAlarmPermissionHelper {
    private ExactAlarmPermissionHelper() { }

    /** Returns whether the app can schedule exact alarms on the current device. */
    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    /**
     * Opens the system settings screen allowing the user to grant the exact alarm permission.
     * This is only relevant on Android 12+ where the permission can be revoked by the user.
     */
    public static void requestExactAlarmPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (canScheduleExactAlarms(context)) {
            return;
        }
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> handlers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            DiagLog.e("Unable to request exact alarm permission", e);
        }
    }
}
