package com.bro.brorcc.commands;

import androidx.lifecycle.MutableLiveData;

import com.bro.brorcc.R;
import com.bro.brorcc.model.TunnelViewModel;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.service.RemoteControlService;
import com.bro.brorcc.utils.Constants;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StartTunnelCommandHandlerTest {
    @Test
    public void testExecuteStartsTunnel() {
        StartTunnelCommandHandler handler = new StartTunnelCommandHandler();
        RemoteControlService service = mock(RemoteControlService.class);
        when(service.getString(R.string.cmd_start_tunnel)).thenReturn("start");
        assertTrue(handler.canHandle("start", service));

        TunnelViewModel tunnel = mock(TunnelViewModel.class);
        MutableLiveData<Boolean> keyExists = new MutableLiveData<>(false);
        when(tunnel.getKeyExists()).thenReturn(keyExists);
        MqttClientManager mqtt = mock(MqttClientManager.class);

        handler.execute(service, tunnel, mqtt, "resp");
        verify(tunnel).copyKey();
        verify(tunnel).startTunnel();
        verify(mqtt).publish("resp", Constants.RESP_TUNNEL_STARTED);
    }
}
