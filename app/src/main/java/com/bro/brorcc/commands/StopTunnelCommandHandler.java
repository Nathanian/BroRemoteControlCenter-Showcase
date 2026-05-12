package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;

/** Handles stopping the tunnel. */
public class StopTunnelCommandHandler implements CommandHandler {
    @Override
    public boolean canHandle(String command, RemoteControlService service) {
        return service.getString(R.string.cmd_stop_tunnel).equalsIgnoreCase(command)
                || service.getString(R.string.cmd_stop_tunnel_alt).equalsIgnoreCase(command);
    }

    @Override
    public void execute(RemoteControlService service, TunnelViewModel tunnel,
                         MqttClientManager mqtt, String responseTopic) {
        tunnel.stopTunnel();
        mqtt.publish(responseTopic, Constants.RESP_TUNNEL_STOPPED);
    }
}
