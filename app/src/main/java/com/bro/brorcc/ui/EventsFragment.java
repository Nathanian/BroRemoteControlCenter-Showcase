package com.bro.brorcc.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.Layout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bro.brorcc.databinding.FragmentEventsBinding;

import java.util.List;

/**
 * Displays consolidated event messages emitted from the dashboard.
 */
public class EventsFragment extends Fragment {
    private FragmentEventsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentEventsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.textEvents.setMovementMethod(new ScrollingMovementMethod());

        DashboardLogViewModel logViewModel = new ViewModelProvider(requireActivity())
                .get(DashboardLogViewModel.class);

        logViewModel.getEvents().observe(getViewLifecycleOwner(), this::renderEvents);
    }

    private void renderEvents(@Nullable List<String> events) {
        if (events == null || events.isEmpty()) {
            binding.textEvents.setText("");
            binding.emptyState.setVisibility(View.VISIBLE);
            return;
        }
        binding.emptyState.setVisibility(View.GONE);
        binding.textEvents.setText(TextUtils.join("\n", events));
        binding.textEvents.post(() -> scrollToBottom(binding.textEvents));
    }

    private void scrollToBottom(@NonNull TextView textView) {
        Layout layout = textView.getLayout();
        if (layout == null) {
            return;
        }
        int scrollAmount = layout.getLineTop(textView.getLineCount()) - textView.getHeight();
        if (scrollAmount > 0) {
            textView.scrollTo(0, scrollAmount);
        } else {
            textView.scrollTo(0, 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
