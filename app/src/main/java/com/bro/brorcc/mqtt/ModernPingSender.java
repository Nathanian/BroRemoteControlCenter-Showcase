package com.bro.brorcc.mqtt;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import com.bro.brorcc.utils.DiagLog;

import org.eclipse.paho.client.mqttv3.MqttPingSender;
import org.eclipse.paho.client.mqttv3.internal.ClientComms;

public class ModernPingSender implements MqttPingSender {
    private static final String PING_ACTION = "com.bro.brorcc.MQTT_PING";
    private ClientComms comms;
    private final Context context;
    private PendingIntent pendingIntent;
    private final PingReceiver receiver;

    public ModernPingSender(Context context) {
        this.context = context;
        this.receiver = new PingReceiver();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void init(ClientComms comms) {
        this.comms = comms;
        // Android 7 bis 12 Kompatibilität
        context.registerReceiver(receiver, new IntentFilter(PING_ACTION));
    }

    @Override
    public void start() {
        schedule(comms.getKeepAlive());
    }

    @Override
    public void stop() {
        if (pendingIntent != null) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pendingIntent);
        }
        try {
            context.unregisterReceiver(receiver);
        } catch (Exception ignored) {}
    }

    @Override
    public void schedule(long delayInMilliseconds) {
        long nextAlarmInMilliseconds = SystemClock.elapsedRealtime() + delayInMilliseconds;
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(PING_ACTION);

        // --- KOMPATIBILITÄTS-CHECK FÜR FLAGS ---
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Erst ab Android 6 (API 23) verfügbar, zwingend ab Android 12
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        pendingIntent = PendingIntent.getBroadcast(context, 0, i, flags);

        // --- KOMPATIBILITÄTS-CHECK FÜR ALARME ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Prüfen ob exakte Alarme erlaubt sind
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6 bis 11
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
        } else {
            // Android 4.4 bis 5.1 (falls relevant, hier für Android 7)
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextAlarmInMilliseconds, pendingIntent);
        }
    }

    private class PingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (comms != null) comms.checkForActivity();
        }
    }
}