package com.bro.brorcc.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bro.brorcc.databinding.FragmentSettingsBinding;
import com.bro.brorcc.mqtt.MqttClientManager;
import com.bro.brorcc.utils.BotConfigReader;
import com.bro.brorcc.utils.Constants;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        SharedPreferences prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        binding.editTopic.setText(prefs.getString("customTopic", ""));

        binding.btnSubscribe.setOnClickListener(v -> {
            String topic = binding.editTopic.getText().toString().trim();
            if (!topic.isEmpty()) {
                MqttClientManager.getInstance(ctx).subscribeCustom(topic);
            }
        });

        binding.btnUnsubscribe.setOnClickListener(v -> {
            MqttClientManager.getInstance(ctx).unsubscribeCustom();
        });

        binding.btnPublishAlive.setOnClickListener(v -> {
            String loginTopic = BotConfigReader.getMqttTopic(ctx);
            if (loginTopic != null) {
                MqttClientManager.getInstance(ctx).publish(Constants.MQTT_LOGIN_TOPIC, loginTopic);
            }
        });

        binding.btnBack.setOnClickListener(v -> requireActivity().finish());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
