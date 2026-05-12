package com.bro.brorcc.service;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.mqtt.MqttConnectionListener;
import com.bro.brorcc.mqtt.MqttMessageHandler;
import com.bro.brorcc.utils.JsonUtils;
import com.bro.brorcc.utils.Constants;
import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.utils.BotConfigReader;
import com.bro.brorcc.utils.ConfigGuard;
import com.bro.brorcc.model.BotConfig;
import com.bro.brorcc.utils.NetworkMonitor;
import com.bro.brorcc.utils.AdbUtils;
import com.bro.brorcc.R;

import com.bro.brorcc.commands.CommandHandler;
import com.bro.brorcc.commands.StartTunnelCommandHandler;
import com.bro.brorcc.commands.StopTunnelCommandHandler;
import com.bro.brorcc.commands.AdbRebootCommandHandler;
import com.bro.brorcc.commands.IpQueryCommandHandler;
import com.bro.brorcc.commands.RebootCommandHandler;
import com.bro.brorcc.commands.PingCommandHandler;
import com.bro.brorcc.commands.CheckAdbPortCommandHandler;

import com.bro.brorcc.utils.CommandResult;
import com.bro.brorcc.utils.ShellCommandRunner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Arrays;

/**
 * Background service maintaining the MQTT connection and reacting to
 * remote commands for tunnel control. It is started on boot and kept
 * alive by returning {@link Service#START_STICKY}.
 */
public class RemoteControlService extends Service implements MqttMessageHandler, NetworkMonitor.Listener, MqttConnectionListener {
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static final String CHANNEL_ID = "remote_control";
    private static final int NOTIF_ID = 1;
    private static volatile boolean userInitiatedStop = false;
    private static final String PREF_NAME = "remote_control_service";
    private static final String KEY_USER_STOP = "user_stop";
    private static final String ACTION_RETRY = "com.bro.brorcc.action.RETRY";

