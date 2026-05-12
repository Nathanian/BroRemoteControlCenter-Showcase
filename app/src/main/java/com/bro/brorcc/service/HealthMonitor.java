package com.bro.brorcc.service;

import android.os.Handler;

import com.bro.brorcc.utils.DiagLog;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class managing ping/pong health checks for the MQTT connection.
 * It schedules periodic ping messages and triggers a reconnect callback when
 * no corresponding pong is received within the timeout window.
 */
public class HealthMonitor {

    /** Abstraction over the scheduling primitives used by {@link HealthMonitor}. */
    interface Scheduler {
        void post(Runnable task);

        void postDelayed(Runnable task, long delayMs);

        void removeCallbacks(Runnable task);
    }

    public interface Callback {
        /**
         * Sends a ping message. Returns {@code true} if the ping was sent.
         */
        boolean sendPing();

        /**
         * Invoked when a reconnect should be attempted.
         */
        void requestReconnect();
    }

    private final Scheduler scheduler;
    private final Callback callback;
    private final long pingIntervalMs;
    private final long pongTimeoutMs;
    private final AtomicLong lastPongAt = new AtomicLong(0);
    private final AtomicBoolean pingInFlight = new AtomicBoolean(false);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private final Runnable pongTimeout = new Runnable() {
        @Override
        public void run() {
            if (pingInFlight.getAndSet(false)) {
                callback.requestReconnect();
            }
        }
    };

    private final Runnable pingTask = new Runnable() {
        @Override
        public void run() {
            if (callback.sendPing()) {
                pingInFlight.set(true);
                scheduler.postDelayed(pongTimeout, pongTimeoutMs);
            } else {
                DiagLog.d("Ping skipped");
            }
            long base = pingIntervalMs;
            long jitter = (long) (base * (Math.random() * 0.2 - 0.1));
            long delay = Math.max(0L, base + jitter);
            DiagLog.d("Next ping in " + delay + "ms");
            scheduler.postDelayed(this, delay);
        }
    };

    public HealthMonitor(Handler handler, Callback callback, long pingIntervalMs, long pongTimeoutMs) {
        this(new HandlerScheduler(handler), callback, pingIntervalMs, pongTimeoutMs);
    }

    HealthMonitor(Scheduler scheduler, Callback callback, long pingIntervalMs, long pongTimeoutMs) {
        this.scheduler = scheduler;
        this.callback = callback;
        this.pingIntervalMs = pingIntervalMs;
        this.pongTimeoutMs = pongTimeoutMs;
    }

    /** Starts periodic ping scheduling. */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            scheduler.post(pingTask);
        }
    }

    /** Stops all scheduled tasks. */
    public void stop() {
        scheduler.removeCallbacks(pingTask);
        scheduler.removeCallbacks(pongTimeout);
        pingInFlight.set(false);
        isRunning.set(false);
    }

    /**
     * Should be called when a pong message is received.
     */
    public void onPong() {
        lastPongAt.set(System.currentTimeMillis());
        pingInFlight.set(false);
        DiagLog.heartbeat();
    }

    /**
     * Returns the timestamp of the last pong reception.
     */
    public long getLastPongAt() {
        return lastPongAt.get();
    }

    /**
     * Returns {@code true} if a pong was received within the given time window.
     */
    public boolean isHealthy(long windowMs) {
        return System.currentTimeMillis() - lastPongAt.get() <= windowMs;
    }

    /** Convenience overload using default window. */
    public boolean isHealthy() {
        return isHealthy(HealthConfig.HEALTH_WINDOW_MS);
    }

    private static final class HandlerScheduler implements Scheduler {
        private final Handler handler;

        HandlerScheduler(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void post(Runnable task) {
            handler.post(task);
        }

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            handler.postDelayed(task, delayMs);
        }

        @Override
        public void removeCallbacks(Runnable task) {
            handler.removeCallbacks(task);
        }
    }
}

