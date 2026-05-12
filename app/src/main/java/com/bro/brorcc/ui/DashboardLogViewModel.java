package com.bro.brorcc.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Shared ViewModel collecting dashboard events for the log tab.
 */
public class DashboardLogViewModel extends AndroidViewModel {
    private static final int MAX_EVENTS = 200;
    private final MutableLiveData<List<String>> eventsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final Deque<String> buffer = new ArrayDeque<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public DashboardLogViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<String>> getEvents() {
        return eventsLiveData;
    }

    public synchronized void appendEvent(@NonNull String message) {
        if (message.isEmpty()) {
            return;
        }
        String entry = String.format(Locale.getDefault(), "%s  %s", timeFormat.format(new Date()), message);
        buffer.addLast(entry);
        while (buffer.size() > MAX_EVENTS) {
            buffer.removeFirst();
        }
        eventsLiveData.setValue(new ArrayList<>(buffer));
    }
}
