package com.bro.brorcc.ui;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.service.ServiceController;
import com.bro.brorcc.service.AlarmRestartHelper;
import com.bro.brorcc.service.WatchdogManager;
import com.bro.brorcc.service.HealthConfig;
import com.bro.brorcc.mqtt.MqttClientManager;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;

import com.bro.brorcc.R;

public class DiagnosticsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_diagnostics);

        Button btn = findViewById(R.id.btnRequestIgnoreBattery);
        btn.setOnClickListener(v -> {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                // WHY: Without this exemption, Android may kill the background RemoteControlService
                // required for maintaining remote control connectivity.
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        TextView svc = findViewById(R.id.txtServiceState);
        boolean running = ServiceController.isRunning(this);
        svc.setText("Service: " + (running ? "running" : "stopped"));

        TextView net = findViewById(R.id.txtNetworkState);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        boolean hasNet = false;
        if (cm != null) {
            Network n = cm.getActiveNetwork();
            if (n != null) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                hasNet = nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }
        net.setText("Network: " + (hasNet ? "connected" : "disconnected"));

        TextView alarm = findViewById(R.id.txtPendingAlarm);
        long alarmAt = AlarmRestartHelper.getNextTriggerAt(this);
        if (alarmAt > 0) {
            long eta = alarmAt - SystemClock.elapsedRealtime();
            alarm.setText("Restart alarm in " + eta / 1000 + "s");
        } else {
            alarm.setText("Restart alarm: none");
        }

        TextView job = findViewById(R.id.txtNextJob);
        long jobAt = WatchdogManager.getNextTriggerAt(this);
        if (jobAt > 0) {
            long eta = jobAt - SystemClock.elapsedRealtime();
            job.setText("Watchdog job in " + eta / 1000 + "s");
        } else {
            job.setText("Watchdog job: none");
        }

        TextView hb = findViewById(R.id.txtHeartbeat);
        long ts = DiagLog.getLastHeartbeat();
        if (ts > 0) {
            String formatted = DateFormat.getDateTimeInstance().format(new Date(ts));
            hb.setText("Last heartbeat: " + formatted);
        } else {
            hb.setText("Last heartbeat: none");
        }

        TextView logView = findViewById(R.id.txtLogs);
        List<String> logs = DiagLog.getRecentLogs(50);
        StringBuilder sb = new StringBuilder();
        for (String line : logs) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        logView.setText(sb.toString());

        TextView cfg = findViewById(R.id.txtHealthConfig);
        cfg.setText("Ping=" + HealthConfig.PING_INTERVAL_MS/1000 + "s PongTimeout=" +
                HealthConfig.PONG_TIMEOUT_MS/1000 + "s Window=" +
                HealthConfig.HEALTH_WINDOW_MS/1000 + "s ProbeTimeout=" +
                HealthConfig.HEALTH_PROBE_TIMEOUT_MS/1000 + "s");

        TextView backoff = findViewById(R.id.txtBackoff);
        MqttClientManager mgr = MqttClientManager.getInstance(this);
        backoff.setText("MQTT backoff=" + mgr.getCurrentBackoffMs()/1000 + "s");
    }
}
