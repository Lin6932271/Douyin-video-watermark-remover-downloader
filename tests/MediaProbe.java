package com.aojiao.shiying;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONObject;

public final class MediaProbe {
    public static void main(String[] args) throws Exception {
        String raw = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
        VideoInfo video = PageParser.fromBrowserSnapshot(raw, "7409533098766896422");
        if (video == null) throw new Exception("Rendered page has no media URL");
        Exception failure = null;
        for (String url : video.urls) {
            HttpURLConnection connection = null;
            try {
                connection = new HttpTransport(null).open(url, true, HttpTransport.MOBILE_UA);
                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
                long expected = connection.getContentLengthLong(), total = 0;
                try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(Paths.get(args[1]))) {
                    byte[] buffer = new byte[65536]; int n;
                    while ((n = in.read(buffer)) != -1) { out.write(buffer, 0, n); total += n; }
                }
                if (expected > 0 && total != expected) throw new Exception("Truncated MP4");
                JSONObject report = new JSONObject().put("video_id", video.id).put("status", 200).put("bytes", total)
                    .put("expected_bytes", expected).put("source_host", new java.net.URI(url).getHost());
                Files.write(Paths.get(args[2]), report.toString(2).getBytes("UTF-8"));
                System.out.println(report); return;
            } catch (Exception e) { failure = e; } finally { if (connection != null) connection.disconnect(); }
        }
        throw new Exception("All rendered media addresses failed", failure);
    }
}
