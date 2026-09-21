package com.nexauren.musicplayer;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class UpdateScheduler {
    private static final String PERIODIC_NAME = "nexauren-update-periodic";
    private static final String STARTUP_NAME = "nexauren-update-startup";

    private UpdateScheduler() {}

    public static void ensure(Context context) {
        Context app=context.getApplicationContext();
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();

        PeriodicWorkRequest periodic=new PeriodicWorkRequest.Builder(
                UpdateCheckWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic);

        OneTimeWorkRequest startup=new OneTimeWorkRequest.Builder(UpdateCheckWorker.class)
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(app).enqueueUniqueWork(
                STARTUP_NAME, ExistingWorkPolicy.REPLACE, startup);
    }
}
