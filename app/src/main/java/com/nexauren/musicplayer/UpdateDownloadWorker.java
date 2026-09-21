package com.nexauren.musicplayer;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class UpdateDownloadWorker extends Worker {
    public static final String INPUT_URL = "url";
    public static final String INPUT_VERSION = "version";
    public static final String INPUT_DIGEST = "digest";
    private static final int NOTIFICATION_ID = 2102;

    public UpdateDownloadWorker(@NonNull Context appContext, @NonNull WorkerParameters params) {
        super(appContext, params);
    }

    @NonNull @Override
    public Result doWork() {
        String urlString = getInputData().getString(INPUT_URL);
        String version = getInputData().getString(INPUT_VERSION);
        String digest = getInputData().getString(INPUT_DIGEST);
        if (urlString == null) urlString = "";
        if (version == null) version = "";
        if (digest == null) digest = "";
        if (urlString.isEmpty() || version.isEmpty()) return Result.failure();

        try {
            setForegroundAsync(createForegroundInfo("A preparar atualização " + version, 0, true)).get();

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Nexauren-Music-Player");
            connection.setRequestProperty("Accept", "application/octet-stream");
            try {
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                long total = connection.getContentLengthLong();
                File output = UpdateManager.updateFile(getApplicationContext());
                if (output.exists()) output.delete();

                try (InputStream input = new BufferedInputStream(connection.getInputStream());
                     BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                    byte[] buffer = new byte[16384];
                    long downloaded = 0;
                    int read;
                    int last = -1;
                    while ((read = input.read(buffer)) != -1) {
                        if (isStopped()) return Result.failure();
                        out.write(buffer, 0, read);
                        downloaded += read;
                        int progress = total > 0 ? (int)Math.min(100, downloaded * 100 / total) : 0;
                        if (progress != last) {
                            last = progress;
                            setProgressAsync(new Data.Builder().putInt("progress", progress).putLong("bytes", downloaded).putLong("total", total).build());
                            setForegroundAsync(createForegroundInfo("Baixando Nexauren " + version, progress, false));
                        }
                    }
                }

                if (!digest.isEmpty() && digest.startsWith("sha256:")) {
                    String expected = digest.substring("sha256:".length()).trim().toLowerCase(java.util.Locale.US);
                    String actual = UpdateManager.sha256(output);
                    if (!expected.equals(actual)) {
                        output.delete();
                        throw new SecurityException("Hash SHA-256 da atualização não corresponde.");
                    }
                }

                getApplicationContext().getSharedPreferences(UpdateManager.PREFS, Context.MODE_PRIVATE)
                        .edit().putString("downloaded_version", version).apply();

                postReadyNotification(version);
                return Result.success(new Data.Builder().putString("path", output.getAbsolutePath()).putString("version", version).build());
            } finally {
                connection.disconnect();
            }
        } catch (Exception error) {
            postErrorNotification(version, error.getMessage());
            return Result.failure();
        }
    }

    private ForegroundInfo createForegroundInfo(String title, int progress, boolean indeterminate) {
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), UpdateNotifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(indeterminate ? "Conectando…" : progress + "%")
                .setProgress(100, progress, indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build();
        return new ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }

    private void postReadyNotification(String version) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setAction(UpdateManager.ACTION_INSTALL_UPDATE);
        intent.putExtra(UpdateManager.EXTRA_VERSION, version);
        android.app.PendingIntent pending = android.app.PendingIntent.getActivity(
                getApplicationContext(), 2103, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), UpdateNotifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Atualização pronta")
                .setContentText("Nexauren " + version + " está pronta para instalar.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        UpdateNotifications.notify(getApplicationContext(), 2103, notification);
    }

    private void postErrorNotification(String version, String reason) {
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), UpdateNotifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Atualização do Nexauren")
                .setContentText(reason == null ? "Não foi possível baixar a atualização." : "Falha no download.")
                .setAutoCancel(true)
                .build();
        UpdateNotifications.notify(getApplicationContext(), 2104, notification);
    }
}
