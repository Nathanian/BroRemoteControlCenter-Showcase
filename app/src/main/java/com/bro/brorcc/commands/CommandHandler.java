package com.bro.brorcc.commands;

import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;

/** Interface for handling commands received via MQTT. */
public interface CommandHandler {
    /** Returns true if this handler can process the given command. */
    boolean canHandle(String command, RemoteControlService service);

    /** Executes the command using provided services. */
    void execute(RemoteControlService service, TunnelViewModel tunnel,
                 MqttClientManager mqtt, String responseTopic);
}
