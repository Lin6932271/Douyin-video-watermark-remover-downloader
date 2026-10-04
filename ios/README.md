# 拾影 iOS 1.0.0

**由 AI（OpenAI Codex）开发。** 这是原生 iOS 源码工程，不是已经完成签名和真机验证的 IPA。用户操作设备，开发端的验证范围在下文单独列出。

原生 SwiftUI + WKWebView + URLSession + PhotoKit 应用，适配 iPhone / iPad，最低 iOS 16.0。没有第三方 iOS SDK、广告、注册或订阅，也不依赖安卓设备。

## 交付与验证状态

- 提供完整 `Shiying.xcodeproj`、共享 Scheme、6 个 Swift 源文件、生产解析脚本、图标、隐私清单、构建脚本和测试。
- 安卓版本由用户反馈“测试可用”；本工程沿用其播放地址选择与网页辅助读取方式，但 iOS WebKit 与安卓 WebView 行为不保证完全一致。
- Windows 已运行生产 JavaScript 解析器与网页读取脚本回归测试，并检查 Xcode 工程引用、资源、plist、Scheme 和 Swift 语法树。
- **未执行**：Swift 类型检查、Xcode 编译、Apple 签名、iOS 模拟器 / 真机运行、相册保存真机测试、Firecrawl 真实云请求。没有 Mac、Apple 签名账号和云 API Key，当前交付不是可直接安装的 IPA。
- 既有真实样本 `https://v.douyin.com/HOpFK-XHaEg/` 的 API / 网页快照可被本版解析器读取。这是已有证据重放，不是当前 iPhone 端的网络验收。真实样本的 `allow_download` 为 true；作者关闭下载的情况使用合成回归数据验证。

## 在 Mac 上编译和安装

1. 安装包含 iOS SDK 的 Xcode（建议 Xcode 16 或更新版本），首次启动完成组件安装。所需 macOS 版本以 [Apple 的 Xcode 系统要求](https://developer.apple.com/xcode/system-requirements) 为准。
2. 将整个 `ios` 文件夹复制到 Mac，双击 `Shiying.xcodeproj`。
3. 选择 `Shiying` Target → Signing & Capabilities，开启 Automatically manage signing，选择自己的 Team。必要时将 Bundle Identifier 改成自己的唯一标识。
4. 连接 iPhone，在 Xcode 选择该设备并按 Run。按照设备提示信任开发签名、开启开发者模式。签名 / 授权有效期由你的 Apple 账号和安装方式决定。
5. 模拟器不需要设备签名，可先选择一个 iPhone Simulator 后按 Run。

终端构建（进入复制到 Mac 的 `ios` 目录后执行）：

```bash
# 未执行：无签名模拟器构建。
bash scripts/build-ios.sh simulator

# 未执行：使用你自己的 Team 签名并生成归档。
TEAM_ID=你的TeamID BUNDLE_ID=你的唯一BundleID bash scripts/build-ios.sh archive
```

输出分别为 `build/DerivedData/Build/Products/Debug-iphonesimulator/Shiying.app` 和 `build/Shiying.xcarchive`。归档通过 Xcode Organizer 按自己的账号权限导出 / 安装。脚本没有内置证书、描述文件或开发者账号。

## 手机操作

1. 在抖音复制视频分享链接；可以复制包含说明文字的整段内容。
2. 打开拾影，点击“粘贴” → “解析视频”。
3. 静态页面没有播放数据时自动打开“网页辅助解析”。等网页加载，出现验证时由你操作；必要时点播放，再点“读取播放地址”。只会读取原视频，不跳转到其他视频。
4. 点击“下载原播放视频”，下载时保持应用在前台。可取消下载；失败会尝试其他解析到的播放地址。
5. 下载完成后，在记录中选择“存入相册”。应用只申请相册添加权限，不读取已有照片。也可以“分享” → 保存到文件、AirDrop 或其他应用。
6. 记录中的“播放”读取本地文件；“删除”只删除拾影内的文件，不删除已存入相册的副本。

## 无水印与下载关闭的含义

- 使用公开页面的 `play_addr_h264` / 高码率 `play_addr`，不使用通常包含平台下载水印的 `download_addr`；软件本身不叠加任何水印。
- 不以 `video_control.allow_download` 作为读取公开播放地址的条件，因此作者关闭下载按钮但视频仍可公开播放时可以尝试下载。
- 这不是删除已烧进画面的文字 / Logo 的视频修复器，也不承诺所有抖音视频都存在无水印 MP4 地址。
- 不读取私密、删除的视频；不突破付费或不可播放内容。平台验证、地域 / 登录状态、接口变动和签名地址过期都可能导致失败。
- 当前版本仅保存完整 MP4，校验 HTTP 状态、媒体域名、`ftyp` 文件头和长度；HLS / 分片流不进入下载候选。
- 前台 URLSession 下载不承诺应用被系统挂起或强制退出后继续；失败后重新解析即可获取新地址。

## 可选 Firecrawl 云解析

主流程完全在手机上执行，不需要 API Key。只有主动点击“可选云解析”，输入自己的 Firecrawl API Key 并开始后，才通过 `POST /v2/scrape` 发送公开分享链接。Key 只保存在运行时内存；不将 WebKit 的 Cookie、相册或视频文件上传给云解析服务。

按 [Firecrawl REST 文档](https://docs.firecrawl.dev/agent-source-of-truth/curl) 请求 `rawHtml`，禁用缓存（`maxAge: 0`），再交给同一解析器。这个可选路径未进行真实 API 请求验证，不保证云渲染能读取所有抖音页面。

网页辅助解析使用系统 WKWebView 的默认数据存储，网页 Cookie 会保留在本机 WebKit 存储中；仅对实际媒体目标域名匹配的 Cookie 用于下载请求。没有 JavaScript 原生桥，也没有禁用 HTTPS 证书验证。可在 iOS 设置删除应用以清除应用本地数据。

## 回归测试与项目再生成

Node 18 或更新版本即可运行生产脚本测试，不需要 npm 安装：

```bash
node tests/parser.test.cjs
```

独立源码包包含 37 个自包含回归案例；在原工作区另有既有真实 API / 浏览器快照时自动增加 2 个重放案例。为避免打包临时签名地址或会话字段，这些原始网络证据不进入源码包。

可选静态验证（Python 3.10+，先安装语法解析依赖）：

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r scripts/requirements-static.txt
.venv/bin/python scripts/validate_project.py
```

静态验证只检查结构与语法，不会将通过结果冒充 Swift 编译成功。真正的编译验收命令是上述 `bash scripts/build-ios.sh simulator`。

`scripts/create_project.py` 可以从当前 Swift 文件再生成工程和代码绘制的图标。**会重置工程配置和签名设置**，正常使用已交付工程不需要执行；要调整生成规则，先备份工程再运行。

参考：[WKWebView](https://developer.apple.com/documentation/webkit/wkwebview)、[PhotoKit 添加权限](https://developer.apple.com/documentation/photos/phphotolibrary)、[URLSession 后台下载机制](https://developer.apple.com/documentation/foundation/downloading-files-in-the-background)。本版明确使用前台下载，不声明后台模式。
