package com.aojiao.shiying;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public final class HttpTransport {
    public static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    public static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    public interface Cookies { String get(String url); }
    public static final class Response {
        public String body, finalUrl;
        public int status;
    }
    private final Cookies cookies;
    public HttpTransport(Cookies cookies) { this.cookies = cookies; }
    public HttpURLConnection open(String initial, boolean media, String ua) throws IOException {
        String url = initial;
        for (int redirects = 0; redirects < 9; redirects++) {
            if (!(media ? LinkTools.isMediaUrl(url) : LinkTools.isShareUrl(url))) throw new IOException("跳转到了不支持的域名");
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(12000); connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", ua);
            connection.setRequestProperty("Referer", "https://www.douyin.com/");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            connection.setRequestProperty("Accept-Encoding", "identity");
            String cookie = cookies == null ? "" : cookies.get(url);
            if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("跳转响应缺少地址");
                url = new URL(new URL(url), location).toString();
                if (url.startsWith("http://")) url = "https://" + url.substring(7);
            } else return connection;
        }
        throw new IOException("分享链接跳转次数过多");
    }
    public Response get(String url, String ua) throws IOException {
        HttpURLConnection connection = open(url, false, ua);
        try {
            Response response = new Response();
            response.status = connection.getResponseCode(); response.finalUrl = connection.getURL().toString();
            if (response.status != 200) throw new IOException("页面请求失败，HTTP " + response.status);
            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) stream = new GZIPInputStream(stream);
            try (InputStream in = stream) { response.body = readText(in, 8 * 1024 * 1024); }
            return response;
        } finally { connection.disconnect(); }
    }
    public static String readText(InputStream stream, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IOException("响应内容过大");
            output.write(buffer, 0, count);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
