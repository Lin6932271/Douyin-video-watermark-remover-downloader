package com.aojiao.shiying;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class VideoInfo {
    public String id = "", title = "", author = "", source = "";
    public final List<String> urls = new ArrayList<>();
    public final List<MediaItem> items = new ArrayList<>();
    public String warning = "";
    public List<MediaItem> mediaItems() {
        if (!items.isEmpty()) return items;
        List<MediaItem> result = new ArrayList<>();
        if (!urls.isEmpty()) {
            MediaItem item = new MediaItem(); item.kind = "video"; item.urls.addAll(urls); result.add(item);
        }
        return result;
    }
    public String summary() {
        int images = 0, motion = 0, videos = 0;
        for (MediaItem item : mediaItems()) {
            if ("live_video".equals(item.kind)) motion++;
            else if (item.isVideo()) videos++;
            else images++;
        }
        return "图片/动图 " + images + " · 实况片段 " + motion + " · 视频 " + videos;
    }
    public JSONObject toJson() throws Exception {
        JSONArray media = new JSONArray();
        for (MediaItem item : items) media.put(item.toJson());
        return new JSONObject().put("id", id).put("title", title).put("author", author)
            .put("source", source).put("urls", new JSONArray(urls)).put("items", media).put("warning", warning);
    }
    public static VideoInfo fromJson(String json) throws Exception {
        JSONObject object = new JSONObject(json);
        VideoInfo result = new VideoInfo();
        result.id = object.optString("id"); result.title = object.optString("title");
        result.author = object.optString("author"); result.source = object.optString("source");
        result.warning = object.optString("warning");
        JSONArray array = object.optJSONArray("urls");
        if (array != null) for (int i = 0; i < array.length(); i++) {
            String url = array.optString(i);
            if (LinkTools.isMediaUrl(url)) result.urls.add(url);
        }
        JSONArray media = object.optJSONArray("items");
        if (media != null) for (int i = 0; i < Math.min(media.length(), 2000); i++) {
            MediaItem item = MediaItem.fromJson(media.getJSONObject(i));
            if (!item.urls.isEmpty()) result.items.add(item);
        }
        return result;
    }
}
