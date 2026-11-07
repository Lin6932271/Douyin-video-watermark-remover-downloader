package com.aojiao.shiying;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Trust bytes, not CDN suffixes or Content-Type (which can describe an HTML challenge). */
public final class MediaFormat {
    public final String mime, extension;
    public final boolean video;
    private MediaFormat(String mime, String extension, boolean video) {
        this.mime = mime; this.extension = extension; this.video = video;
    }
    private static String ascii(byte[] b, int offset, int length) {
        return new String(b, offset, length, StandardCharsets.US_ASCII);
    }
    public static MediaFormat detect(byte[] b, int n, boolean expectVideo) throws IOException {
        MediaFormat format = null;
        if (n >= 12 && ascii(b, 4, 4).equals("ftyp")) {
            String brand = ascii(b, 8, 4);
            String brands = ascii(b, 8, Math.min(n, 64) - 8);
            if (brands.contains("avif") || brands.contains("avis")) format = new MediaFormat("image/avif", ".avif", false);
            else if (brand.matches("heic|heix|hevc|hevx|mif1|msf1")) format = new MediaFormat("image/heic", ".heic", false);
            else if (brand.matches("isom|iso[2-9]|mp4[12]|avc1|M4V |MSNV|dash|qt  "))
                format = new MediaFormat(brand.equals("qt  ") ? "video/quicktime" : "video/mp4", brand.equals("qt  ") ? ".mov" : ".mp4", true);
        } else if (n >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
            format = new MediaFormat("image/jpeg", ".jpg", false);
        else if (n >= 8 && (b[0] & 255) == 137 && ascii(b, 1, 3).equals("PNG") && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10)
            format = new MediaFormat("image/png", ".png", false);
        else if (n >= 6 && (ascii(b, 0, 6).equals("GIF89a") || ascii(b, 0, 6).equals("GIF87a")))
            format = new MediaFormat("image/gif", ".gif", false);
        else if (n >= 12 && ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP"))
            format = new MediaFormat("image/webp", ".webp", false);
        if (format == null || format.video != expectVideo)
            throw new IOException("返回内容不是预期的" + (expectVideo ? "视频" : "图片/动图") + "，可能是验证页或地址已过期");
        return format;
    }
}
