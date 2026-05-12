package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;
import com.bro.brorcc.utils.DiagLog;

/** Responds to ping commands. */
public class PingCommandHandler implements CommandHandler {
    @Override
    public boolean canHandle(String command, RemoteControlService service) {
        return service.getString(R.string.cmd_ping).equalsIgnoreCase(command);
    }

    @Override
    public void execute(RemoteControlService service, TunnelViewModel tunnel,
                         MqttClientManager mqtt, String responseTopic) {
        boolean ok = mqtt.publish(responseTopic, Constants.RESP_PONG);
        if (!ok) {
            DiagLog.e("Failed to publish PONG", null);
            mqtt.reportHealthOffline();
        }
    }
}
