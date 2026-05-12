package com.bro.brorcc.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import com.bro.brorcc.utils.ExactAlarmPermissionHelper;

import com.bro.brorcc.utils.ConfigGuard;

/** Starts {@link RemoteControlService} after device boot. */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String ACTION_CONFIG_RETRY = "com.bro.brorcc.action.CONFIG_RETRY";
    private static final String PREF = "boot_retry";
    private static final String KEY_COUNT = "count";
    private static final int MAX_RETRIES = 6;
    private static final long RETRY_INTERVAL_MS = 5 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !ACTION_CONFIG_RETRY.equals(action)) {
            return;
        }

        if (ACTION_CONFIG_RETRY.equals(action)) {
            handleRetry(context);
            return;
        }

        if (!RemoteControlService.wasUserInitiatedStop(context)) {
            if (!ConfigGuard.isConfigured(context)) {
                Log.w(TAG, "Boot: config missing");
                ServiceController.showMissingConfigNotification(context);
                scheduleRetry(context, 0);
                return;
            }
            ServiceController.start(context);
        }
    }

    private void handleRetry(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        int count = prefs.getInt(KEY_COUNT, 0);
        if (ConfigGuard.isConfigured(context)) {
            ServiceController.start(context);
            prefs.edit().remove(KEY_COUNT).apply();
            return;
        }
        if (count < MAX_RETRIES) {
            count++;
            prefs.edit().putInt(KEY_COUNT, count).apply();
            scheduleRetry(context, RETRY_INTERVAL_MS);
        }
    }

    private void scheduleRetry(Context context, long delayMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent i = new Intent(context, BootReceiver.class).setAction(ACTION_CONFIG_RETRY);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long trigger = SystemClock.elapsedRealtime() + delayMs;
        boolean canScheduleExact = ExactAlarmPermissionHelper.canScheduleExactAlarms(context);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canScheduleExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                }
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Exact alarm denied; scheduling inexact retry", e);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
        }
    }
}
