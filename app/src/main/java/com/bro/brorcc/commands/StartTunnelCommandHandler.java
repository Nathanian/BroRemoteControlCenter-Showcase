package com.bro.brorcc.commands;

import androidx.lifecycle.LiveData;

import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;

import com.bro.brorcc.R;
import com.bro.brorcc.utils.DiagLog;

/** Handles the start tunnel command. */
public class StartTunnelCommandHandler implements CommandHandler {
    @Override
    public boolean canHandle(String command, RemoteControlService service) {
        return service.getString(R.string.cmd_start_tunnel).equalsIgnoreCase(command);
    }

    @Override
    public void execute(RemoteControlService service, TunnelViewModel tunnel,
                         MqttClientManager mqtt, String responseTopic) {
        if (responseTopic == null) {
            DiagLog.w("StartTunnel: responseTopic is null; MQTT may not be configured yet");
        }

        LiveData<Boolean> existsLive = tunnel.getKeyExists();
        Boolean exists = existsLive != null ? existsLive.getValue() : null;
        if (exists == null || !exists) {
            tunnel.copyKey();
        }
        tunnel.startTunnel();
        mqtt.publish(responseTopic, Constants.RESP_TUNNEL_STARTED);
    }
}
