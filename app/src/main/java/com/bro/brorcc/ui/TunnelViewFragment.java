package com.bro.brorcc.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

import com.bro.brorcc.databinding.FragmentTunnelViewBinding;
import com.bro.brorcc.model.TunnelViewModel;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

/**
 * Displays log messages from the SSH/VNC tunnel managed by {@link TunnelViewModel}.
 * No controls are provided; the tunnel lifecycle is handled by {@link com.bro.brorcc.service.RemoteControlService}.
 */
public class TunnelViewFragment extends Fragment {
    private FragmentTunnelViewBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTunnelViewBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TunnelViewModel vm = TunnelViewModel.getInstance(requireActivity().getApplication());

        // Alte Logs laden
        List<String> history = vm.getLogHistory();
        for (String msg : history) {
            appendMessage(msg);
        }

        // Neue Nachrichten beobachten
        vm.getStatusMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                appendMessage(msg);
            }
        });

        // Tunnelstatus beobachten
        vm.getTunnelState().observe(getViewLifecycleOwner(), state -> {
            if (state != null) {
                appendMessage("State: " + state.name());
            }
        });

        if (requireActivity() instanceof MainActivity) {
            binding.btnBack.setVisibility(View.GONE);
        } else {
            binding.btnBack.setOnClickListener(v -> requireActivity().finish());
        }
    }

    private void appendMessage(String msg) {
        CharSequence entry;
        String lower = msg.toLowerCase();
        if (lower.contains("handshake") || lower.contains("authentication successful")) {
            SpannableString colored = new SpannableString(msg + "\n");
            colored.setSpan(new ForegroundColorSpan(Color.GREEN), 0, colored.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            entry = colored;
        } else {
            entry = msg + "\n";
        }
        binding.txtLog.append(entry);
        binding.scrollLog.post(() -> binding.scrollLog.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
