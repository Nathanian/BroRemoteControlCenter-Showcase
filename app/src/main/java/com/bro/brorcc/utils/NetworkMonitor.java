package com.bro.brorcc.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Monitors network connectivity and notifies registered listeners.
 * Components can react to connectivity changes to repair their state
 * instead of using random retries.
 */
public class NetworkMonitor {
    /** Listener for network availability changes. */
    public interface Listener {
        /** Called when network connectivity becomes available. */
        void onNetworkAvailable();
        /** Called when the active network is lost. */
        void onNetworkLost();
    }

    private static final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private static boolean registered = false;
    private static HandlerThread handlerThread;
    private static Handler handler;
    private static ConnectivityManager connectivityManager;
    private static ConnectivityManager.NetworkCallback networkCallback;

    private NetworkMonitor() { }

    /**
     * Initialize monitoring using the application's context. Safe to call multiple times.
     */
    public static synchronized void init(Context context) {
        if (registered) return;

        if (handlerThread == null) {
            handlerThread = new HandlerThread("NetworkMonitor");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());
        }

        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : listeners) l.onNetworkAvailable();
                    }
                });
            }

            @Override
            public void onLost(Network network) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : listeners) l.onNetworkLost();
                    }
                });
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        }
        registered = true;
    }

    /** Register a listener to be notified about connectivity changes. */
    public static void addListener(Listener l) { listeners.add(l); }

    /** Remove a previously registered listener. */
    public static synchronized void removeListener(Listener l) {
        listeners.remove(l);
        if (listeners.isEmpty()) {
            shutdown();
        }
    }

    /** Stop monitoring and release resources. */
    public static synchronized void shutdown() {
        if (!registered) return;
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) { }
            networkCallback = null;
            connectivityManager = null;
        }
        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }
        handler = null;
        registered = false;
        listeners.clear();
    }
}
