package com.bro.brorcc.commands;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CheckAdbPortCommandHandlerTest {
    @Test
    public void testExecutePublishesPort() {
        CheckAdbPortCommandHandler handler = new CheckAdbPortCommandHandler();
        RemoteControlService service = mock(RemoteControlService.class);
        when(service.getString(R.string.cmd_check_adb_port)).thenReturn("port");
        when(service.getProp("service.adb.tcp.port")).thenReturn("5555");
        assertTrue(handler.canHandle("port", service));
        TunnelViewModel tunnel = mock(TunnelViewModel.class);
        MqttClientManager mqtt = mock(MqttClientManager.class);
        handler.execute(service, tunnel, mqtt, "resp");
        verify(mqtt).publish("resp", Constants.RESP_ADB_PORT_PREFIX + "5555");
    }
}
