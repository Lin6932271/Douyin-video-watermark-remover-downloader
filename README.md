# Douyin video watermark remover downloader

**由 AI（OpenAI Codex）开发。** 用户提出需求并反馈安卓测试可用；AI 完成 Android / iOS 源码、构建脚本、解析测试和文档。本项目不声称所有平台已完成真机验收。

中文名称：**拾影 · 抖音视频保存器**。粘贴抖音分享链接，保存公开播放视频，不额外添加平台下载水印。名称中的 watermark remover 不表示能擦除已经烧入画面的水印。

| 平台 | 交付 | 验证状态 |
| --- | --- | --- |
| Android 10+ | [Releases 安装包](https://github.com/Lin6932271/Douyin-video-watermark-remover-downloader/releases/tag/v1.0.0)、Java 源码 | 构建 / 签名检查通过，用户反馈手机测试可用 |
| iOS 16+ | [Xcode 工程](ios/Shiying.xcodeproj)、SwiftUI 源码 | 解析回归与静态检查通过；未编译、签名或真机测试 |

**[完整使用文档](docs/USAGE.md)** · **[iOS 构建与使用说明](ios/README.md)**

**[直接下载 Android APK](https://github.com/Lin6932271/Douyin-video-watermark-remover-downloader/releases/download/v1.0.0/shiying-1.0.0.apk)**。正式安装包和 `SHA256SUMS.txt` 均位于 Releases 的 Assets 中；仓库 `dist` 目录另保留同一 APK。

> Android APK SHA-256：`d1f55a657a010c9b06efe1f1f3f050158f5f73537ef04dab10548de31839487b`

纯 Java 安卓应用，Android 10（API 29）及以上。没有广告、注册、统计 SDK 和自建解析服务器。

## 手机使用

1. 安装 `dist/shiying-1.0.0.apk`，启动“拾影”。允许通知后可在通知栏查看后台下载进度；没有通知权限也可在前台下载。
2. 在抖音复制视频分享链接，把整段分享文字粘贴进应用，点“解析视频”。也可通过系统分享菜单把文字发送到“拾影”。
3. 直接 HTTP 解析失败时会打开本机内置网页，自动读取播放资源。如果页面要求验证或登录，由你在网页完成；必要时播放视频数秒，再点“读取当前页面”。
4. 点“保存视频到相册”。文件保存在公共 `Movies/拾影/`，完成后在“最近保存”里播放或分享。
5. 下载可以在应用内或通知栏取消。失败、取消或不完整下载会删除未完成的相册条目。

## 功能边界

- 优先读取 `play_addr`、H.264 播放地址和网页的真实播放请求，不使用可能带平台水印的 `download_addr`。原文件保存，不重新编码、不添加水印。
- 作者关闭下载按钮不是客户端的下载门禁：公开可播放的视频只要返回可访问播放资源，会继续尝试保存。平台没有返回播放资源、资源已失效、视频被删除、需访问权限或受加密保护时，明确报错。
- 这不是画面去水印算法。作者已经烘焙在视频画面中的文字、Logo、剪辑软件水印仍会保留。
- 当前版本面向单条 MP4 视频；不包含图集、直播、HLS 分段合并、批量下载或自动登录。
- 内置网页不忽略 TLS 错误、不加载明文 HTTP、不允许文件访问；未暴露 JavaScript 原生桥。
- Firecrawl 是可选备用解析。仅点击“云端解析设置”并提供自己的 Key 后使用，分享链接会发送给该服务；本机登录 Cookie 不会发送，Key 不写入源码、APK 或文件。未配置 Key 不影响本机解析。

## 已执行验证（2026-10-04）

- 你提供的 `https://v.douyin.com/HOpFK-XHaEg/` 重定向到视频 ID `7409533098766896422`。
- 普通分享页缺少播放地址，匿名直调详情接口返回空响应；桌面浏览器执行页面后取得真实详情，且网页资源快照可被应用同一解析器读取。
- 使用应用同一 HTTP 下载组件从该页面实际播放地址下载 `499253` 字节 MP4；完整音视频解码退出码为 0，时长约 7.23 秒，576×1024、H.264 视频与 AAC 音频。
- 共 22 项解析回归通过，包含真实 API 响应和真实网页快照；`allow_download=false` 仅使用合成夹具验证没有阻断公共播放资源。
- 实测视频接口的 `allow_download=true`，不把这条样本冒充“作者关闭下载”实测。
- APK 构建、ZIP 完整性、zipalign、v3 签名、包名与 SDK 级别已核验。
- **用户反馈安卓测试可用**；开发端没有直接执行手机操作，也没有逐项真机覆盖 WebView 兼容性、前台服务、相册保存、通知交互和关闭下载的视频。桌面浏览器验证不等于开发端完成手机端全量验收。
- Firecrawl 未提供 Key，云端备用解析未进行真实服务验收。

## 本机重建

依赖：Windows、Python 3.9+、JDK 17。设置 `JAVA_HOME_17` 指向自己的 JDK 17；构建脚本也包含默认 Eclipse Adoptium 安装目录的回退。

```powershell
# 在下载或克隆后的项目根目录执行，按自己的安装位置设置 JDK。
$env:JAVA_HOME_17 = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
.\build.ps1 -Setup
python .\scripts\test.py
```

首次构建从 Google 官方 Android 仓库下载 API 35 和 Build Tools 35.0.0，并核对仓库提供的归档哈希；测试使用 Maven Central 的 `org.json:json:20240303`。资源与 Java 源码复制到 ASCII 临时目录构建，避免 Windows 中文路径问题。再次构建复用缓存和签名密钥。

签名密钥位于 `.build/signing/`，需要后续覆盖安装时保留，不包含在源码 ZIP 中。移除或重建密钥后无法覆盖安装旧签名版本，需先卸载旧包。构建脚本会先保留已有 APK 的 `.work` 副本。

源码也包含 Android Studio/Gradle 配置（AGP 8.6.1、建议 Gradle 8.7、JDK 17）；这一路径未执行验收。本次已实测的是上面的独立构建脚本，不依赖本机安装 Gradle。

## 手机验收清单

- 冷启动正常；粘贴给定链接并解析；网页需要验证时能由你完成。
- 正常视频可保存，相册能看到且音视频可播放；画面没有额外添加的抖音下载水印。
- 一条确实关闭作者下载按钮的视频：网页仍可播放时能否保存。
- 后台下载、取消、再次下载；取消后相册无残缺文件。
- 从抖音系统分享菜单发送链接到“拾影”；历史记录播放和分享。
- 如果失败，请回传手机 Android 版本、WebView 版本、分享链接和应用显示的完整错误。

## 实现参考

- [Android MediaStore 官方文档](https://developer.android.com/training/data-storage/shared/media)
- [yt-dlp 官方 Douyin 解析器与播放地址结构](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/tiktok.py)
- [Firecrawl 官方 Java 参数文档](https://docs.firecrawl.dev/agent-source-of-truth/java)
- [Firecrawl 官方 Scrape API](https://docs.firecrawl.dev/api-reference/endpoint/scrape)

项目不包含任何云技能、提示词、受管理技能缓存或其正文。
