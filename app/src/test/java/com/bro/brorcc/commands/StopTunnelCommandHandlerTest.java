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

public class StopTunnelCommandHandlerTest {
    @Test
    public void testExecuteStopsTunnel() {
        StopTunnelCommandHandler handler = new StopTunnelCommandHandler();
        RemoteControlService service = mock(RemoteControlService.class);
        when(service.getString(R.string.cmd_stop_tunnel)).thenReturn("stop");
        when(service.getString(R.string.cmd_stop_tunnel_alt)).thenReturn("halt");
        assertTrue(handler.canHandle("stop", service));
        assertTrue(handler.canHandle("halt", service));

        TunnelViewModel tunnel = mock(TunnelViewModel.class);
        MqttClientManager mqtt = mock(MqttClientManager.class);
        handler.execute(service, tunnel, mqtt, "resp");
        verify(tunnel).stopTunnel();
        verify(mqtt).publish("resp", Constants.RESP_TUNNEL_STOPPED);
    }
}
