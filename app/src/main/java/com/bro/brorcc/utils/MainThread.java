package com.bro.brorcc.utils;

import android.os.Handler;
import android.os.Looper;

/** Utility to dispatch runnables onto the main thread. */
public final class MainThread {
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private MainThread() {}

    public static void dispatch(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            handler.post(r);
        }
    }
}
