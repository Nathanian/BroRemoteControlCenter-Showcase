package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;
import com.bro.brorcc.utils.ShellCommandRunner;

import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/** Tests for {@link RebootCommandHandler}. */
public class RebootCommandHandlerTest {

    @Test
    public void executesWithAckBeforeReboot() {
        RebootCommandHandler handler = new RebootCommandHandler();
        RemoteControlService service = mock(RemoteControlService.class);
        when(service.getString(R.string.cmd_reboot)).thenReturn("reboot");
        assertTrue(handler.canHandle("reboot", service));
        TunnelViewModel tunnel = mock(TunnelViewModel.class);
        MqttClientManager mqtt = mock(MqttClientManager.class);
        when(mqtt.publishWithAck("resp", Constants.RESP_REBOOTING, 1, 800)).thenReturn(true);

        try (MockedStatic<ShellCommandRunner> shell = mockStatic(ShellCommandRunner.class)) {
            handler.execute(service, tunnel, mqtt, "resp");
            verify(mqtt).publishWithAck("resp", Constants.RESP_REBOOTING, 1, 800);
            verify(mqtt, never()).publish("resp", Constants.RESP_REBOOTING, 1);
            shell.verify(() -> ShellCommandRunner.runAsRoot("reboot"));
        }
    }

    @Test
    public void executesWithFallbackWhenNoAck() {
        RebootCommandHandler handler = new RebootCommandHandler();
        RemoteControlService service = mock(RemoteControlService.class);
        when(service.getString(R.string.cmd_reboot)).thenReturn("reboot");
        assertTrue(handler.canHandle("reboot", service));
        TunnelViewModel tunnel = mock(TunnelViewModel.class);
        MqttClientManager mqtt = mock(MqttClientManager.class);
        when(mqtt.publishWithAck("resp", Constants.RESP_REBOOTING, 1, 800)).thenReturn(false);

        try (MockedStatic<ShellCommandRunner> shell = mockStatic(ShellCommandRunner.class)) {
            handler.execute(service, tunnel, mqtt, "resp");
            verify(mqtt).publishWithAck("resp", Constants.RESP_REBOOTING, 1, 800);
            verify(mqtt).publish("resp", Constants.RESP_REBOOTING, 1);
            shell.verify(() -> ShellCommandRunner.runAsRoot("reboot"));
        }
    }
}

