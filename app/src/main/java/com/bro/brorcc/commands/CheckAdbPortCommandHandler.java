package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;

/** Checks the configured ADB port. */
public class CheckAdbPortCommandHandler implements CommandHandler {
    @Override
    public boolean canHandle(String command, RemoteControlService service) {
        return service.getString(R.string.cmd_check_adb_port).equalsIgnoreCase(command);
    }

    @Override
    public void execute(RemoteControlService service, TunnelViewModel tunnel,
                         MqttClientManager mqtt, String responseTopic) {
        String port = service.getProp("service.adb.tcp.port");
        if (port == null || (port = port.trim()).isEmpty()) {
            port = service.getProp("persist.adb.tcp.port");
            if (port != null) port = port.trim();
        }
        String response = (port == null || port.isEmpty())
                ? Constants.RESP_ADB_PORT_NOT_SET
                : Constants.RESP_ADB_PORT_PREFIX + port;
        mqtt.publish(responseTopic, response);
    }
}
