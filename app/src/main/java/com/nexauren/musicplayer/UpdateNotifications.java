package com.nexauren.musicplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public final class UpdateNotifications {
    public static final String CHANNEL_ID = "nexauren_updates";

    private UpdateNotifications() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel=new NotificationChannel(
                        CHANNEL_ID,"Atualizações do Nexauren",NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Novas versões, progresso e instalação do Nexauren Music Player.");
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void notify(Context context,int id,Notification notification){
        ensureChannel(context);
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(manager!=null)manager.notify(id,notification);
    }
}
