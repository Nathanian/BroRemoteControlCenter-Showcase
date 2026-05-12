package com.bro.brorcc.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bro.brorcc.utils.BotConfigReader;

/** Restarts {@link RemoteControlService} after the app package is replaced. */
public class UpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "UpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }

        String topic = BotConfigReader.getMqttTopic(context.getApplicationContext());
        if (topic == null || topic.isEmpty()) {
            Log.w(TAG, "Topic missing after update");
            return;
        }
        ServiceController.start(context);
    }
}
