package com.aojiao.shiying;
public final class ResolverProbe {
    public static void main(String[] args) throws Exception {
        VideoInfo info = new DouyinResolver(null).resolve(args[0]);
        System.out.println(info.toJson().toString());
    }
}
