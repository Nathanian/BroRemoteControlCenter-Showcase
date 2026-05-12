package com.bro.brorcc.mqtt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadLocalRandom;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.bro.brorcc.utils.Constants;
import com.bro.brorcc.utils.BotConfigReader;
import com.bro.brorcc.utils.NetworkMonitor;
import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.utils.MainThread;

/**
 * Singleton managing a global MQTT connection.
 */
public class MqttClientManager implements NetworkMonitor.Listener {
    private static volatile MqttClientManager instance;
    private final String serverUri;
    private final String clientId;
    private final MutableLiveData<Boolean> connected = new MutableLiveData<>(false);
    private final MutableLiveData<String> failure = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> published = new MutableLiveData<>();
    private final MutableLiveData<java.util.List<String>> topicsLive = new MutableLiveData<>();

    // Health-Overlay: erlaubt, Transportstatus per Ping/Pong zu übersteuern
    private final MutableLiveData<Boolean> healthOverride = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> effectiveConnected = new MutableLiveData<>(false);

    private final Context context;
    private String mainTopic;
    private String customTopic;
    private String healthTopic;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int reconnectAttempts = 0;
    private long currentBackoffMs = 1000L;
    private final Set<MqttMessageHandler> messageHandlers = new CopyOnWriteArraySet<>();
    private final Set<MqttConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
    private org.eclipse.paho.client.mqttv3.MqttAsyncClient client;

    // ===== helpers =====
    private boolean isClientConnected() {
        return client != null && client.isConnected();
    }

    private void subscribeNow(String topic) {
        if (topic == null || topic.isEmpty() || !isClientConnected()) return;
        try { client.subscribe(topic, 0); } catch (MqttException e) { failure.postValue(e.getMessage()); }
    }

    private void unsubscribeNow(String topic) {
        if (topic == null || topic.isEmpty() || !isClientConnected()) return;
        try { client.unsubscribe(topic); } catch (MqttException ignored) {}
    }

    private void updateEffective() {
        Boolean t = connected.getValue();
        Boolean h = healthOverride.getValue();
        boolean eff = (h != null) ? h : (t != null && t);
        effectiveConnected.postValue(eff);
    }

    private MqttClientManager(Context ctx) {
        context = ctx.getApplicationContext();
        serverUri = "tcp://" + Constants.SSH_HOST + ":" + Constants.MQTT_PORT;
        SharedPreferences prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String cid = prefs.getString("clientId", null);
        if (cid == null) {
            cid = MqttClient.generateClientId();
            prefs.edit().putString("clientId", cid).apply();
        }
        clientId = cid;
        newClient();
        String savedTopic = prefs.getString("customTopic", null);
        if (savedTopic != null && !savedTopic.isEmpty()) {
            customTopic = savedTopic;
        }
        updateTopicsLive();
        NetworkMonitor.addListener(this);
        DiagLog.i("Initializing MQTT client for " + serverUri + " id=" + clientId);
        connect();
    }

  /*  private void newClient() {
        try {
            client = new MqttAsyncClient(serverUri, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            DiagLog.e("Failed to create MQTT client", e);
            failure.postValue(e.getMessage());
            client = null;
            return;
        }
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                DiagLog.i("MQTT connected (reconnect=" + reconnect + ")");
                reconnectAttempts = 0;
                currentBackoffMs = 1000L;
                connected.postValue(true);
                failure.postValue(null);
                notifyConnection(true);
                clearHealthOverride();
                updateEffective(); // <- transport changed

                subscribeNow(mainTopic);
                subscribeNow(customTopic);
                subscribeNow(healthTopic);

                String loginTopic = BotConfigReader.getMqttTopic(context);
                if (loginTopic != null) {
                    publish(Constants.MQTT_LOGIN_TOPIC, "MQTT verbunden");
                    publish(Constants.MQTT_LOGIN_TOPIC, loginTopic);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                DiagLog.w("MQTT connection lost" + (cause != null ? ": " + cause.getMessage() : ""));
                connected.postValue(false);
                notifyConnection(false);
                if (cause != null) {
                    failure.postValue(cause.getMessage());
                }
                updateEffective(); // <- transport changed
                scheduleReconnect();
            }

            @Override
            public void messageArrived(String topic, MqttMessage mqttMessage) {
                String payload = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
                DiagLog.d("MQTT message on " + topic + ": " + payload);
                message.postValue(payload);
                for (MqttMessageHandler h : messageHandlers) {
                    MainThread.dispatch(() -> h.handleMessage(topic, payload));
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) { }
        });
    }*/

