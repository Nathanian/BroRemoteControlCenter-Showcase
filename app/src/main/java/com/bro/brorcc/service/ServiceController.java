package com.bro.brorcc.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.app.NotificationCompat;

import com.bro.brorcc.utils.BotConfigReader;
import com.bro.brorcc.utils.ServiceUtils;
import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.utils.ConfigGuard;

import java.util.concurrent.atomic.AtomicBoolean;

/** Utility class to interact with the {@link RemoteControlService}. */
public final class ServiceController {
    private ServiceController() {
        // Utility class
    }

    private static final AtomicBoolean starting = new AtomicBoolean(false);

    /** Start the {@link RemoteControlService}. */
    public static void start(Context context) {
        if (RemoteControlService.wasUserInitiatedStop(context)) {
            DiagLog.d("User stopped service; not starting");
            return;
        }
        if (!ConfigGuard.isConfigured(context)) {
            showMissingConfigNotification(context);
            return;
        }
        if (isRunning(context)) {
            DiagLog.d("Service already running");
            return;
        }
        if (starting.get()) {
            DiagLog.d("Service start in progress");
            return;
        }
        starting.set(true);
        try {
            ServiceUtils.startForegroundServiceCompat(
                    context, new Intent(context, RemoteControlService.class));
        } finally {
            starting.set(false);
        }
    }

    /** Stop the {@link RemoteControlService}. */
    public static void stop(Context context) {
        starting.set(false);
        context.stopService(new Intent(context, RemoteControlService.class));
    }

    /** Return true if {@link RemoteControlService} is running. */
    public static boolean isRunning(Context context) {
        if (RemoteControlService.isRunning()) {
            return true;
        }
        return ServiceUtils.isServiceRunning(context, RemoteControlService.class);
    }

    /** Send a heartbeat probe to {@link RemoteControlService}. */
    public static void poke(Context context) {
        LocalBroadcastManager.getInstance(context)
                .sendBroadcast(new Intent(WatchdogManager.ACTION_HEALTH_PROBE));
    }

    static void showMissingConfigNotification(Context context) {
        Context appCtx = context.getApplicationContext();
        NotificationManager nm = (NotificationManager) appCtx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String channelId = "remote_control";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "Remote Control", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification notification = new NotificationCompat.Builder(appCtx, channelId)
                .setSmallIcon(com.bro.brorcc.R.drawable.ic_launcher_foreground)
                .setContentTitle("Missing configuration")
                .setContentText("Please place bot_config.json in "
                        + BotConfigReader.describeConfigLocation(appCtx))
                .setAutoCancel(true)
                .build();
        nm.notify(100, notification);
    }
}
