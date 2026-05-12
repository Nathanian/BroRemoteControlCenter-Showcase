package com.bro.brorcc.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarm receiver that triggers health checks as a fallback. */
public class WatchdogAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WatchdogManager watchdog = new WatchdogManager(context);
        watchdog.ensureServiceRunning();
        watchdog.scheduleOneShot(); // sofortige Job-Prüfung zusätzlich
        watchdog.scheduleAlarm();
    }
}
