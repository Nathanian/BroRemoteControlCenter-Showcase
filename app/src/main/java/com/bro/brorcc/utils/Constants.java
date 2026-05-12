package com.bro.brorcc.utils;

/** Global constants for the application. */
public class Constants {
    // Achtung: Der dazugehörige SSH-Handshake ist auf Legacy festgelegt
    // (siehe TunnelViewModel). Änderungen an Host oder Handshake können
    // die Verbindung komplett verhindern.
    public static final String SSH_HOST = "YOUR_SERVER_IP";
    public static final int MQTT_PORT = 1883;
    public static final String MQTT_USERNAME = "YOUR_MQTT_USERNAME";
    public static final String MQTT_PASSWORD = "YOUR_MQTT_PASSWORD";
    public static final String MQTT_LOGIN_TOPIC = "YOUR_TOPIC/login";

    // Response payloads published on the MQTT response topic. Keeping these
    // values centralized allows remote integrations to rely on a stable
    // interface when interpreting results of commands sent to the device.
    public static final String RESP_TUNNEL_STARTED = "tunnel started";
    public static final String RESP_TUNNEL_STOPPED = "tunnel stopped";
    public static final String RESP_ADB_RESTARTED = "adb daemon restarted";
    public static final String RESP_PONG = "pong";
    public static final String RESP_ADB_PORT_PREFIX = "adb port: ";
    public static final String RESP_ADB_PORT_NOT_SET = "adb port not set";
    public static final String RESP_IP_PREFIX = "ip: ";
    public static final String RESP_IP_NOT_FOUND = "ip: not found";
    public static final String RESP_REBOOTING = "device reboot initiated";
    public static final String RESP_UNKNOWN_COMMAND = "unknown command";
}
