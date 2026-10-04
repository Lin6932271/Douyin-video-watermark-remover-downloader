package com.aojiao.shiying;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class VideoInfo {
    public String id = "", title = "", author = "", source = "";
    public final List<String> urls = new ArrayList<>();
    public JSONObject toJson() throws Exception {
        return new JSONObject().put("id", id).put("title", title).put("author", author)
            .put("source", source).put("urls", new JSONArray(urls));
    }
    public static VideoInfo fromJson(String json) throws Exception {
        JSONObject object = new JSONObject(json);
        VideoInfo result = new VideoInfo();
        result.id = object.optString("id"); result.title = object.optString("title");
        result.author = object.optString("author"); result.source = object.optString("source");
        JSONArray array = object.optJSONArray("urls");
        if (array != null) for (int i = 0; i < array.length(); i++) {
            String url = array.optString(i);
            if (LinkTools.isMediaUrl(url)) result.urls.add(url);
        }
        return result;
    }
}