    private void newClient() {
        try {
            // 1. We NO LONGER call context.startService.
            // The Java-based AsyncClient doesn't need the buggy Paho Service.

            // 2. Initialize the client with MemoryPersistence (safe for all Android versions)
            // and our ModernPingSender (to fix the Android 12 PendingIntent crash).
            client = new org.eclipse.paho.client.mqttv3.MqttAsyncClient(
                    serverUri,
                    clientId,
                    new org.eclipse.paho.client.mqttv3.persist.MemoryPersistence(),
                    new ModernPingSender(context)
            );

            // 3. Set the callback (Logic remains the same as your original)
            client.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    DiagLog.i("MQTT connected (reconnect=" + reconnect + ")");
                    reconnectAttempts = 0;
                    currentBackoffMs = 1000L;
                    connected.postValue(true);
                    failure.postValue(null);
                    notifyConnection(true);
                    clearHealthOverride();
                    updateEffective();

                    // Re-subscribe to topics
                    subscribeNow(mainTopic);
                    subscribeNow(customTopic);
                    subscribeNow(healthTopic);

                    String loginTopic = BotConfigReader.getMqttTopic(context);
                    if (loginTopic != null) {
                        publish(Constants.MQTT_LOGIN_TOPIC, "MQTT verbunden");
                        publish(Constants.MQTT_LOGIN_TOPIC, loginTopic);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    DiagLog.w("MQTT connection lost" + (cause != null ? ": " + cause.getMessage() : ""));
                    connected.postValue(false);
                    notifyConnection(false);
                    if (cause != null) {
                        failure.postValue(cause.getMessage());
                    }
                    updateEffective();
                    scheduleReconnect();
                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) {
                    String payload = new String(mqttMessage.getPayload(), StandardCharsets.UTF_8);
                    DiagLog.d("MQTT message on " + topic + ": " + payload);
                    message.postValue(payload);
                    for (MqttMessageHandler h : messageHandlers) {
                        MainThread.dispatch(() -> h.handleMessage(topic, payload));
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) { }
            });
        } catch (MqttException e) {
            DiagLog.e("MQTT Client Creation Failed", e);
            failure.postValue(e.getMessage());
        }
    }

    /*private void connect() {
        if (client != null && client.isConnected()) return;
        if (client == null) {
            newClient();
            if (client == null) {
                DiagLog.e("MQTT client unavailable; aborting connect");
                return;
            }
        }
        DiagLog.i("Connecting to MQTT broker " + serverUri);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false);
        options.setCleanSession(false);
        options.setKeepAliveInterval(25);
        options.setUserName(Constants.MQTT_USERNAME);
        options.setPassword(Constants.MQTT_PASSWORD.toCharArray());
        String loginTopic = BotConfigReader.getMqttTopic(context);
        if (loginTopic != null) {
            byte[] willPayload = (loginTopic + " offline").getBytes(StandardCharsets.UTF_8);
            options.setWill(Constants.MQTT_LOGIN_TOPIC, willPayload, 0, *//*retained*//* true);
        }

        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DiagLog.i("MQTT connect success");
                    // Some MQTT libraries do not invoke connectComplete on the
                    // initial connection which means our connection state would
                    // remain "disconnected" even though a session is active.
                    // Update the LiveData here to ensure UI reflects the actual
                    // transport state as soon as the broker accepts the
                    // connection.
                    connected.postValue(true);
                    failure.postValue(null);
                    notifyConnection(true);
                    clearHealthOverride();
                    reconnectAttempts = 0;
                    currentBackoffMs = 1000L;
                    updateEffective(); // <- transport changed
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    DiagLog.e("MQTT connect failed" + (exception != null ? ": " + exception.getMessage() : ""));
                    connected.postValue(false);
                    notifyConnection(false);
                    if (exception != null) {
                        failure.postValue(exception.getMessage());
                    }
                    updateEffective(); // <- transport changed
                    scheduleReconnect();
                }
            });
        } catch (IllegalArgumentException e) {
            DiagLog.e("MQTT connect error: invalid handle", e);
            failure.postValue("Recreating MQTT client (invalid handle)");
            safeCloseClient();
            newClient();
            scheduleReconnectSoon();
        } catch (MqttException e) {
            DiagLog.e("MQTT connect error", e);
            failure.postValue(e.getMessage());
            scheduleReconnect();
        }
    }*/


    private void connect() {
        if (client != null && client.isConnected()) return;

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(false); // We handle reconnects manually via scheduleReconnect()
        options.setCleanSession(false);
        options.setKeepAliveInterval(25);
        options.setUserName(Constants.MQTT_USERNAME);
        options.setPassword(Constants.MQTT_PASSWORD.toCharArray());

        // Set Last Will
        String loginTopic = BotConfigReader.getMqttTopic(context);
        if (loginTopic != null) {
            options.setWill(Constants.MQTT_LOGIN_TOPIC, (loginTopic + " offline").getBytes(), 0, true);
        }

        try {
            // This is the standard Java Paho connect call
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // Note: connectComplete in the callback will also be triggered
                    DiagLog.i("MQTT connect success");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    DiagLog.e("MQTT connect failed", exception);
                    connected.postValue(false);
                    updateEffective();
                    scheduleReconnect();
                }
            });
        } catch (MqttException e) {
            DiagLog.e("MQTT Connect Error", e);
            scheduleReconnect();
        }
    }


    private void scheduleReconnect() {
        reconnectAttempts++;
        long delay = currentBackoffMs;
        double jitter = ThreadLocalRandom.current().nextDouble(0.8, 1.2);
        long jittered = (long) (delay * jitter);
        DiagLog.w("Scheduling MQTT reconnect in " + jittered + "ms (attempt " + reconnectAttempts + ")");
        handler.postDelayed(this::connect, jittered);
        currentBackoffMs = Math.min(currentBackoffMs * 2, 60_000L);
    }

    private void scheduleReconnectSoon() {
        DiagLog.i("Scheduling MQTT reconnect in 1000ms");
        handler.postDelayed(this::connect, 1000);
    }

    private void safeCloseClient() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (MqttException ignored) {
        } finally {
            client = null;
        }
    }

    @Override
    public void onNetworkAvailable() {
        DiagLog.i("Network available, triggering MQTT connect");
        handler.post(this::connect);
    }

    @Override
    public void onNetworkLost() {
        DiagLog.w("Network lost, publishing offline");
        publishOffline();
    }

    /** Transport-Flag von Paho (roh). */
    public boolean isConnected() {
        Boolean c = connected.getValue();
        return c != null && c;
    }

    /** Effektiver Verbindungsstatus (Health-Overlay > Transport). */
    public LiveData<Boolean> getEffectiveConnected() { return effectiveConnected; }

    public static MqttClientManager getInstance(Context ctx) {
        if (instance == null) {
            synchronized (MqttClientManager.class) {
                if (instance == null) {
                    instance = new MqttClientManager(ctx);
                }
            }
        }
        return instance;
    }

    public LiveData<Boolean> getConnected() { return connected; }
    public LiveData<String> getFailure() { return failure; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<String> getPublished() { return published; }
    public LiveData<java.util.List<String>> getTopics() { return topicsLive; }

    /** Current reconnect backoff in milliseconds. */
    public long getCurrentBackoffMs() { return currentBackoffMs; }

    private void updateTopicsLive() {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (mainTopic != null) list.add(mainTopic);
        if (customTopic != null) list.add(customTopic);
        if (healthTopic != null) list.add(healthTopic);
        topicsLive.postValue(list);
    }

    public void setMainTopic(String topic) {
        if (topic == null || topic.equals(mainTopic)) return;
        if (mainTopic != null) {
            unsubscribeNow(mainTopic);
        }
        mainTopic = topic;
        DiagLog.i("Setting main topic: " + topic);
        subscribeNow(mainTopic);
        updateTopicsLive();
    }

    public void setHealthTopic(String topic) {
        if (topic == null || topic.equals(healthTopic)) return;
        if (healthTopic != null) {
            unsubscribeNow(healthTopic);
        }
        healthTopic = topic;
        DiagLog.i("Setting health topic: " + topic);
        subscribeNow(healthTopic);
        updateTopicsLive();
    }

    public void subscribeCustom(String topic) {
        if (topic == null || topic.isEmpty() || topic.equals(mainTopic)) return;
        if (topic.equals(customTopic)) return;
        if (customTopic != null) {
            unsubscribeNow(customTopic);
        }
        customTopic = topic;
        DiagLog.i("Subscribing custom topic: " + topic);
        subscribeNow(customTopic);
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().putString("customTopic", customTopic).apply();
        updateTopicsLive();
    }

    public void unsubscribeCustom() {
        if (customTopic != null) {
            unsubscribeNow(customTopic);
        }
        DiagLog.i("Unsubscribing custom topic");
        customTopic = null;
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit().remove("customTopic").apply();
        updateTopicsLive();
    }

    /** Publish with default QoS 0; true wenn der Publish-Versuch gestartet wurde. */
    public boolean publish(String topic, String payload) {
        return publish(topic, payload, 0);
    }

    /** Publish with explicit QoS. */
    public boolean publish(String topic, String payload, int qos) {
        if (topic == null || client == null || !client.isConnected()) return false;
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            DiagLog.d("Publishing to " + topic + ": " + payload + " qos=" + qos);
            client.publish(topic, msg);
            published.postValue(payload);
            return true;
        } catch (MqttException e) {
            DiagLog.e("Publish failed", e);
            failure.postValue(e.getMessage());
            return false;
        }
    }

    /** Publish and wait for acknowledgement up to timeoutMs. */
    public boolean publishWithAck(String topic, String payload, int qos, long timeoutMs) {
        if (topic == null || client == null || !client.isConnected()) return false;
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            msg.setQos(qos);
            DiagLog.d("Publishing to " + topic + ": " + payload + " qos=" + qos);
            IMqttDeliveryToken tok = client.publish(topic, msg);
            published.postValue(payload);
            tok.waitForCompletion(timeoutMs);
            return tok.isComplete();
        } catch (Exception e) {
            DiagLog.e("Publish failed", e);
            failure.postValue(e.getMessage());
            return false;
        }
    }

    /** Health-Overlay: sofort „online“ anzeigen (z. B. nach PONG). */
    public void reportHealthOnline() {
        healthOverride.postValue(true);
        updateEffective();
    }

    /** Health-Overlay: sofort „offline“ anzeigen (z. B. wenn Publish nicht startet / Timeout). */
    public void reportHealthOffline() {
        healthOverride.postValue(false);
        updateEffective();
    }

    /** Health-Overlay zurücksetzen; Anzeige folgt wieder dem Transport-Flag. */
    public void clearHealthOverride() {
        healthOverride.postValue(null);
    }

    /**
     * Publish an explicit offline marker and disconnect the MQTT client.
     * Helps the server immediately recognize that this device went offline.
     */
    public void publishOffline() {
        String loginTopic = BotConfigReader.getMqttTopic(context);
        if (loginTopic != null && isClientConnected()) {
            try {
                MqttMessage msg = new MqttMessage((loginTopic + " offline").getBytes(StandardCharsets.UTF_8));
                msg.setQos(0);
                DiagLog.i("Publishing offline marker for " + loginTopic);
                client.publish(Constants.MQTT_LOGIN_TOPIC, msg);
            } catch (MqttException e) {
                DiagLog.e("Publish offline failed", e);
                failure.postValue(e.getMessage());
            }
        }
        try { if (client != null) client.disconnect(); } catch (MqttException ignored) {}
        DiagLog.i("MQTT client disconnected");
        connected.postValue(false);
        notifyConnection(false);
        updateEffective(); // <- transport changed
    }

    public void addMessageHandler(MqttMessageHandler handler) { messageHandlers.add(handler); }
    public void removeMessageHandler(MqttMessageHandler handler) { messageHandlers.remove(handler); }
    public void addConnectionListener(MqttConnectionListener l) { connectionListeners.add(l); }
    public void removeConnectionListener(MqttConnectionListener l) { connectionListeners.remove(l); }

    private void notifyConnection(boolean state) {
        for (MqttConnectionListener l : connectionListeners) {
            MainThread.dispatch(() -> l.onConnectionChanged(state));
        }
    }

/*    *//** Force a disconnect and immediate reconnect attempt. *//*
    public void forceReconnect() {
        DiagLog.w("Force reconnect requested");
        handler.post(() -> {
            try { if (client != null && client.isConnected()) client.disconnect(); } catch (MqttException ignored) {}
            connected.postValue(false);
            notifyConnection(false);
            updateEffective(); // <- transport changed
            safeCloseClient();
            newClient();
            connect();
        });
    }*/

    /** Force a disconnect and immediate reconnect attempt. */
    public void forceReconnect() {
        DiagLog.w("Force reconnect requested");
        handler.post(() -> {
            try {
                if (client != null && client.isConnected()) {
                    client.disconnectForcibly();
                }
            } catch (Exception ignored) {}

            connected.postValue(false);
            notifyConnection(false);
            updateEffective();

            safeCloseClient(); // Clean up the old client
            newClient();       // Create the new client with ModernPingSender
            connect();         // Reconnect
        });
    }
}
