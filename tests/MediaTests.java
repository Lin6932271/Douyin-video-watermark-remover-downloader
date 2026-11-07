package com.aojiao.shiying;

import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MediaTests {
    private static int count;
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        count++; System.out.println("PASS " + name);
    }
    public static void main(String[] args) throws Exception {
        JSONArray cases = new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8"));
        for (int i = 0; i < cases.length(); i++) {
            JSONObject test = cases.getJSONObject(i);
            VideoInfo info = PageParser.parse(test.get("data").toString(), test.getString("target"));
            JSONArray expected = test.getJSONArray("kinds");
            check((info == null ? 0 : info.mediaItems().size()) == expected.length(), test.getString("name") + " count");
            if (info != null) {
                for (int j = 0; j < expected.length(); j++) check(info.mediaItems().get(j).kind.equals(expected.getString(j)), test.getString("name") + " kind " + j);
                check(VideoInfo.fromJson(info.toJson().toString()).mediaItems().size() == expected.length(), test.getString("name") + " roundtrip");
                if (test.has("warning")) check(!info.warning.isEmpty(), test.getString("name") + " warning");
                if (test.getString("name").contains("animated original")) check(info.items.get(0).urls.size() == 1, "animation never silently falls back to a static cover");
            }
        }
        String[] headers = {"\u00ff\u00d8\u00ff", "\u0089PNG\r\n\u001a\n", "GIF89a", "RIFFxxxxWEBP", "xxxxftypisom", "xxxxftypheic", "xxxxftypavif"};
        String[] extensions = {".jpg", ".png", ".gif", ".webp", ".mp4", ".heic", ".avif"};
        for (int i = 0; i < headers.length; i++) {
            byte[] raw = headers[i].getBytes(StandardCharsets.ISO_8859_1);
            check(MediaFormat.detect(raw, raw.length, i == 4).extension.equals(extensions[i]), "sniff " + extensions[i]);
        }
        for (String invalid : new String[]{"<html>challenge</html>", "xxxxftypheic", "GIF87a"}) {
            boolean rejected = false;
            try { byte[] b = invalid.getBytes("US-ASCII"); MediaFormat.detect(b, b.length, true); } catch (java.io.IOException e) { rejected = true; }
            check(rejected, "not video: " + invalid);
        }
        check(!LinkTools.isMediaUrl("https://douyinpic.com.evil.invalid/a.jpg"), "picture lookalike blocked");
        check(!LinkTools.isMediaUrl("https://douyinpic.com@evil.invalid/a.jpg"), "picture userinfo blocked");
        check(LinkTools.isMediaUrl("https://p3-pc-sign.douyinpic.com/a.webp"), "picture CDN allowed");
        check(LinkTools.videoId("https://www.iesdouyin.com/share/slides/7000000000000000001/?region=CN").equals("7000000000000000001"), "actual mobile redirect slides ID");
        check(LinkTools.videoId("https://www.douyin.com/video/111111111111111111111111111111").isEmpty(), "overlong IDs rejected");
        String note = new JSONObject().put("href", "https://www.douyin.com/note/7000000000000000001")
            .put("resources", new JSONArray().put("https://v3-web.douyinvod.com/recommendation.mp4")).toString();
        check(PageParser.fromBrowserSnapshot(note, "7000000000000000001") == null, "album never becomes unrelated performance video");
        if (args.length > 2) {
            // Local-only real sample: args[1] = saved detail response, args[2] = its share redirect URL (both under evidence/, never committed)
            String realId = LinkTools.videoId(args[2]);
            VideoInfo real = PageParser.parse(new String(Files.readAllBytes(Paths.get(args[1])), "UTF-8"), realId);
            check(real != null && real.items.size() == 4, "user actual response: two stills plus two live clips");
            check(real.items.get(0).urls.size() >= 2 && real.items.get(1).kind.equals("live_video"), "user actual CDN alternatives and live pair");
        }
        System.out.println("MEDIA_TESTS_PASSED=" + count);
    }
}
