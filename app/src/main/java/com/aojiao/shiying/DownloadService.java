package com.aojiao.shiying;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.IBinder;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DownloadService extends Service {
    public static final String EVENT = "com.aojiao.shiying.DOWNLOAD_EVENT";
    public static final String CANCEL = "com.aojiao.shiying.CANCEL";
    public static volatile boolean running;
    private static final String CHANNEL = "video_downloads";
    private static final int NOTICE = 41;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile HttpURLConnection activeConnection;
    private NotificationManager notifications;
    @Override public void onCreate() {
        super.onCreate();
        notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "视频下载", NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notification(String text, int progress, boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_download)
            .setContentTitle("拾影 · 视频下载").setContentText(text).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(ongoing);
        if (ongoing) {
            PendingIntent cancel = PendingIntent.getService(this, 1, new Intent(this, DownloadService.class).setAction(CANCEL), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setProgress(100, Math.max(0, progress), progress < 0).addAction(new Notification.Action.Builder(null, "取消", cancel).build());
        }
        return builder.build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (CANCEL.equals(intent.getAction())) {
            cancelled.set(true);
            HttpURLConnection connection = activeConnection;
            if (connection != null) connection.disconnect();
            if (!running) stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        running = true; cancelled.set(false);
        startForeground(NOTICE, notification("正在连接播放资源…", -1, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        final String json = intent.getStringExtra("video");
        executor.execute(() -> {
            try {
                VideoInfo video = VideoInfo.fromJson(json);
                if (video.urls.isEmpty()) throw new IOException("没有有效的视频地址，请重新解析");
                Uri saved = download(video);
                addHistory(video, saved);
                publish("done", "已保存到相册 / Movies/拾影", 100, saved.toString());
                notifications.notify(NOTICE + 1, notification("视频已保存到相册", 100, false));
            } catch (Exception e) {
                publish("error", cancelled.get() ? "下载已取消" : "下载失败：" + readable(e), 0, "");
            } finally {
                running = false; activeConnection = null;
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            }
        });
        return START_NOT_STICKY;
    }
    private Uri download(VideoInfo video) throws Exception {
        Exception last = null;
        HttpTransport http = new HttpTransport(url -> CookieManager.getInstance().getCookie(url));
        for (String url : video.urls) {
            if (cancelled.get()) throw new IOException("已取消");
            Uri destination = null;
            HttpURLConnection connection = null;
            try {
                connection = http.open(url, true, HttpTransport.MOBILE_UA); activeConnection = connection;
                if (connection.getResponseCode() != 200) throw new IOException("播放资源 HTTP " + connection.getResponseCode());
                long expected = connection.getContentLengthLong();
                try (PushbackInputStream in = new PushbackInputStream(connection.getInputStream(), 32)) {
                    byte[] header = new byte[12]; int got = 0, count;
                    while (got < header.length && (count = in.read(header, got, header.length - got)) > 0) got += count;
                    if (got < 12 || header[4] != 'f' || header[5] != 't' || header[6] != 'y' || header[7] != 'p') {
                        throw new IOException("返回内容不是 MP4，可能是验证页面或失效地址");
                    }
                    in.unread(header, 0, got);
                    ContentValues values = new ContentValues();
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
                    values.put(MediaStore.Video.Media.DISPLAY_NAME, "拾影_" + timestamp + "_" + video.id + ".mp4");
                    values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/拾影");
                    values.put(MediaStore.Video.Media.IS_PENDING, 1);
                    destination = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                    if (destination == null) throw new IOException("无法创建相册文件，检查剩余空间");
                    OutputStream stream = getContentResolver().openOutputStream(destination, "w");
                    if (stream == null) throw new IOException("无法写入相册文件");
                    long downloaded = 0, lastUpdate = 0;
                    try (OutputStream out = stream) {
                        byte[] buffer = new byte[65536];
                        while ((count = in.read(buffer)) != -1) {
                            if (cancelled.get()) throw new IOException("已取消");
                            out.write(buffer, 0, count); downloaded += count;
                            long now = System.currentTimeMillis();
                            if (now - lastUpdate > 400) {
                                lastUpdate = now;
                                int progress = expected > 0 ? (int) Math.min(99, downloaded * 100 / expected) : -1;
                                String message = String.format(Locale.CHINA, "已下载 %.1f MB%s", downloaded / 1048576.0,
                                    expected > 0 ? String.format(Locale.CHINA, " / %.1f MB", expected / 1048576.0) : "");
                                publish("progress", message, progress, "");
                                notifications.notify(NOTICE, notification(message, progress, true));
                            }
                        }
                    }
                    if (cancelled.get()) throw new IOException("已取消");
                    if (downloaded < 1024 || (expected > 0 && downloaded != expected)) throw new IOException("视频文件不完整，请重试");
                    ContentValues complete = new ContentValues(); complete.put(MediaStore.Video.Media.IS_PENDING, 0);
                    if (getContentResolver().update(destination, complete, null, null) == 0) throw new IOException("相册文件提交失败");
                    return destination;
                }
            } catch (Exception e) {
                if (destination != null) try { getContentResolver().delete(destination, null, null); } catch (Exception ignored) { }
                last = e;
                if (cancelled.get()) throw e;
            } finally {
                activeConnection = null;
                if (connection != null) connection.disconnect();
            }
        }
        throw new IOException("所有播放地址均不可用；重新解析可刷新地址。" + (last == null ? "" : readable(last)), last);
    }
    private void addHistory(VideoInfo video, Uri uri) {
        try {
            JSONArray old = new JSONArray(getSharedPreferences("downloads", MODE_PRIVATE).getString("history", "[]"));
            JSONArray updated = new JSONArray();
            updated.put(new JSONObject().put("title", video.title).put("id", video.id).put("uri", uri.toString())
                .put("time", System.currentTimeMillis()));
            for (int i = 0; i < Math.min(29, old.length()); i++) updated.put(old.get(i));
            getSharedPreferences("downloads", MODE_PRIVATE).edit().putString("history", updated.toString()).apply();
        } catch (Exception ignored) { }
    }
    private void publish(String state, String message, int progress, String uri) {
        sendBroadcast(new Intent(EVENT).setPackage(getPackageName()).putExtra("state", state)
            .putExtra("message", message).putExtra("progress", progress).putExtra("uri", uri));
    }
    private static String readable(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
    @Override public void onTimeout(int startId, int fgsType) {
        cancelled.set(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        cancelled.set(true); executor.shutdown();
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
