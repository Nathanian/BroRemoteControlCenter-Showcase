package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.CommandResult;
import com.bro.brorcc.utils.Constants;
import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.utils.ShellCommandRunner;

/** Handles device reboot command. */
public class RebootCommandHandler implements CommandHandler {
    @Override
    public boolean canHandle(String command, RemoteControlService service) {
        return service.getString(R.string.cmd_reboot).equalsIgnoreCase(command);
    }

    @Override
    public void execute(RemoteControlService service, TunnelViewModel tunnel,
                        MqttClientManager mqtt, String responseTopic) {
        // Optional: erst ankündigen, DANN rebooten (falls Publish wichtig ist)
        boolean ack = mqtt.publishWithAck(responseTopic, Constants.RESP_REBOOTING, 1, 800);
        if (!ack) {
            // fallback publish without ack
            mqtt.publish(responseTopic, Constants.RESP_REBOOTING, 1);
        }
        try {
            Thread.sleep(300);
        } catch (InterruptedException ignored) { }
        CommandResult result = ShellCommandRunner.runAsRoot("reboot");
        if (result == null || result.exitCode != 0) {
            DiagLog.e("Error executing reboot");
            mqtt.publish(responseTopic, "reboot_failed");
        }
    }
}
