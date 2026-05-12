package com.bro.brorcc.service;

/**
 * Centralized health timing configuration used by service and watchdog.
 */
public final class HealthConfig {
    private HealthConfig() {}

    /** Interval between health pings in milliseconds. */
    public static final long PING_INTERVAL_MS = 30_000L;
    /** Timeout waiting for a pong reply in milliseconds. */
    public static final long PONG_TIMEOUT_MS = 5_000L;
    /** Window considered healthy since last pong in milliseconds. */
    public static final long HEALTH_WINDOW_MS = 90_000L;
    /** Timeout for watchdog health probes in milliseconds. */
    public static final long HEALTH_PROBE_TIMEOUT_MS = 4_000L;
}
