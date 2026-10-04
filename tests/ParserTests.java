package com.aojiao.shiying;

import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URLEncoder;

public final class ParserTests {
    private static int count;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        count++; System.out.println("PASS " + name);
    }
    private static String fixture(String id, String extra, String urls) {
        return "{\"aweme_id\":\"" + id + "\",\"desc\":\"sample + clip\",\"author\":{\"nickname\":\"tester\"}," + extra
            + "\"video\":{\"play_addr\":{\"url_list\":" + urls + "}}}";
    }
    public static void main(String[] args) throws Exception {
        String id = "7409533098766896422";
        String url = "https://v3-web.douyinvod.com/video/tos/cn/test.mp4?x=1&y=2";
        String json = fixture(id, "", "[\"" + url + "\"]");
        check(LinkTools.extract("分享视频 https://v.douyin.com/HOpFK-XHaEg/ 复制打开").equals("https://v.douyin.com/HOpFK-XHaEg/"), "share text extraction");
        check(LinkTools.videoId("https://www.iesdouyin.com/share/video/" + id + "/").equals(id), "redirect video id");
        check(LinkTools.videoId("https://www.douyin.com/?modal_id=" + id).equals(id), "modal video id");
        check(!LinkTools.isShareUrl("https://douyin.com.evil.invalid/video/123"), "lookalike domain rejected");
        check(!LinkTools.isShareUrl("https://douyin.com@evil.invalid/"), "userinfo domain rejected");
        check(!LinkTools.isMediaUrl("https://127.0.0.1/a.mp4"), "local media address rejected");
        check(!LinkTools.isMediaUrl("https://v3-web.douyinvod.com:8443/a.mp4"), "unexpected port rejected");
        VideoInfo video = PageParser.parse(json, id);
        check(video != null && video.urls.get(0).equals(url) && video.author.equals("tester"), "structured playback parsing");
        check(PageParser.parse("<script>window._ROUTER_DATA = {\"loaderData\":{\"video_(id)/page\":{\"videoInfoRes\":{\"item_list\":[" + json + "]}}}};</script>", id) != null, "router data parsing");
        check(PageParser.parse("<script id=\"RENDER_DATA\">" + URLEncoder.encode(json, "UTF-8").replace("+", "%20") + "</script>", id).title.equals("sample + clip"), "percent-encoded render data and plus preservation");
        check(PageParser.parse(json, "7409533098766896000") == null, "different video excluded");
        check(PageParser.parse(fixture(id, "\"video_control\":{\"allow_download\":false},", "[\"" + url + "\"]"), id) != null, "creator download switch does not hide public playback resource");
        check(PageParser.parse(fixture(id, "\"status\":{\"is_private\":true},", "[\"" + url + "\"]"), id) == null, "private video rejected");
        check(PageParser.parse("{\"aweme_id\":\"" + id + "\",\"video\":{\"download_addr\":{\"url_list\":[\"" + url + "\"]}}}", id) == null, "watermarked download field excluded");
        check(PageParser.parse(fixture(id, "", "[\"https://evil.invalid/video.mp4\"]"), id) == null, "untrusted CDN excluded");
        String wm = "https://aweme.snssdk.com/aweme/v1/playwm/?video_id=abc";
        check(PageParser.parse(fixture(id, "", "[\"" + wm + "\"]"), id).urls.get(0).contains("/play/"), "playwm normalized to playback endpoint");
        check(PageParser.parse("", id) == null && PageParser.parse("<html>verification</html>", id) == null, "empty response and challenge handled");
        check(VideoInfo.fromJson(video.toJson().toString()).urls.equals(video.urls), "service payload round trip");
        String snapshot = new JSONObject().put("href", "https://www.douyin.com/video/" + id).put("title", "sample")
            .put("videoSrc", "blob:https://www.douyin.com/local").put("resources", new org.json.JSONArray().put(url)).toString();
        check(PageParser.fromBrowserSnapshot(snapshot, id).urls.get(0).equals(url), "browser resource fallback for blob playback");
        boolean rejected = false;
        try { PageParser.fromBrowserSnapshot(snapshot, "7409533098766896000"); } catch (Exception expected) { rejected = true; }
        check(rejected, "browser navigation to a different video rejected");
        if (args.length > 0) {
            String actual = new String(Files.readAllBytes(Paths.get(args[0])), "UTF-8");
            VideoInfo live = PageParser.parse(actual, id);
            check(live != null && live.id.equals(id), "real sample response parses");
            System.out.println("LIVE_VIDEO=" + live.toJson());
        }
        if (args.length > 1) {
            String actual = new String(Files.readAllBytes(Paths.get(args[1])), "UTF-8");
            check(PageParser.fromBrowserSnapshot(actual, id) != null, "real rendered browser snapshot parses");
        }
        System.out.println("TESTS_PASSED=" + count);
    }
}
