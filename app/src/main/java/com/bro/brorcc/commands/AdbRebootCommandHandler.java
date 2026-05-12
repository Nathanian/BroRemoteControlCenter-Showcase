package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.CommandResult;
import com.bro.brorcc.utils.Constants;
import com.bro.brorcc.utils.DiagLog;
import com.bro.brorcc.utils.ShellCommandRunner;

/** Handles restarting the ADB daemon. */
public class AdbRebootCommandHandler implements CommandHandler {
    @Override
    public boolean canHandle(String command, RemoteControlService service) {
        return service.getString(R.string.cmd_adb_reboot).equalsIgnoreCase(command)
                || service.getString(R.string.cmd_restart_adb_alt).equalsIgnoreCase(command)
                || service.getString(R.string.cmd_restart_adb).equalsIgnoreCase(command);
    }

    @Override
    public void execute(RemoteControlService service, TunnelViewModel tunnel,
                        MqttClientManager mqtt, String responseTopic) {
        String[] cmds = {
                "setprop persist.adb.tcp.port 5555",
                "setprop service.adb.tcp.port 5555",
                "stop adbd"
        };
        boolean ok = true;
        for (String cmd : cmds) {
            CommandResult r = ShellCommandRunner.runAsRoot(cmd);
            if (r == null || r.exitCode != 0) {
                ok = false;
                DiagLog.w("Failed: " + cmd);
                break;
            }
        }
        if (ok) {
            // kleines Delay, damit adbd wirklich aus ist
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
            CommandResult start = ShellCommandRunner.runAsRoot("start adbd");
            ok = start != null && start.exitCode == 0;
        }
        mqtt.publish(responseTopic, ok ? Constants.RESP_ADB_RESTARTED
                : "adb_restart_failed");
    }
}
