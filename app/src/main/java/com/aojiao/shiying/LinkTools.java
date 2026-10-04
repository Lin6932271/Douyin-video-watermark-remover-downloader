package com.aojiao.shiying;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinkTools {
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"'，。！？；（）【】]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("/(?:share/)?(?:video|note)/(\\d{10,25})");
    private LinkTools() {}
    private static boolean hostIn(String host, String[] domains) {
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String domain : domains) if (host.equals(domain) || host.endsWith("." + domain)) return true;
        return false;
    }
    private static boolean safe(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme()) && uri.getRawUserInfo() == null
            && (uri.getPort() == -1 || uri.getPort() == 443);
    }
    public static boolean isShareUrl(String value) {
        try {
            URI uri = URI.create(value);
            return safe(uri) && hostIn(uri.getHost(), new String[]{"douyin.com", "iesdouyin.com", "amemv.com"});
        } catch (Exception e) { return false; }
    }
    public static boolean isMediaUrl(String value) {
        try {
            URI uri = URI.create(value);
            return safe(uri) && hostIn(uri.getHost(), new String[]{"douyinvod.com", "douyin.com", "iesdouyin.com",
                "amemv.com", "snssdk.com", "bytecdn.cn", "bytecdn.com", "bytedance.com", "zijieapi.com", "pstatp.com", "bytedance.net"})
                && !value.contains(".m3u8") && !value.contains("/playwm/");
        } catch (Exception e) { return false; }
    }
    public static String extract(String text) {
        if (text == null || text.length() > 32768) throw new IllegalArgumentException("分享文本为空或过长");
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            String url = matcher.group().replaceAll("[)\\]}>.,;!]+$", "");
            if (url.startsWith("http://")) url = "https://" + url.substring(7);
            if (isShareUrl(url)) return url;
        }
        throw new IllegalArgumentException("没有找到抖音链接，请粘贴完整分享文本");
    }
    public static String videoId(String value) {
        if (value == null) return "";
        Matcher matcher = ID.matcher(value);
        if (matcher.find()) return matcher.group(1);
        matcher = Pattern.compile("(?:[?&])(?:modal_id|aweme_id|item_ids)=(\\d{10,25})").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }
    public static String decodeData(String value) throws Exception {
        return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8");
    }
    public static String normalizePlayback(String url) {
        if (url == null) return "";
        url = url.replace("&amp;", "&");
        if (url.startsWith("//")) url = "https:" + url;
        if (url.startsWith("http://")) url = "https://" + url.substring(7);
        return url.replace("/playwm/", "/play/");
    }
}
