package com.bro.brorcc;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.bro.brorcc.service.AlarmRestartHelper;
import com.bro.brorcc.service.ServiceController;
import com.bro.brorcc.service.WatchdogManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.BotConfigReader;
import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.utils.NetworkMonitor;

import java.util.concurrent.atomic.AtomicBoolean;

public class MqttApp extends Application {
    private NetworkMonitor.Listener networkListener;
    private Handler handler;
    private WatchdogManager watchdog;
    private static final AtomicBoolean startedOnce = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        DiagLog.init(this);
        BotConfigReader.installConfigFromAssets(this);
        DiagLog.i("Application started");
        handler = new Handler(Looper.getMainLooper());
        final Thread.UncaughtExceptionHandler systemHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            AlarmRestartHelper.schedule(getApplicationContext(), 1000L);
            if (systemHandler != null) {
                systemHandler.uncaughtException(t, e);
            }
        });
        watchdog = new WatchdogManager(this);
        if (!RemoteControlService.wasUserInitiatedStop(this)) {
            initIfTopicReady();
        }

        // Start monitoring network connectivity so components can repair
        // themselves when connectivity changes.
        NetworkMonitor.init(this);
        networkListener = new NetworkMonitor.Listener() {
            @Override
            public void onNetworkAvailable() {
                initIfTopicReady();
                if (startedOnce.get()) {
                    watchdog.ensureServiceRunning();
                }
            }

            @Override
            public void onNetworkLost() { /* no-op */ }
        };
        NetworkMonitor.addListener(networkListener);
    }

    private void initIfTopicReady() {
        if (RemoteControlService.wasUserInitiatedStop(this)) return;
        String topic = BotConfigReader.getMqttTopic(this);
        if (topic == null || topic.isEmpty()) {
            handler.postDelayed(this::initIfTopicReady, 2000);
            return;
        }
        if (!startedOnce.compareAndSet(false, true)) {
            DiagLog.d("Already started");
            return;
        }
        ServiceController.start(this);
        watchdog.schedulePeriodic();
        watchdog.scheduleAlarm();
        watchdog.scheduleOneShot();
    }

    @Override
    public void onTerminate() {
        NetworkMonitor.removeListener(networkListener);
        NetworkMonitor.shutdown();
        WatchdogManager.shutdown();
        super.onTerminate();
    }
}
