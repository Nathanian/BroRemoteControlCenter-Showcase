package com.bro.brorcc.model;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;
import com.bro.brorcc.utils.Constants;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Properties;

public class TunnelViewModel extends AndroidViewModel {

    private static TunnelViewModel instance;

    public enum TunnelState { NOT_STARTED, CONNECTING, RUNNING, FAILED }

    private final MutableLiveData<Boolean> keyExists = new MutableLiveData<>(false);
    private final MutableLiveData<TunnelState> tunnelState = new MutableLiveData<>(TunnelState.NOT_STARTED);
    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private static final int LOG_HISTORY_LIMIT = 500;
    private final List<String> logHistory = new ArrayList<>();

    private Session session;
    private volatile boolean stopRequested = false;
    private Thread tunnelThread;

    public TunnelViewModel(@NonNull Application application) {
        super(application);
        checkKey();
    }

    public static synchronized TunnelViewModel getInstance(@NonNull Application application) {
        if (instance == null) {
            instance = new TunnelViewModel(application);
        }
        return instance;
    }

    public LiveData<Boolean> getKeyExists() {
        return keyExists;
    }

    public LiveData<TunnelState> getTunnelState() {
        return tunnelState;
    }

    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    /**
     * Returns a snapshot of recent log messages. The history is capped to the
     * last {@link #LOG_HISTORY_LIMIT} entries, with the oldest messages
     * discarded as new ones arrive.
     */
    public synchronized List<String> getLogHistory() {
        return new ArrayList<>(logHistory);
    }

    private synchronized void log(String msg) {
        logHistory.add(msg);
        while (logHistory.size() > LOG_HISTORY_LIMIT) {
            logHistory.remove(0);
        }
        statusMessage.postValue(msg);
        Log.d("BroRCC", msg);
    }

    private void setTunnelState(TunnelState state) {
        tunnelState.postValue(state);
        log("State: " + state.name());
    }

    public void checkKey() {
        File key = new File(getApplication().getFilesDir(), "brovnc-key.pem");
        keyExists.postValue(key.exists());
    }

    public void copyKey() {
        File key = new File(getApplication().getFilesDir(), "brovnc-key.pem");
        if (key.exists()) {
            log("SSH key already exists");
            keyExists.postValue(true);
            return;
        }
        try (InputStream in = getApplication().getAssets().open("brovnc-key.pem");
             FileOutputStream out = new FileOutputStream(key)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            // restriktive Rechte setzen (best effort)
            try {
                key.setReadable(true, true);
                key.setWritable(true, true);
            } catch (Throwable ignored) { }
            // zusätzlich chmod 600, falls verfügbar (alte, robuste Variante)
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"chmod", "600", key.getAbsolutePath()});
                p.waitFor();
            } catch (Throwable ignored) { }

            log("SSH key copied");
            keyExists.postValue(true);
        } catch (IOException e) {
            log("Failed to copy key");
            Log.e("BroRCC", "copyKey", e);
        }
    }

    public void startTunnel() {
        if (tunnelThread != null && tunnelThread.isAlive()) {
            return;
        }

        stopRequested = false;
        tunnelThread = new Thread(() -> {
            File key = new File(getApplication().getFilesDir(), "brovnc-key.pem");
            if (!key.exists()) {
                log("SSH key missing");
                setTunnelState(TunnelState.FAILED);
                return;
            }

            setTunnelState(TunnelState.CONNECTING);

            try {
                // --- Alte, deterministische BC-Initialisierung ---
                try { Security.removeProvider("BC"); } catch (Exception ignored) {}
                Security.insertProviderAt(new BouncyCastleProvider(), 1);

                JSch jsch = new JSch();
                jsch.addIdentity(key.getAbsolutePath());

                // optional known_hosts aus app-internem Speicher
                File knownHosts = new File(getApplication().getFilesDir(), "known_hosts");
                if (knownHosts.exists()) {
                    try {
                        jsch.setKnownHosts(knownHosts.getAbsolutePath());
                        log("Using known_hosts from " + knownHosts.getAbsolutePath());
                    } catch (Throwable ignored) {
                        // nicht kritisch
                    }
                }

                JSch.setLogger(new com.jcraft.jsch.Logger() {
                    @Override public boolean isEnabled(int level) { return true; }
                    @Override
                    public void log(int level, String message) {
                        TunnelViewModel.this.log("JSch [" + level + "]: " + message);
                    }
                });

                log("Initializing SSH session");

                // Benutzer ggf. anpassen; Host aus Constants
                session = jsch.getSession("brovnc", Constants.SSH_HOST, 22);

                // Legacy/kompatible Settings wie im alten Mix
                // WICHTIG: Diese Legacy-Konfiguration nicht verändern!
                // Der Server akzeptiert nur diesen alten SSH-Handshake.
                // Änderungen hier könnten die Verbindung dauerhaft brechen.
                Properties legacy = new Properties();
                legacy.put("StrictHostKeyChecking", "no");
                legacy.put("kex", "ecdh-sha2-nistp256,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1");
                legacy.put("server_host_key", "ecdsa-sha2-nistp256,ssh-rsa");
                legacy.put("cipher.c2s", "aes128-ctr,aes128-cbc");
                legacy.put("cipher.s2c", "aes128-ctr,aes128-cbc");
                legacy.put("mac.c2s", "hmac-sha1");
                legacy.put("mac.s2c", "hmac-sha1");
                session.setConfig(legacy);

                log("Starting SSH handshake");
                session.connect(10000);

                log("SSH handshake successful");
                log("Authentication successful");

                try {
                    // REMOTE:5555 -> LOCALHOST:5555
                    session.setPortForwardingR(5555, "localhost", 5555);
                    log("Remote port forwarding established on 5555");
                } catch (JSchException e) {
                    log("Failed to establish remote port forwarding on 5555");
                    setTunnelState(TunnelState.FAILED);
                    return;
                }

                setTunnelState(TunnelState.RUNNING);

                while (!stopRequested && session.isConnected()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (!stopRequested) {
                    log("SSH session ended");
                    setTunnelState(TunnelState.FAILED);
                }
            } catch (JSchException e) {
                if (!stopRequested) {
                    log("Connection failed");
                    Log.e("BroRCC", "tunnel", e);
                    setTunnelState(TunnelState.FAILED);
                }
            } finally {
                if (session != null && session.isConnected()) {
                    try {
                        session.delPortForwardingR(5555);
                    } catch (JSchException ignored) {}
                    session.disconnect();
                }
                session = null;

                if (stopRequested) {
                    setTunnelState(TunnelState.NOT_STARTED);
                }
            }
        }, "bro-ssh-tunnel");
        tunnelThread.setPriority(Thread.NORM_PRIORITY);
        tunnelThread.start();
    }

    public void stopTunnel() {
        stopRequested = true;
        if (session != null && session.isConnected()) {
            log("Stopping tunnel...");
            try {
                session.delPortForwardingR(5555);
                log("Remote port forwarding closed");
            } catch (JSchException e) {
                Log.e("BroRCC", "delPortForwardingR", e);
                log("Failed to remove port forwarding");
            }
            log("Disconnecting session");
            session.disconnect();
            session = null;
            log("Tunnel stopped");
        } else {
            log("Tunnel already stopped");
        }

        if (tunnelThread != null) {
            tunnelThread.interrupt();
            tunnelThread = null;
        }

        setTunnelState(TunnelState.NOT_STARTED);
    }

}
