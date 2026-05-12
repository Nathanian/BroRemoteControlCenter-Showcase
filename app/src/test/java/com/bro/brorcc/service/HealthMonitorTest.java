package com.bro.brorcc.service;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

/** Tests for {@link HealthMonitor} jitter scheduling. */
@RunWith(RobolectricTestRunner.class)
public class HealthMonitorTest {

    /** Scheduler recording the last scheduled runnable and delay. */
    private static class RecordingScheduler implements HealthMonitor.Scheduler {
        long lastDelay;
        Runnable lastRunnable;

        @Override
        public void post(Runnable task) {
            lastRunnable = task;
            lastDelay = 0;
        }

        @Override
        public void postDelayed(Runnable task, long delayMillis) {
            lastRunnable = task;
            lastDelay = delayMillis;
        }

        @Override
        public void removeCallbacks(Runnable task) {
            if (lastRunnable == task) {
                lastRunnable = null;
                lastDelay = 0;
            }
        }
    }

    @Test
    public void pingDelayHasJitter() {
        RecordingScheduler scheduler = new RecordingScheduler();
        HealthMonitor.Callback cb = mock(HealthMonitor.Callback.class);
        when(cb.sendPing()).thenReturn(true);
        HealthMonitor monitor = new HealthMonitor(scheduler, cb,
                HealthConfig.PING_INTERVAL_MS, HealthConfig.PONG_TIMEOUT_MS);

        monitor.start();

        Set<Long> delays = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            scheduler.lastRunnable.run();
            delays.add(scheduler.lastDelay);
        }

        long base = HealthConfig.PING_INTERVAL_MS;
        long lower = (long) (base * 0.9);
        long upper = (long) (base * 1.1);

        for (long d : delays) {
            assertTrue(d >= lower && d <= upper);
        }
        assertTrue("jitter should vary delays", delays.size() > 1);
    }
}

