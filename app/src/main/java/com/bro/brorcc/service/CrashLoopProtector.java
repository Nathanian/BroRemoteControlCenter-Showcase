package com.bro.brorcc.service;

import android.content.Context;
import android.content.SharedPreferences;

/** Utility tracking service restarts to avoid crash loops. */
public final class CrashLoopProtector {
    private static final String PREF = "crash_loop_protector";
    private static final String KEY_FIRST = "first";
    private static final String KEY_COUNT = "count";
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_COUNT = 3;

    private CrashLoopProtector() { }

    public static boolean tooMany(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long first = prefs.getLong(KEY_FIRST, 0L);
        int count = prefs.getInt(KEY_COUNT, 0);
        long now = System.currentTimeMillis();
        if (first == 0L || now - first > WINDOW_MS) {
            return false;
        }
        return count >= MAX_COUNT;
    }

    public static void noteStart(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long first = prefs.getLong(KEY_FIRST, 0L);
        int count = prefs.getInt(KEY_COUNT, 0);
        if (first == 0L || now - first > WINDOW_MS) {
            first = now;
            count = 0;
        }
        prefs.edit().putLong(KEY_FIRST, first).putInt(KEY_COUNT, count + 1).apply();
    }

    public static void reset(Context context) {
        context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().clear().apply();
    }
}

