package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;

/** Handles IP address queries. */
public class IpQueryCommandHandler implements CommandHandler {
    @Override
    public boolean canHandle(String command, RemoteControlService service) {
        return service.getString(R.string.cmd_get_ip).equalsIgnoreCase(command)
                || service.getString(R.string.cmd_ip).equalsIgnoreCase(command);
    }

    @Override
    public void execute(RemoteControlService service, TunnelViewModel tunnel,
                         MqttClientManager mqtt, String responseTopic) {
        String ip = service.detectIp();
        String response = ip != null
                ? Constants.RESP_IP_PREFIX + ip
                : Constants.RESP_IP_NOT_FOUND;
        mqtt.publish(responseTopic, response);
    }
}
