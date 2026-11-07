package com.aojiao.shiying;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** One file with alternate CDN URLs, not one file per URL. */
public final class MediaItem {
    public String kind = "image";
    public int index = 1;
    public final List<String> urls = new ArrayList<>();
    public boolean isVideo() { return "video".equals(kind) || "live_video".equals(kind); }
    public String label() {
        return String.format(java.util.Locale.ROOT, "%03d · %s", index,
            "live_video".equals(kind) ? "实况动态片段" : "video".equals(kind) ? "视频" : "animation".equals(kind) ? "动图" : "图片");
    }
    public JSONObject toJson() throws Exception {
        return new JSONObject().put("kind", kind).put("index", index).put("urls", new JSONArray(urls));
    }
    public static MediaItem fromJson(JSONObject obj) throws Exception {
        MediaItem item = new MediaItem();
        item.kind = obj.optString("kind", "image");
        if (!item.kind.matches("image|animation|live_video|video")) throw new IllegalArgumentException("无效素材类型");
        item.index = obj.optInt("index", 1);
        if (item.index < 1 || item.index > 1000) throw new IllegalArgumentException("无效素材序号");
        JSONArray urls = obj.optJSONArray("urls");
        if (urls != null) for (int i = 0; i < Math.min(urls.length(), 32); i++) {
            String url = urls.optString(i);
            if (LinkTools.isMediaUrl(url) && !item.urls.contains(url)) item.urls.add(url);
        }
        return item;
    }
}
