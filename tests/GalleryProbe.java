package com.aojiao.shiying;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.file.*;

/** Desktop integration probe using the exact resolver, CDN guards and format sniffer shipped in Android. */
public final class GalleryProbe {
    public static void main(String[] args) throws Exception {
        VideoInfo post = new DouyinResolver(null).resolve(args[0]);
        Path folder = Paths.get(args[1]); Files.createDirectories(folder);
        JSONArray files = new JSONArray();
        for (MediaItem item : post.mediaItems()) {
            Exception last = null;
            boolean saved = false;
            for (String url : item.urls) {
                HttpURLConnection c = null;
                Path temporary = folder.resolve("download.part");
                try {
                    c = new HttpTransport(null).open(url, true, HttpTransport.MOBILE_UA);
                    if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                    long size = 0, expected = c.getContentLengthLong();
                    MediaFormat format;
                    try (PushbackInputStream in = new PushbackInputStream(c.getInputStream(), 64); OutputStream out = Files.newOutputStream(temporary)) {
                        byte[] header = new byte[64]; int got = 0, n;
                        while (got < 64 && (n = in.read(header, got, 64 - got)) > 0) got += n;
                        format = MediaFormat.detect(header, got, item.isVideo()); in.unread(header, 0, got);
                        byte[] buffer = new byte[65536];
                        while ((n = in.read(buffer)) != -1) { out.write(buffer, 0, n); size += n; }
                    }
                    if (expected > 0 && size != expected) throw new IOException("Incomplete media");
                    Path path = folder.resolve(String.format("%03d_%s%s", item.index, item.kind, format.extension));
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                    files.put(new JSONObject().put("file", path.getFileName()).put("mime", format.mime).put("bytes", size)
                        .put("expected_bytes", expected).put("host", new URI(url).getHost()));
                    saved = true; break;
                } catch (Exception e) { last = e; Files.deleteIfExists(temporary); }
                finally { if (c != null) c.disconnect(); }
            }
            if (!saved) throw new IOException(item.label() + " all URLs failed", last);
        }
        JSONObject result = new JSONObject().put("id", post.id).put("summary", post.summary()).put("files", files)
            .put("scope", "Desktop resolver/HTTP/format validation; not Android MediaStore or device validation");
        Files.write(folder.resolve("report.json"), result.toString(2).getBytes("UTF-8"));
        System.out.println(result.toString(2));
    }
}
