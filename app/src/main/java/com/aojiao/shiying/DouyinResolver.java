package com.aojiao.shiying;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DouyinResolver {
    private final HttpTransport http;
    public DouyinResolver(HttpTransport.Cookies cookies) { http = new HttpTransport(cookies); }
    public VideoInfo resolve(String input) throws Exception {
        String share = LinkTools.extract(input);
        HttpTransport.Response response = http.get(share, HttpTransport.MOBILE_UA);
        String id = LinkTools.videoId(response.finalUrl);
        VideoInfo result = PageParser.parse(response.body, id);
        if (result != null) { result.source = share; return result; }
        Set<String> pages = new LinkedHashSet<>();
        if (!id.isEmpty()) {
            pages.add("https://www.iesdouyin.com/share/video/" + id + "/");
            pages.add("https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=" + id);
        }
        for (String page : pages) {
            try {
                response = http.get(page, page.contains("/aweme/") ? HttpTransport.DESKTOP_UA : HttpTransport.MOBILE_UA);
                result = PageParser.parse(response.body, id);
                if (result != null) { result.source = share; return result; }
            } catch (IOException ignored) { }
        }
        throw new IOException("页面没有返回可下载的播放资源。请在内置网页中完成验证或播放视频，再点“读取当前页面”。");
    }
    public VideoInfo cloudResolve(String input, String apiKey) throws Exception {
        String share = LinkTools.extract(input);
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IOException("尚未填写 Firecrawl API Key");
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.firecrawl.dev/v2/scrape").openConnection();
        connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(45000);
        connection.setInstanceFollowRedirects(false); connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        connection.setRequestProperty("Content-Type", "application/json");
        JSONObject request = new JSONObject().put("url", share).put("formats", new JSONArray().put("rawHtml"))
            .put("onlyMainContent", false).put("mobile", true).put("maxAge", 0).put("timeout", 30000).put("waitFor", 2000);
        try {
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(request.toString().getBytes(StandardCharsets.UTF_8)); }
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Firecrawl 请求失败，HTTP " + status + "；检查 Key 和额度");
            String text;
            try (InputStream in = connection.getInputStream()) { text = HttpTransport.readText(in, 8 * 1024 * 1024); }
            JSONObject data = new JSONObject(text);
            if (!data.optBoolean("success")) throw new IOException("Firecrawl 未返回成功结果");
            JSONObject page = data.optJSONObject("data");
            if (page == null) throw new IOException("Firecrawl 响应缺少页面内容");
            String finalUrl = page.optJSONObject("metadata") == null ? share : page.getJSONObject("metadata").optString("sourceURL", share);
            String id = LinkTools.videoId(finalUrl);
            VideoInfo video = PageParser.parse(page.optString("rawHtml"), id);
            if (video == null) throw new IOException("云端页面也没有播放资源，可改用内置网页解析");
            video.source = share; return video;
        } finally { connection.disconnect(); }
    }
}
