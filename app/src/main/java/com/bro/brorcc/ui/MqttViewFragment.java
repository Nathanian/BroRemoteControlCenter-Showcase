package com.bro.brorcc.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bro.brorcc.databinding.FragmentMqttViewBinding;
import com.bro.brorcc.mqtt.MqttClientManager;

public class MqttViewFragment extends Fragment {
    private FragmentMqttViewBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMqttViewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MqttClientManager manager = MqttClientManager.getInstance(requireContext());
        manager.getTopics().observe(getViewLifecycleOwner(), topics -> {
            if (topics == null || topics.isEmpty()) {
                binding.txtTopics.setText("Topics: none");
            } else {
                binding.txtTopics.setText("Topics: " + android.text.TextUtils.join(", ", topics));
            }
        });
        // Use effective connection state (includes health checks)
        manager.getEffectiveConnected().observe(getViewLifecycleOwner(), connected -> {
            if (Boolean.TRUE.equals(connected)) {
                binding.txtStatus.setText("Connected");
            } else {
                String reason = manager.getFailure().getValue();
                if (reason == null || reason.isEmpty()) {
                    binding.txtStatus.setText("Disconnected");
                } else {
                    binding.txtStatus.setText("Disconnected: " + reason);
                }
            }
        });
        manager.getMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                binding.txtReceived.append(msg + "\n");
                binding.scrollReceived.post(() -> binding.scrollReceived.fullScroll(View.FOCUS_DOWN));
            }
        });
        manager.getPublished().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                binding.txtPublished.append(msg + "\n");
                binding.scrollPublished.post(() -> binding.scrollPublished.fullScroll(View.FOCUS_DOWN));
            }
        });
        if (requireActivity() instanceof MainActivity) {
            binding.btnBack.setVisibility(View.GONE);
        } else {
            binding.btnBack.setOnClickListener(v -> requireActivity().finish());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
