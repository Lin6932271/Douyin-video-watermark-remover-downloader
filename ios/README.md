# 拾影 iOS 1.2.0 源码

原生 SwiftUI + WKWebView + URLSession + PhotoKit，iOS 16+。支持视频、图片图集、GIF/WebP 等动图原文件、实况图片及 MP4 动态片段。实况不会合成为原生 Live Photo。

## 验证边界

本次执行了生产 JavaScript 解析器测试，以及 Xcode 工程引用、资源、plist、Scheme、6 个 Swift 文件的语法树检查。**未执行 Swift 类型检查、Xcode 编译、Apple 签名、模拟器/真机、Photos 相册交互。没有可安装的 IPA。**

用户样本的桌面下载证据来自共享逻辑的 Android Java 组件，不代表已在 iPhone 下载成功。动图原格式若不能写入系统 Photos，可保留本地并分享到文件。

## 在 Mac 上构建

1. 打开 `Shiying.xcodeproj`；选择 `Shiying` Target，在 Signing & Capabilities 设置自己的 Team 和唯一 Bundle Identifier。
2. 选择模拟器或自己的 iPhone 后 Run。
3. 也可在本目录运行以下命令（开发端未执行）：

```bash
bash scripts/build-ios.sh simulator
TEAM_ID=你的TeamID BUNDLE_ID=你的唯一BundleID bash scripts/build-ios.sh archive
```

下载需保持前台。解析完成后点击“下载全部素材”，在下载记录中查看、存入相册或分享。文件名中的序号对应原图集顺序，`image` 和 `live_video` 对应同一张实况的两个资源。

## 独立测试

```bash
node tests/parser.test.cjs
python3 -m venv .venv
.venv/bin/python -m pip install -r scripts/requirements-static.txt
.venv/bin/python scripts/validate_project.py
```

根目录的合成图集夹具与此处 `tests/media-cases.json` 保持一致；不打包原始网络会话或临时签名地址。重新生成工程的 `scripts/create_project.py` 会重置工程配置，修改签名配置后不要无故重跑。

可选 Firecrawl 只在主动提供自己的 Key 后发送分享链接，不上传网页 Cookie。该云路径没有真实请求验收。应用不关闭 TLS 验证、无原生 JavaScript 桥。
