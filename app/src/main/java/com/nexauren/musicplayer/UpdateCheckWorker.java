package com.nexauren.musicplayer;

import android.content.Context;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.Worker;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Data;

public final class UpdateCheckWorker extends Worker {
    public UpdateCheckWorker(@NonNull Context appContext,@NonNull WorkerParameters params){super(appContext,params);}

    @NonNull @Override public Result doWork(){
        try{
            UpdateManager.ReleaseInfo info=UpdateManager.fetchLatest();
            if(!UpdateManager.isNewer(info.version,BuildConfig.VERSION_NAME)) return Result.success();
            android.content.SharedPreferences prefs=getApplicationContext().getSharedPreferences(UpdateManager.PREFS,Context.MODE_PRIVATE);
            String deferred=prefs.getString("deferred_version","");
            long deferredUntil=prefs.getLong("deferred_until",0L);
            if(info.version.equals(deferred)&&System.currentTimeMillis()<deferredUntil)return Result.success();
            prefs.edit().putString("latest_version",info.version).putString("latest_url",info.apkUrl)
                    .putString("latest_notes",info.notes).putString("latest_digest",info.digest).apply();

            postNotification(info);
            return Result.success();
        }catch(Exception e){return Result.retry();}
    }

    private void postNotification(UpdateManager.ReleaseInfo info){
        Intent intent=new Intent(getApplicationContext(),MainActivity.class);
        intent.setAction(UpdateManager.ACTION_DOWNLOAD_UPDATE);
        intent.putExtra(UpdateManager.EXTRA_VERSION,info.version);
        intent.putExtra(UpdateManager.EXTRA_URL,info.apkUrl);
        intent.putExtra(UpdateManager.EXTRA_NOTES,info.notes);
        intent.putExtra(UpdateManager.EXTRA_DIGEST,info.digest);
        PendingIntent pending=PendingIntent.getActivity(getApplicationContext(),2101,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        String notes=info.notes==null?"Nova versão disponível.":info.notes.replaceAll("\\s+"," ").trim();
        if(notes.length()>140)notes=notes.substring(0,140)+"…";

        NotificationCompat.Builder builder=new NotificationCompat.Builder(getApplicationContext(),UpdateNotifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Nexauren " + info.version + " disponível")
                .setContentText(notes.isEmpty()?"Novidades disponíveis.":notes)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notes))
                .setContentIntent(pending)
                .addAction(android.R.drawable.stat_sys_download,"Baixar atualização",pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        UpdateNotifications.notify(getApplicationContext(),2101,builder.build());
    }
}
