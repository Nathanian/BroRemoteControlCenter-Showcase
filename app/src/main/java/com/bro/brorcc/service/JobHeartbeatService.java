package com.bro.brorcc.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** JobService that performs periodic health checks on the MQTT service. */
public class JobHeartbeatService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        ServiceController.start(this);
        ServiceController.poke(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
