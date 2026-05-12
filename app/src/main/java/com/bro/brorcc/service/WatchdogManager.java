package com.bro.brorcc.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.utils.ExactAlarmPermissionHelper;
import com.bro.brorcc.service.HealthConfig;


import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages scheduling and probing of {@link RemoteControlService} health checks.
 * Instance based to allow dependency injection in tests.
 */
public class WatchdogManager {
    public static final String ACTION_HEALTH_PROBE = "com.bro.brorcc.ACTION_MQTT_HEALTH_PROBE";
    public static final String ACTION_HEALTH_RESULT = "com.bro.brorcc.ACTION_MQTT_HEALTH_RESULT";

    private static final int JOB_ID_PERIODIC = 1000;
    private static final int JOB_ID_ONESHOT = 1001;
    private static final long INTERVAL_MS = 15 * 60 * 1000L;
    private static final float RESCHEDULE_THRESHOLD = 0.25f; // 25% of interval

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private static final String PREF_NAME = "watchdog_mgr";
    private static final String KEY_NEXT_TRIGGER = "next_trigger";

    private final Context context;
    private final ServiceStarter serviceStarter;

    /** Interface to allow injecting service starting behaviour for tests. */
    public interface ServiceStarter {
        void start(Context context);
    }

    public WatchdogManager(Context context) {
        this(context, ServiceController::start);
    }

    public WatchdogManager(Context context, ServiceStarter serviceStarter) {
        this.context = context.getApplicationContext();
        this.serviceStarter = serviceStarter;
    }

    private boolean shouldSchedule(long trigger) {
        long existing = getNextTriggerAt(context);
        long now = SystemClock.elapsedRealtime();
        if (existing > now && existing - now < INTERVAL_MS * RESCHEDULE_THRESHOLD) {
            DiagLog.d("Skip scheduling; next trigger in " + (existing - now) + "ms");
            return false;
        }
        return true;
    }

    /** Schedule the periodic JobScheduler watchdog. */
    public void schedulePeriodic() {
        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID_PERIODIC,
                new ComponentName(context, JobHeartbeatService.class))
                .setPersisted(true)
                .setBackoffCriteria(TimeUnit.SECONDS.toMillis(5),
                        JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(INTERVAL_MS)
                .build();
        long trigger = SystemClock.elapsedRealtime() + INTERVAL_MS;
        if (shouldSchedule(trigger)) {
            js.schedule(job);
            saveNextTrigger(trigger);
        }
    }

    /** Schedule a one-shot watchdog job to run soon. */
    public void scheduleOneShot() {
        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID_ONESHOT,
                new ComponentName(context, JobHeartbeatService.class))
                .setMinimumLatency(0)
                .setOverrideDeadline(1000)
                .build();
        long trigger = SystemClock.elapsedRealtime();
        if (shouldSchedule(trigger)) {
            js.schedule(job);
            saveNextTrigger(trigger);
        }
    }

    /** Schedule the AlarmManager fallback watchdog after the default interval. */
    public void scheduleAlarm() {
        scheduleAlarm(INTERVAL_MS);
    }

    /** Schedule the AlarmManager fallback watchdog after the given delay. */
    public void scheduleAlarm(long delayMs) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, WatchdogAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long trigger = SystemClock.elapsedRealtime() + delayMs;
        if (shouldSchedule(trigger)) {
            boolean canScheduleExact = ExactAlarmPermissionHelper.canScheduleExactAlarms(context);
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    if (canScheduleExact) {
                        am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                    }
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                }
            } catch (SecurityException e) {
                DiagLog.e("Exact watchdog alarm denied; scheduling inexact alarm", e);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                }
            }
            saveNextTrigger(trigger);
        }
    }

    /** Probe service health via local broadcast asynchronously. */
    public CompletableFuture<Boolean> probeHealth() {
        final CompletableFuture<Boolean> future = new CompletableFuture<>();
        final LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!future.isDone()) {
                    boolean healthy = intent.getBooleanExtra("isHealthy", false);
                    future.complete(healthy);
                }
            }
        };
        lbm.registerReceiver(receiver, new IntentFilter(ACTION_HEALTH_RESULT));
        lbm.sendBroadcast(new Intent(ACTION_HEALTH_PROBE));

        ScheduledFuture<?> timeoutFuture = scheduler.schedule(() -> {
            if (!future.isDone()) {
                try {
                    future.complete(false);
                } catch (CancellationException ignored) {
                    // Future was cancelled; ignore
                }
            }
        }, HealthConfig.HEALTH_PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        future.whenComplete((r, t) -> {
            timeoutFuture.cancel(false);
            lbm.unregisterReceiver(receiver);
        });
        return future;
    }

    /** Ensure the service is running and healthy. */
    public CompletableFuture<Boolean> ensureServiceRunning() {
        if (RemoteControlService.wasUserInitiatedStop(context)) {
            return CompletableFuture.completedFuture(false);
        }
        return probeHealth().thenApply(healthy -> {
            if (!healthy && !RemoteControlService.wasUserInitiatedStop(context)) {
                if (com.bro.brorcc.utils.ConfigGuard.isConfigured(context)) {
                    serviceStarter.start(context);
                } else {
                    ServiceController.showMissingConfigNotification(context);
                }
            }
            return healthy;
        });
    }

    private void saveNextTrigger(long triggerAt) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_NEXT_TRIGGER, triggerAt).apply();
    }

    /**
     * Shut down the internal scheduler. This should be invoked when watchdog
     * functionality is disabled to clean up resources.
     */
    public static void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Return the {@code elapsedRealtime()} timestamp when the next watchdog
     * job or alarm is expected to fire, or {@code 0} if none scheduled.
     */
    public static long getNextTriggerAt(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_NEXT_TRIGGER, 0L);
    }

    /** Cancel all scheduled watchdog jobs and alarms. */
    public static void cancelAll(Context context) {
        Context appCtx = context.getApplicationContext();
        JobScheduler js = (JobScheduler) appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            js.cancel(JOB_ID_PERIODIC);
            js.cancel(JOB_ID_ONESHOT);
        }
        AlarmManager am = (AlarmManager) appCtx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            Intent intent = new Intent(appCtx, WatchdogAlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(appCtx, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        }
        appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY_NEXT_TRIGGER).apply();
    }
}

