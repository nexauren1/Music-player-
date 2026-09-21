package com.nexauren.musicplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class UpdateManager {
    public static final String REPO = "nexauren1/Music-player-";
    public static final String ACTION_DOWNLOAD_UPDATE = "com.nexauren.musicplayer.DOWNLOAD_UPDATE";
    public static final String ACTION_INSTALL_UPDATE = "com.nexauren.musicplayer.INSTALL_UPDATE";
    public static final String EXTRA_VERSION = "update_version";
    public static final String EXTRA_URL = "update_url";
    public static final String EXTRA_NOTES = "update_notes";
    public static final String EXTRA_DIGEST = "update_digest";
    public static final String WORK_DOWNLOAD = "nexauren-update-download";
    public static final String PREFS = "nexauren_updates";

    private UpdateManager() {}

    public interface Callback {
        void onResult(ReleaseInfo info);
        void onError(Exception error);
    }

    public static final class ReleaseInfo {
        public final String version;
        public final String name;
        public final String notes;
        public final String apkUrl;
        public final String digest;

        ReleaseInfo(String version, String name, String notes, String apkUrl, String digest) {
            this.version = version;
            this.name = name;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.digest = digest;
        }
    }

    public static void checkAsync(Context context, Callback callback) {
        new Thread(() -> {
            try {
                ReleaseInfo info = fetchLatest();
                callback.onResult(info);
            } catch (Exception e) {
                callback.onError(e);
            }
        }, "nexauren-update-check").start();
    }

    public static ReleaseInfo fetchLatest() throws Exception {
        URL url = new URL("https://api.github.com/repos/" + REPO + "/releases/latest");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Nexauren-Music-Player");
        connection.setInstanceFollowRedirects(true);
        try (InputStream input = connection.getInputStream()) {
            String json = readUtf8(input);
            JSONObject root = new JSONObject(json);
            String version = root.optString("tag_name", "");
            if (version.startsWith("v")) version = version.substring(1);
            JSONArray assets = root.optJSONArray("assets");
            String apkUrl = "";
            String digest = "";
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String assetName = asset.optString("name", "");
                    if ("app-release.apk".equals(assetName) || assetName.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "");
                        digest = asset.optString("digest", "");
                        break;
                    }
                }
            }
            if (version.isEmpty() || apkUrl.isEmpty()) {
                throw new IllegalStateException("A release mais recente não contém um APK utilizável.");
            }
            return new ReleaseInfo(
                    version,
                    root.optString("name", "Nexauren Music Player " + version),
                    root.optString("body", "Nova atualização disponível."),
                    apkUrl,
                    digest
            );
        } finally {
            connection.disconnect();
        }
    }

    public static boolean isNewer(String remote, String local) {
        int[] r = versionParts(remote);
        int[] l = versionParts(local);
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) return rv > lv;
        }
        return false;
    }

    private static int[] versionParts(String value) {
        String clean = value == null ? "0" : value.replaceFirst("^[vV]", "");
        String[] parts = clean.split("[^0-9]+");
        int[] result = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try { result[n++] = Integer.parseInt(part); } catch (NumberFormatException ignored) { result[n++] = 0; }
        }
        int[] trimmed = new int[n];
        System.arraycopy(result, 0, trimmed, 0, n);
        return trimmed;
    }

    public static File updateFile(Context context) {
        File dir = new File(context.getCacheDir(), "updates");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "nexauren-latest.apk");
    }

    public static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) hex.append(String.format(Locale.US, "%02x", b));
        return hex.toString();
    }

    public static void install(Activity activity) {
        File apk = updateFile(activity);
        if (!apk.isFile() || apk.length() == 0) {
            android.widget.Toast.makeText(activity, "Nenhuma atualização baixada.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settingsIntent);
            activity.getPreferences(Context.MODE_PRIVATE).edit().putBoolean("install_after_settings", true).apply();
            android.widget.Toast.makeText(activity, "Permita a instalação do Nexauren e volte para continuar.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    "com.nexauren.musicplayer.fileprovider", apk, apk.getName());
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
        } catch (Exception e) {
            android.widget.Toast.makeText(activity, "Não foi possível abrir o instalador do Android.", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static String readUtf8(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toString("UTF-8");
    }
}
