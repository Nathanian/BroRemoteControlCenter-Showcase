package com.bro.brorcc.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import com.bro.brorcc.utils.ExactAlarmPermissionHelper;
import com.bro.brorcc.utils.DiagLog;

/**
 * Helper for scheduling process restarts via AlarmManager with
 * exponential backoff. The next delay is persisted in
 * {@link SharedPreferences} so that repeated crashes backoff up to 60s.
 */
public final class AlarmRestartHelper {
    private static final String PREF_NAME = "alarm_restart";
    private static final String KEY_DELAY = "delay";
    /** Timestamp in elapsedRealtime() when the alarm is expected to fire. */
    private static final String KEY_TRIGGER_AT = "trigger_at";
    private static final long MAX_DELAY_MS = 60_000L;

    private AlarmRestartHelper() { }

    /**
     * Schedule {@link RemoteControlService} to restart after the current
     * backoff delay. Each invocation doubles the delay up to 60 seconds.
     *
     * @param context application context
     * @param initialDelayMs starting delay when no previous backoff is stored
     */
    public static void schedule(Context context, long initialDelayMs) {
        Context appCtx = context.getApplicationContext();
        SharedPreferences prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long delay = prefs.getLong(KEY_DELAY, initialDelayMs);

        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }

        Intent intent = new Intent(appCtx, RemoteControlService.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pi = PendingIntent.getForegroundService(appCtx, 0, intent, flags);
        } else {
            pi = PendingIntent.getService(appCtx, 0, intent, flags);
        }

        long triggerAt = SystemClock.elapsedRealtime() + delay;
        boolean canScheduleExact = ExactAlarmPermissionHelper.canScheduleExactAlarms(appCtx);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (canScheduleExact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
                }
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            DiagLog.e("Exact alarm scheduling failed; falling back to inexact alarm", e);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi);
            }
        }

        long next = Math.min(delay * 2, MAX_DELAY_MS);
        prefs.edit()
                .putLong(KEY_DELAY, next)
                .putLong(KEY_TRIGGER_AT, triggerAt)
                .apply();
    }

    /** Cancel any pending restart alarms and reset the backoff. */
    public static void cancel(Context context) {
        Context appCtx = context.getApplicationContext();
        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(appCtx, RemoteControlService.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            pi = PendingIntent.getForegroundService(appCtx, 0, intent, flags);
        } else {
            pi = PendingIntent.getService(appCtx, 0, intent, flags);
        }
        if (am != null) {
            am.cancel(pi);
        }
        appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_DELAY)
                .remove(KEY_TRIGGER_AT)
                .apply();
    }

    /**
     * Return the {@code elapsedRealtime()} timestamp when the restart alarm is
     * expected to fire, or {@code 0} if none is scheduled.
     */
    public static long getNextTriggerAt(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_TRIGGER_AT, 0L);
    }
}
