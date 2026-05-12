package com.bro.brorcc.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.bro.brorcc.service.HealthConfig;

/**
 * Tests for {@link WatchdogManager} scheduling and health checks.
 */
@RunWith(RobolectricTestRunner.class)
public class WatchdogManagerTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void schedulePeriodic_shouldScheduleJob() {
        WatchdogManager manager = new WatchdogManager(context);

        manager.schedulePeriodic();

        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        List<JobInfo> jobs = js.getAllPendingJobs();
        assertEquals(1, jobs.size());
        JobInfo job = jobs.get(0);
        assertEquals(1000, job.getId());
        assertTrue(job.isPersisted());
        assertEquals(JobInfo.BACKOFF_POLICY_EXPONENTIAL, job.getBackoffPolicy());
        assertEquals(TimeUnit.SECONDS.toMillis(5), job.getInitialBackoffMillis());
        assertEquals(JobInfo.NETWORK_TYPE_ANY, job.getNetworkType());
    }

    @Test
    public void scheduleOneShot_shouldScheduleJob() {
        WatchdogManager manager = new WatchdogManager(context);

        manager.scheduleOneShot();

        JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo job = js.getPendingJob(1001);
        assertNotNull(job);
        assertEquals(1001, job.getId());
    }

    @Test
    public void scheduleAlarm_shouldSetAlarm() {
        WatchdogManager manager = new WatchdogManager(context);

        manager.scheduleAlarm(5000L);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAm = Shadows.shadowOf(am);
        List<ShadowAlarmManager.ScheduledAlarm> alarms = shadowAm.getScheduledAlarms();
        assertEquals(1, alarms.size());
        ShadowAlarmManager.ScheduledAlarm alarm = alarms.get(0);
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type);
    }

    @Test
    public void ensureServiceRunning_whenHealthy_doesNotRestart() throws Exception {
        WatchdogManager.ServiceStarter starter = mock(WatchdogManager.ServiceStarter.class);
        WatchdogManager manager = new WatchdogManager(context, starter);

        CompletableFuture<Boolean> future = manager.ensureServiceRunning();

        Intent result = new Intent(WatchdogManager.ACTION_HEALTH_RESULT);
        result.putExtra("isHealthy", true);
        LocalBroadcastManager.getInstance(context).sendBroadcast(result);

        assertTrue(future.get(1, TimeUnit.SECONDS));
        verify(starter, never()).start(any());
    }

    @Test
    public void ensureServiceRunning_whenUnhealthy_restartsService() throws Exception {
        WatchdogManager.ServiceStarter starter = mock(WatchdogManager.ServiceStarter.class);
        WatchdogManager manager = new WatchdogManager(context, starter);

        CompletableFuture<Boolean> future = manager.ensureServiceRunning();

        Intent result = new Intent(WatchdogManager.ACTION_HEALTH_RESULT);
        result.putExtra("isHealthy", false);
        LocalBroadcastManager.getInstance(context).sendBroadcast(result);

        assertFalse(future.get(1, TimeUnit.SECONDS));

        verify(starter).start(eq(context));
    }

    @Test
    public void probeHealth_timesOut() throws Exception {
        WatchdogManager manager = new WatchdogManager(context);
        long start = System.currentTimeMillis();
        CompletableFuture<Boolean> future = manager.probeHealth();
        boolean result = future.get(HealthConfig.HEALTH_PROBE_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
        assertFalse(result);
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed >= HealthConfig.HEALTH_PROBE_TIMEOUT_MS);
    }
}
