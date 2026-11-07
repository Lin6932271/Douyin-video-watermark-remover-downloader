# 拾影 · 抖音媒体保存器 1.2.0

基于 [Lin6932271/Douyin-video-watermark-remover-downloader](https://github.com/Lin6932271/Douyin-video-watermark-remover-downloader) 的本地增强版。基线提交：`92318e760b19a3718645ef405cf7b4c888ed14b3`。本次修改未推送到 GitHub 或发布到 Releases。

**由 OpenAI Codex 开发。** 支持公开作品的视频、图片、动图和实况素材，不添加水印、不重编码，也不是擦除画面内水印的算法。

## 交付

- Android 10+：`dist/shiying-1.2.0.apk`。包名 `com.aojiao.shiying`，versionCode 120。本次 APK 与仓库原版 APK 的签名证书一致。
- Android + iOS 完整源码：`dist/shiying-1.2.0-source.zip`。
- iOS 16+：提供更新后的 Xcode/SwiftUI 源码，**没有 IPA；未做 Swift 类型检查、Xcode 编译、签名或真机测试**。
- 原始 1.0.0 APK 保留不覆盖；`SHA256SUMS.txt` 给出本次产物的校验值。

## 新增能力

- 图片图集：按原顺序解析，每张图片的多个 CDN 地址视为备用地址，不重复下载。
- GIF、WebP、PNG/APNG 等图片：按真实文件头决定扩展名和 MIME，原始字节保存，不把动图转成静态 JPG。
- 实况：保存**同编号的图片和 MP4 动态片段**；不再只抓静态封面或顶层图集配乐视频。
- Android 支持“全部保存”和“选择素材”；图片写入 `Pictures/拾影/`，视频和实况片段写入 `Movies/拾影/`。
- 支持 `/video/`、`/note/`、`/share/slides/` 和查询参数形式的作品 ID。
- 每个文件独立尝试备用地址；部分失败会报告数量和原因；取消时保留已成功的文件，清理当前未完成项。
- 历史记录按真实 MIME 查看/分享，兼容旧视频记录。

**实况的兼容边界：**图片 + MP4 是完整的两类下载素材，不等于合成为 iPhone 原生 Live Photo 或各 Android 品牌的实况照片。相册能否长按播放取决于相册对该格式的支持，本版不承诺长按播放。平台没下发动态轨时会明确提示，而不是把静态图声称为完整实况。

## 使用

1. 将 APK 传到手机安装。本次不自动安装或操作手机。签名不兼容时不要先卸载旧版，先核对包名与签名。
2. 复制完整抖音分享文字，粘贴后点“解析作品”。
3. 查看图片/实况数量，点“保存全部素材到相册”或“选择素材保存…”。
4. 若需验证/登录，在内置网页自行完成，再点“读取当前页面”。动态图可先在网页播放一下。
5. 需要新地址时重新解析；不应复用过期的签名 CDN 地址。

详见 [使用说明](docs/USAGE.md)、[变更与验收记录](docs/MEDIA_SUPPORT.md)、[iOS 说明](ios/README.md)。

## 构建与测试

Windows、Python 3.9+、JDK 17：

```powershell
$env:JAVA_HOME_17 = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
.\build.ps1 -Setup
python scripts/test.py
node ios/tests/parser.test.cjs
python scripts/package_source.py
```

构建使用 Google 官方 Android API 35 / Build Tools 35.0.0，`setup_tools.py` 核对官方归档 SHA-1；解析测试依赖 Maven Central 的 `org.json:json:20240303`。首次安装工具需联网。可选环境变量：

- `ANDROID_SDK_ROOT`：已有 Android SDK。
- `SHIYING_JSON_JAR`：已有测试用 JSON JAR。
- `SHIYING_SIGNING_DIR`：已有签名目录；未设置时使用本项目 `.build/signing/`。不要分享该目录。

源码不包含签名密钥；用新的密钥重建不保证能覆盖旧版。源码 ZIP 不包含工具缓存、网络会话、Cookie、签名 URL 或受管理技能内容。

## 验证状态

- Android APK：编译、签名、zipalign、ZIP 完整性检查。
- Java 和 iOS 生产 JS 解析器：普通视频、图集、实况、动图、错误作品隔离和文件格式回归。
- 用户提供的 分享短链（不公开）：当前桌面网络实测解析出 2 张图片 + 2 段实况 MP4；通过应用同一 Java HTTP/格式识别组件下载 4 个文件，长度一致且 FFmpeg 完整解码退出码为 0。
- **未执行** Android 手机运行和 MediaStore 相册交互验收，由用户自行测试；桌面下载不能证明手机网页或相册行为。
- iOS：生产 JS 回归、6 个 Swift 文件语法树、工程资源/引用/plist 检查；不等于 iOS 编译成功。
- Firecrawl 保留原先的可选设计，未进行真实云请求；没有 Key 不影响本机解析。

## 不包含的功能

私密/删除/付费/加密内容不做突破；不处理直播、HLS 分段合并、主页爬取或自动登录。仅保存平台实际返回且可访问的资源，不保证所有作品始终可下载。作者已烧入画面的水印仍保留。

实现参考：[Android MediaStore](https://developer.android.com/training/data-storage/shared/media)。
