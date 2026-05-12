package com.bro.brorcc.mqtt;

/** Callback for processing incoming MQTT messages. */
public interface MqttMessageHandler {
    void handleMessage(String topic, String payload);
}
