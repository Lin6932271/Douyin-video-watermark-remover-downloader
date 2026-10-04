package com.aojiao.shiying;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PageParser {
    private PageParser() {}
    public static VideoInfo fromBrowserSnapshot(String raw, String expectedId) throws Exception {
        JSONObject snapshot = new JSONObject(raw);
        String href = snapshot.optString("href"), id = LinkTools.videoId(href);
        if (!LinkTools.isShareUrl(href) || id.isEmpty()) throw new Exception("请打开具体的视频页面");
        if (!expectedId.isEmpty() && !expectedId.equals(id)) throw new Exception("当前页面不是原视频，请返回原视频页面");
        VideoInfo found = null;
        JSONArray states = snapshot.optJSONArray("states");
        if (states != null) for (int i = 0; i < states.length() && found == null; i++) found = parse(states.optString(i), id);
        if (found != null) return found;
        String direct = LinkTools.normalizePlayback(snapshot.optString("videoSrc"));
        VideoInfo video = new VideoInfo(); video.id = id; video.title = snapshot.optString("title", "抖音视频");
        if (LinkTools.isMediaUrl(direct)) video.urls.add(direct);
        if (video.urls.isEmpty()) {
            JSONArray resources = snapshot.optJSONArray("resources");
            if (resources != null) for (int i = resources.length() - 1; i >= 0; i--) {
                String url = LinkTools.normalizePlayback(resources.optString(i));
                if (LinkTools.isMediaUrl(url) && !video.urls.contains(url)) video.urls.add(url);
            }
        }
        return video.urls.isEmpty() ? null : video;
    }
    public static VideoInfo parse(String text, String expectedId) throws Exception {
        if (text == null || text.length() > 8 * 1024 * 1024) return null;
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try { VideoInfo result = walk(new JSONTokener(trimmed).nextValue(), expectedId, 0);
                if (result != null) return result;
            } catch (Exception ignored) { }
        }
        Matcher scripts = Pattern.compile("<script\\b([^>]*)>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        while (scripts.find()) {
            String attrs = scripts.group(1), body = scripts.group(2).trim();
            List<String> candidates = new ArrayList<>();
            if (attrs.contains("RENDER_DATA")) {
                try { candidates.add(LinkTools.decodeData(body)); } catch (Exception ignored) { }
            } else if (body.startsWith("{") || body.startsWith("[")) candidates.add(body);
            Matcher assignment = Pattern.compile("(?:window\\.)?(?:_ROUTER_DATA|__INITIAL_STATE__|__NEXT_DATA__)\\s*=\\s*").matcher(body);
            while (assignment.find()) candidates.add(body.substring(assignment.end()));
            for (String candidate : candidates) {
                try {
                    VideoInfo result = walk(new JSONTokener(candidate).nextValue(), expectedId, 0);
                    if (result != null) return result;
                } catch (Exception ignored) { }
            }
        }
        return null;
    }
    private static VideoInfo walk(Object node, String expectedId, int depth) throws Exception {
        if (depth > 48) return null;
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            String id = object.optString("aweme_id", object.optString("awemeId", object.optString("itemId", object.optString("id", ""))));
            JSONObject video = object.optJSONObject("video");
            if (video != null && (!id.isEmpty()) && (expectedId.isEmpty() || expectedId.equals(id))) {
                JSONObject status = object.optJSONObject("status");
                if (status != null && (status.optBoolean("is_delete") || status.optBoolean("is_private"))) return null;
                VideoInfo info = extractVideo(object, video, id);
                if (!info.urls.isEmpty()) return info;
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                // Never descend into recommendations of a different identified video.
                if (video != null && !id.isEmpty() && !expectedId.isEmpty() && !expectedId.equals(id)) return null;
                Object value = object.opt(key);
                if (value instanceof JSONObject || value instanceof JSONArray) {
                    VideoInfo result = walk(value, expectedId, depth + 1);
                    if (result != null) return result;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < Math.min(array.length(), 1000); i++) {
                VideoInfo result = walk(array.opt(i), expectedId, depth + 1);
                if (result != null) return result;
            }
        }
        return null;
    }
    private static VideoInfo extractVideo(JSONObject object, JSONObject video, String id) throws Exception {
        VideoInfo info = new VideoInfo();
        info.id = id; info.title = object.optString("desc", object.optString("title", "抖音视频"));
        JSONObject author = object.optJSONObject("author");
        if (author != null) info.author = author.optString("nickname", author.optString("uniqueId"));
        addAddress(info, video.opt("play_addr_h264"));
        JSONArray bitrates = video.optJSONArray("bit_rate");
        if (bitrates == null) bitrates = video.optJSONArray("bitrateInfo");
        List<JSONObject> ordered = new ArrayList<>();
        if (bitrates != null) for (int i = 0; i < bitrates.length(); i++) {
            JSONObject item = bitrates.optJSONObject(i);
            if (item != null && !item.optString("gear_name").contains("bytevc2")) ordered.add(item);
        }
        ordered.sort((a, b) -> Long.compare(b.optLong("bit_rate", b.optLong("Bitrate")), a.optLong("bit_rate", a.optLong("Bitrate"))));
        for (JSONObject bitrate : ordered) addAddress(info, bitrate.opt("play_addr") != null ? bitrate.opt("play_addr") : bitrate.opt("PlayAddr"));
        addAddress(info, video.opt("play_addr"));
        addAddress(info, video.opt("playAddr"));
        addAddress(info, video.opt("play_addr_bytevc1"));
        // download_addr is deliberately excluded: it can contain a platform watermark.
        return info;
    }
    private static void addAddress(VideoInfo info, Object value) throws Exception {
        if (value instanceof String) { addUrl(info, (String) value); return; }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) addAddress(info, array.opt(i));
        } else if (value instanceof JSONObject) {
            JSONObject address = (JSONObject) value;
            JSONArray urls = address.optJSONArray("url_list");
            if (urls == null) urls = address.optJSONArray("UrlList");
            if (urls != null) addAddress(info, urls);
            addAddress(info, address.opt("src"));
            addAddress(info, address.opt("url"));
            if ((urls == null || urls.length() == 0) && address.optString("uri").matches("[A-Za-z0-9_-]{8,120}")) {
                addUrl(info, "https://aweme.snssdk.com/aweme/v1/play/?video_id=" + address.optString("uri") + "&ratio=1080p&line=0");
            }
        }
    }
    private static void addUrl(VideoInfo info, String value) {
        String url = LinkTools.normalizePlayback(value);
        if (LinkTools.isMediaUrl(url) && !info.urls.contains(url)) info.urls.add(url);
    }
}