    private HandlerThread workerThread;
    private Handler backgroundHandler;
    private String cmdTopic;
    private String respTopic;
    private String healthTopic;
    private WifiManager.WifiLock wifiLock;
    private List<CommandHandler> commandHandlers;
    private HealthMonitor healthMonitor;
    private boolean configured = false;
    private final BroadcastReceiver healthReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean healthy = healthMonitor != null && healthMonitor.isHealthy();
            Intent result = new Intent(WatchdogManager.ACTION_HEALTH_RESULT);
            result.putExtra("isHealthy", healthy);
            LocalBroadcastManager.getInstance(context).sendBroadcast(result);
        }
    };

    public static void setUserInitiatedStop(Context ctx, boolean flag) {
        userInitiatedStop = flag;
        Context appCtx = ctx.getApplicationContext();
        appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_USER_STOP, flag).apply();
    }

    /** Returns {@code true} if the foreground service considers itself running. */
    public static boolean isRunning() {
        return started.get();
    }

    public static boolean wasUserInitiatedStop(Context ctx) {
        if (userInitiatedStop) return true;
        return ctx.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_STOP, false);
    }

    private void runOnWorker(Runnable task) {
        if (backgroundHandler == null) return;
        backgroundHandler.post(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                DiagLog.e("Worker failure", t);
                stopSelf();
            }
        });
    }

    private void withWakeLock(Runnable task) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BroRCC:rc");
            wl.acquire(10_000L);
        }
        try {
            task.run();
        } finally {
            if (wl != null && wl.isHeld()) {
                wl.release();
            }
        }
    }

    private Notification buildCrashNotification() {
        Intent retry = new Intent(this, RemoteControlService.class);
        retry.setAction(ACTION_RETRY);
        PendingIntent pi = PendingIntent.getService(this, 0, retry, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Stopped: Crash protection active")
                .setContentText("Service crashed repeatedly")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .addAction(new NotificationCompat.Action(0, "Retry", pi))
                .setOngoing(true)
                .build();
    }

    private Notification buildMissingConfigNotification() {
        String location = BotConfigReader.describeConfigLocation(this);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Configuration missing")
                .setContentText("Please place bot_config.json in " + location)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onCreate() {
        if (!started.compareAndSet(false, true)) {
            stopSelf();
            return;
        }
        super.onCreate();
        AlarmRestartHelper.cancel(this);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Gesundheits-Service",
                    NotificationManager.IMPORTANCE_MIN);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
        Intent ni = new Intent(this, com.bro.brorcc.ui.MainActivity.class);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(this, 0, ni,
                android.app.PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Remote control active")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            DiagLog.e("Notifications disabled; cannot run foreground service");
            started.set(false);
            stopSelf();
            return;
        }

        try {
            startForeground(NOTIF_ID, notification);
        } catch (ForegroundServiceStartNotAllowedException | SecurityException e) {
            DiagLog.e("Foreground start not allowed", e);
            started.set(false);
            stopSelf();
            return;
        }

        workerThread = new HandlerThread("rc-service");
        workerThread.start();
        backgroundHandler = new Handler(workerThread.getLooper());

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "bro-mqtt");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
        commandHandlers = Arrays.asList(
                new StartTunnelCommandHandler(),
                new StopTunnelCommandHandler(),
                new AdbRebootCommandHandler(),
                new PingCommandHandler(),
                new CheckAdbPortCommandHandler(),
                new IpQueryCommandHandler(),
                new RebootCommandHandler()
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RETRY.equals(intent.getAction())) {
            CrashLoopProtector.reset(this);
            setUserInitiatedStop(this, false);
        }
        if (CrashLoopProtector.tooMany(this)) {
            WatchdogManager.cancelAll(this);
            AlarmRestartHelper.cancel(this);
            setUserInitiatedStop(this, true);
            startForeground(NOTIF_ID, buildCrashNotification());
            configured = false;
            return START_NOT_STICKY;
        }
        CrashLoopProtector.noteStart(this);

        MqttClientManager mqtt = MqttClientManager.getInstance(this);

        BotConfig cfg = ConfigGuard.readOrNull(this);
        if (cfg == null) {
            configured = false;
            startForeground(NOTIF_ID, buildMissingConfigNotification());
            WatchdogManager.cancelAll(this);
            AlarmRestartHelper.cancel(this);
            return START_NOT_STICKY;
        }
        configured = true;
        String deviceTopic = cfg.toTopic();
        cmdTopic = deviceTopic + "/cmd";
        respTopic = deviceTopic + "/resp";
        healthTopic = "brobots/health/" + deviceTopic;
        setUserInitiatedStop(this, false);

        mqtt.addMessageHandler(this);
        mqtt.addConnectionListener(this);
        NetworkMonitor.addListener(this);

        LocalBroadcastManager.getInstance(this).registerReceiver(healthReceiver,
                new IntentFilter(WatchdogManager.ACTION_HEALTH_PROBE));

        if (healthMonitor == null) {
            healthMonitor = new HealthMonitor(backgroundHandler, new HealthMonitor.Callback() {
                @Override
                public boolean sendPing() {
                    MqttClientManager manager = MqttClientManager.getInstance(RemoteControlService.this);
                    if (healthTopic == null || !manager.isConnected()) {
                        DiagLog.d("Health ping skipped");
                        return false;
                    }
                    boolean started = manager.publish(healthTopic, getString(R.string.cmd_ping));
                    if (!started) {
                        manager.reportHealthOffline();
                    }
                    return started;
                }

                @Override
                public void requestReconnect() {
                    MqttClientManager manager = MqttClientManager.getInstance(RemoteControlService.this);
                    manager.reportHealthOffline();
                    withWakeLock(manager::forceReconnect);
                }
            }, HealthConfig.PING_INTERVAL_MS, HealthConfig.PONG_TIMEOUT_MS);
        }

        runOnWorker(() -> {
            mqtt.setMainTopic(cmdTopic);
            mqtt.setHealthTopic(healthTopic);
            if (mqtt.isConnected() && healthMonitor != null) {
                healthMonitor.start();
            }
        });
        WatchdogManager wd = new WatchdogManager(this);
        wd.schedulePeriodic();
        wd.scheduleAlarm();
        wd.scheduleOneShot();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        MqttClientManager mqtt = MqttClientManager.getInstance(this);
        if (configured && mqtt.isConnected()) {
            mqtt.publishOffline();
        }
        mqtt.removeMessageHandler(this);
        mqtt.removeConnectionListener(this);
        NetworkMonitor.removeListener(this);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(healthReceiver);
        if (healthMonitor != null) {
            healthMonitor.stop();
        }
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacksAndMessages(null);
        }
        if (workerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                workerThread.quitSafely();
            } else {
                workerThread.quit();
            }
            workerThread = null;
        }
        backgroundHandler = null;
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
        }
        started.set(false);
        if (!wasUserInitiatedStop(this) && configured) {
            AlarmRestartHelper.schedule(this, 1000L);
        }
        CrashLoopProtector.reset(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void handleMessage(String topic, String payload) {
        if (topic == null) {
            return;
        }
        if (healthTopic != null && healthTopic.equals(topic)
                && "pong".equalsIgnoreCase(payload.trim())) {
            if (healthMonitor != null) {
                healthMonitor.onPong();
            }
            // Health-Overlay: bei PONG sofort online setzen
            MqttClientManager.getInstance(RemoteControlService.this).reportHealthOnline();
            return;
        }
        // Nur Kommandos vom eigenen Command-Topic akzeptieren
        if (cmdTopic == null || !topic.equals(cmdTopic)) {
            return;
        }
        if (!topic.endsWith("/cmd")) {
            return;
        }

        String cmd;
        try {
            JSONObject obj = new JSONObject(payload);
            cmd = JsonUtils.optString(obj, "command");
        } catch (Exception e) {
            cmd = payload;
        }
        cmd = (cmd != null) ? cmd.trim() : null;

        if (cmd == null) {
            DiagLog.e("Invalid message: " + payload, null);
            return;
        }

        final String command = cmd;
        // Publish all command responses back to the caller on this topic.
        final String responseTopic = respTopic;
        if (respTopic == null) {
            DiagLog.w("Command received but respTopic is null (MQTT config not ready yet)");
        }
        runOnWorker(() -> {
            TunnelViewModel tunnel = TunnelViewModel.getInstance(getApplication());
            MqttClientManager mqtt = MqttClientManager.getInstance(RemoteControlService.this);
            for (CommandHandler handler : commandHandlers) {
                if (handler.canHandle(command, RemoteControlService.this)) {
                    handler.execute(RemoteControlService.this, tunnel, mqtt, responseTopic);
                    return;
                }
            }
            DiagLog.w("Unknown command: " + command);
            mqtt.publish(responseTopic, Constants.RESP_UNKNOWN_COMMAND);
        });
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        if (healthMonitor == null) return;
        if (connected && cmdTopic != null) {
            healthMonitor.start();
        } else {
            healthMonitor.stop();
        }
    }

    @Override
    public void onNetworkAvailable() {
        TunnelViewModel tunnel = TunnelViewModel.getInstance(getApplication());
        TunnelViewModel.TunnelState state = tunnel.getTunnelState().getValue();
        if (state == TunnelViewModel.TunnelState.FAILED) {
            tunnel.startTunnel();
        }
        // If no topic was set at startup (e.g. because external storage wasn't
        // ready yet) try again now that the network is available. This ensures
        // the MQTT connection subscribes to the command topic and sends the
        // initial login message once the configuration file becomes accessible.
        if (cmdTopic == null) {
            String deviceTopic = BotConfigReader.getMqttTopic(this);
            if (deviceTopic != null) {
                cmdTopic = deviceTopic + "/cmd";
                respTopic = deviceTopic + "/resp";
                healthTopic = "brobots/health/" + deviceTopic;
                if (cmdTopic != null) {
                    // optionaler Online-Marker
                    String loginTopic = BotConfigReader.getMqttTopic(this);
                    if (loginTopic != null) {
                        MqttClientManager.getInstance(this).publish(Constants.MQTT_LOGIN_TOPIC, loginTopic + " online");
                    }
                }
                MqttClientManager mqtt = MqttClientManager.getInstance(this);
                mqtt.setMainTopic(cmdTopic);
                mqtt.setHealthTopic(healthTopic);
                mqtt.publish(Constants.MQTT_LOGIN_TOPIC, "MQTT verbunden");
                mqtt.publish(Constants.MQTT_LOGIN_TOPIC, deviceTopic);
            }
        }
        if (healthMonitor != null && !healthMonitor.isHealthy()) {
            withWakeLock(() -> MqttClientManager.getInstance(this).forceReconnect());
        }
        AdbUtils.ensureAdbTcpEnabled();
    }

    @Override
    public void onNetworkLost() {
        MqttClientManager mqtt = MqttClientManager.getInstance(this);
        if (configured && mqtt.isConnected()) {
            mqtt.publishOffline();
        }
        TunnelViewModel tunnel = TunnelViewModel.getInstance(getApplication());
        TunnelViewModel.TunnelState state = tunnel.getTunnelState().getValue();
        if (state == TunnelViewModel.TunnelState.RUNNING) {
            tunnel.stopTunnel();
        }
    }

    public String detectIp() {
        String[] ifaces = {"wlan0","wlan1","eth0","usb0","p2p0","rmnet0","rmnet_data0"};
        for (String iface : ifaces) {
            String ip = grepIp("ip addr show " + iface);
            if (ip != null) return ip;
            ip = getProp("dhcp." + iface + ".ipaddress");
            if (ip != null && !ip.isEmpty()) return ip;
        }
        String host = getProp("net.hostip");
        if (host != null && !host.isEmpty()) return host;
        return null;
    }

    private String grepIp(String cmd) {
        CommandResult result = ShellCommandRunner.run(cmd);
        if (result == null || result.exitCode != 0) return null;
        Pattern pat = Pattern.compile("inet\\s+(\\d+\\.\\d+\\.\\d+\\.\\d+)");
        String match = null;
        for (String line : result.stdout.split("\n")) {
            Matcher m = pat.matcher(line);
            if (match == null && m.find() && !m.group(1).startsWith("127.")) {
                match = m.group(1);
            }
        }
        return match;
    }

    public String getProp(String key) {
        CommandResult v = ShellCommandRunner.run(new String[]{"getprop", key});
        return (v == null || v.exitCode != 0) ? null : v.stdout.trim();
    }

}
