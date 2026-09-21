package com.nexauren.musicplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class ArtworkStore {
    private ArtworkStore() {}

    public static File file(Context context, long id) {
        File dir = new File(context.getFilesDir(), "artwork");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "track_" + id + ".jpg");
    }

    public static boolean exists(Context context, long id) {
        return file(context, id).isFile();
    }

    public static boolean save(Context context, long id, InputStream input) {
        File target = file(context, id);
        try {
            Bitmap source = BitmapFactory.decodeStream(input);
            if (source == null) return false;
            int max = 1200;
            Bitmap bitmap = source;
            if (source.getWidth() > max || source.getHeight() > max) {
                float scale = Math.min(max / (float) source.getWidth(), max / (float) source.getHeight());
                bitmap = Bitmap.createScaledBitmap(source,
                        Math.max(1, Math.round(source.getWidth() * scale)),
                        Math.max(1, Math.round(source.getHeight() * scale)), true);
            }
            try (FileOutputStream out = new FileOutputStream(target)) {
                return bitmap.compress(CompressFormat.JPEG, 92, out);
            } finally {
                if (bitmap != source) bitmap.recycle();
                source.recycle();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static void clear(Context context, long id) {
        File f = file(context, id);
        if (f.exists()) f.delete();
    }
}
