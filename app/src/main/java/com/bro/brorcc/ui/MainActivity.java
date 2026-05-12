package com.bro.brorcc.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.bro.brorcc.R;
import com.bro.brorcc.databinding.ActivityMainBinding;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.AlarmRestartHelper;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.service.ServiceController;
import com.bro.brorcc.service.WatchdogManager;
import com.bro.brorcc.utils.BotConfigReader;
import com.bro.brorcc.utils.ExactAlarmPermissionHelper;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.bro.brorcc.utils.Constants;

/**
 * Mission-control dashboard activity.
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_NOTIFICATIONS_PERMISSION = 1;
    private ActivityMainBinding binding;
    private TextView serviceStatusView;
    private TextView mqttStatusView;
    private TextView tunnelStatusView;
    private TextView adbStatusView;
    @Nullable private TextView deviceIdentifierView;
    private CompoundButton masterServiceToggle;
    private CompoundButton.OnCheckedChangeListener serviceToggleListener;
    private DashboardLogViewModel logViewModel;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRunnable = new Runnable() {
        @Override
        public void run() {
            updateServiceStatus();
            updateAdbStatus();
            statusHandler.postDelayed(this, 2000);
        }
    };
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Nullable private String cachedLoginTopic;
    @Nullable private Boolean lastServiceRunning;
    @Nullable private Boolean lastMqttConnected;
    @Nullable private TunnelViewModel.TunnelState lastTunnelState;
    @Nullable private Boolean lastAdbConnected;
    private boolean deviceIdentifierLogged;
    @Nullable private TabLayoutMediator tabLayoutMediator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        logViewModel = new ViewModelProvider(this).get(DashboardLogViewModel.class);

        deviceIdentifierView = binding.deviceIdentifier;
        if (deviceIdentifierView != null) {
            deviceIdentifierView.setText(R.string.dashboard_device_loading);
        }

        serviceStatusView = binding.textServiceStatus;
        mqttStatusView = binding.textMqttStatus;
        tunnelStatusView = binding.textTunnelStatus;
        adbStatusView = binding.textAdbStatus;

        masterServiceToggle = binding.masterServiceToggle;

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        if (tabLayout != null && viewPager != null) {
            viewPager.setAdapter(new DashboardPagerAdapter(this));
            tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                switch (position) {
                    case 0:
                        tab.setText(R.string.dashboard_tab_events);
                        break;
                    case 1:
                        tab.setText(R.string.dashboard_tab_tunnel);
                        break;
                    case 2:
                    default:
                        tab.setText(R.string.dashboard_tab_mqtt);
                        break;
                }
            });
            tabLayoutMediator.attach();
        }

        setStatus(serviceStatusView, getString(R.string.status_unknown), R.color.status_neutral_grey);
        setStatus(mqttStatusView, getString(R.string.status_unknown), R.color.status_neutral_grey);
        setStatus(tunnelStatusView, getString(R.string.status_unknown), R.color.status_neutral_grey);
        setStatus(adbStatusView, getString(R.string.status_unknown), R.color.status_neutral_grey);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATIONS_PERMISSION);
            }
        }

        startRemoteControlService();

        binding.btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        binding.btnDiagnostics.setVisibility(View.VISIBLE);
        binding.btnDiagnostics.setOnClickListener(v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)));

        serviceToggleListener = (button, isChecked) -> {
            if (isChecked) {
                logViewModel.appendEvent(getString(R.string.event_service_start_requested));
                startRemoteControlService();
            } else {
                logViewModel.appendEvent(getString(R.string.event_service_stop_requested));
                RemoteControlService.setUserInitiatedStop(this, true);
                MqttClientManager.getInstance(this).publishOffline();
                AlarmRestartHelper.cancel(this);
                ServiceController.stop(this);
            }
            updateServiceStatus();
        };
        masterServiceToggle.setOnCheckedChangeListener(serviceToggleListener);

        binding.btnMinimize.setOnClickListener(v -> moveTaskToBack(true));
        binding.btnFinish.setOnClickListener(v -> finish());

        final MqttClientManager mqttManager = MqttClientManager.getInstance(this);
        mqttManager.getEffectiveConnected().observe(this, connected -> {
            boolean isConnected = Boolean.TRUE.equals(connected);
            setStatus(mqttStatusView,
                    getString(isConnected ? R.string.status_connected : R.string.status_disconnected),
                    isConnected ? R.color.status_ok_green : R.color.status_error_red);
            if (lastMqttConnected == null || lastMqttConnected != isConnected) {
                logViewModel.appendEvent(getString(isConnected
                        ? R.string.event_mqtt_connected
                        : R.string.event_mqtt_disconnected));
                lastMqttConnected = isConnected;
            }
        });
        mqttManager.getFailure().observe(this, failure -> {
            if (failure != null && !failure.isEmpty()) {
                logViewModel.appendEvent(getString(R.string.event_mqtt_failure, failure));
            }
        });

        TunnelViewModel tunnelViewModel = TunnelViewModel.getInstance(getApplication());
        tunnelViewModel.getTunnelState().observe(this, state -> {
            if (state == null) {
                return;
            }
            switch (state) {
                case RUNNING:
                    setStatus(tunnelStatusView, getString(R.string.status_open), R.color.status_ok_green);
                    break;
                case CONNECTING:
                    setStatus(tunnelStatusView, getString(R.string.status_connecting), R.color.status_warn_yellow);
                    break;
                case FAILED:
                    setStatus(tunnelStatusView, getString(R.string.status_failed), R.color.status_error_red);
                    break;
                default:
                    setStatus(tunnelStatusView, getString(R.string.status_closed), R.color.status_error_red);
                    break;
            }
            if (lastTunnelState != state) {
                logViewModel.appendEvent(getString(R.string.event_tunnel_state, state.name()));
                lastTunnelState = state;
            }

            if (state == TunnelViewModel.TunnelState.RUNNING) {
                updateAdbStatus();
            } else {
                if (lastAdbConnected == null || lastAdbConnected) {
                    logViewModel.appendEvent(getString(R.string.event_adb_disconnected));
                }
                lastAdbConnected = Boolean.FALSE;
                setStatus(adbStatusView, getString(R.string.status_disconnected), R.color.status_error_red);
            }
        });

        resolveDeviceIdentifier();
        updateServiceStatus();
        updateAdbStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusHandler.post(statusRunnable);

        publishLogin();
        resolveDeviceIdentifier();
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusHandler.removeCallbacks(statusRunnable);
    }

    private void startRemoteControlService() {
        if (!ServiceController.isRunning(this)) {
            RemoteControlService.setUserInitiatedStop(this, false);
            ExactAlarmPermissionHelper.requestExactAlarmPermission(this);
            ServiceController.start(this);
            MqttClientManager.getInstance(this).forceReconnect();
            WatchdogManager watchdog = new WatchdogManager(this);
            watchdog.schedulePeriodic();
            watchdog.scheduleAlarm();
            watchdog.scheduleOneShot();
            publishLogin();
        }
    }

    private void updateServiceStatus() {
        boolean running = ServiceController.isRunning(this);
        masterServiceToggle.setOnCheckedChangeListener(null);
        masterServiceToggle.setChecked(running);
        masterServiceToggle.setOnCheckedChangeListener(serviceToggleListener);
        setStatus(serviceStatusView,
                getString(running ? R.string.status_running : R.string.status_stopped),
                running ? R.color.status_ok_green : R.color.status_error_red);
        if (lastServiceRunning == null || lastServiceRunning != running) {
            logViewModel.appendEvent(getString(running
                    ? R.string.event_service_started
                    : R.string.event_service_stopped));
            lastServiceRunning = running;
        }
    }

    private void updateAdbStatus() {
        executor.execute(() -> {
            boolean connected;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", 5555), 200);
                connected = true;
            } catch (IOException e) {
                connected = false;
            }
            boolean finalConnected = connected;
            runOnUiThread(() -> {
                setStatus(adbStatusView,
                        getString(finalConnected ? R.string.status_connected : R.string.status_disconnected),
                        finalConnected ? R.color.status_ok_green : R.color.status_error_red);
                if (lastAdbConnected == null || lastAdbConnected != finalConnected) {
                    logViewModel.appendEvent(getString(finalConnected
                            ? R.string.event_adb_connected
                            : R.string.event_adb_disconnected));
                    lastAdbConnected = finalConnected;
                }
            });
        });
    }

    private void publishLogin() {
        if (cachedLoginTopic != null) {
            MqttClientManager.getInstance(this).publish(Constants.MQTT_LOGIN_TOPIC, cachedLoginTopic);
            updateDeviceIdentifierView(cachedLoginTopic, true);
            return;
        }
        executor.execute(() -> {
            try {
                String loginTopic = BotConfigReader.getMqttTopic(this);
                if (loginTopic != null && !loginTopic.isEmpty()) {
                    cachedLoginTopic = loginTopic;
                    runOnUiThread(() -> {
                        updateDeviceIdentifierView(loginTopic, true);
                        MqttClientManager.getInstance(this).publish(Constants.MQTT_LOGIN_TOPIC, loginTopic);
                    });
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void resolveDeviceIdentifier() {
        if (deviceIdentifierView == null) {
            return;
        }
        executor.execute(() -> {
            try {
                String topic = BotConfigReader.getMqttTopic(this);
                if (topic != null && !topic.isEmpty()) {
                    cachedLoginTopic = topic;
                    runOnUiThread(() -> updateDeviceIdentifierView(topic, true));
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void updateDeviceIdentifierView(@Nullable String identifier, boolean logEvent) {
        if (deviceIdentifierView == null) {
            return;
        }
        if (identifier == null || identifier.isEmpty()) {
            deviceIdentifierView.setText(R.string.dashboard_device_loading);
            return;
        }
        deviceIdentifierView.setText(identifier);
        if (logEvent && !deviceIdentifierLogged) {
            logViewModel.appendEvent(getString(R.string.event_device_identifier_loaded, identifier));
            deviceIdentifierLogged = true;
        }
    }

    private void setStatus(@Nullable TextView view, String text, @ColorRes int colorRes) {
        if (view == null) return;
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        statusHandler.removeCallbacks(statusRunnable);
        executor.shutdownNow();
        if (tabLayoutMediator != null) {
            tabLayoutMediator.detach();
        }
    }
}
