package com.bro.brorcc.mqtt;

/** Listener for MQTT connection status changes. */
public interface MqttConnectionListener {
    void onConnectionChanged(boolean connected);
}
