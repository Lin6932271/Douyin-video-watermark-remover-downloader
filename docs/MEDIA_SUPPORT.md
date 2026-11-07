# 图片 / 动图 / 实况增强验收记录

## 目标与成功判据

在原仓库视频功能上增加按顺序的图集下载、动图原字节保存、实况图片及动态片段下载；不把多个 CDN 当多张图，不把配乐视频或推荐视频误当图集。交付 Android APK、修改源码和可复核记录。

## 根因与对应修改

1. `PageParser.walk` 原先只接受含顶层 `video` 的对象，随后只读 `play_addr`。现在优先读取 `images` / `image_post_info` / camelCase 图集字段，并逐张读取 `images[i].video` 的播放地址。
2. `LinkTools.isMediaUrl` 原先没有 `douyinpic.com` 等图片 CDN；现在扩展受限域名集合，仍按主机边界、HTTPS 和端口验证。
3. 原 `DownloadService` 只校验 `ftyp` 且全部写入 Video MediaStore。现在文件头识别 JPEG/PNG/GIF/WebP/HEIC/AVIF/MP4/MOV，匹配真实 MIME/扩展名并分流 Images/Video。
4. 实际 Android 移动 UA 将用户短链跳至 `/share/slides/7000000000000000001/`，原 ID 正则不支持 slides。首次 Java 在线探针因此没拿到 ID；已扩展路径并新增回归，再运行实际下载成功。
5. 图片与动态轨共享原图序号；动态源失效不悄悄改存静态封面。每个素材只从自身候选地址尝试，部分失败/取消保留成功文件。
6. 多文件任务 JSON 写入应用私有任务文件，再交给服务读取，避免把大图集地址放入受大小限制的 Binder Intent。
7. 网页优先读取结构化状态、页面自身详情响应和受限 React props；图集页面不使用 performance 列表中的任意视频冒充图集。没有原生 JavaScript bridge。

## 真实样本

用户短链与作品 ID 不公开（样本保存在本地 evidence/，不入库）。
当前接口返回 2 张图片，二者都有 `live_photo_type=1`、`clip_type=5` 和 `video.play_addr_h264`。
应用生产 Java 解析器、HTTP 客户端、CDN 校验及文件头识别组件共同完成桌面实际下载：

| 文件 | MIME | 字节数 | FFmpeg 完整解码退出码 |
| --- | --- | ---: | ---: |
| 001_image.webp | image/webp | 99560 | 0 |
| 001_live_video.mp4 | video/mp4 | 234687 | 0 |
| 002_image.webp | image/webp | 120238 | 0 |
| 002_live_video.mp4 | video/mp4 | 329579 | 0 |

4 个文件均与服务器 Content-Length 相同。原始临时签名地址/会话数据不放进源码 ZIP。

## 已执行

- `python scripts/test.py`：原视频 20 个断言、新媒体 63 个断言通过（包含真实响应的 2 个断言）。
- `node ios/tests/parser.test.cjs`：57 个测试通过，其中包含用户真实响应的两组实况断言。
- `python ios/scripts/validate_project.py`：37 个工程对象、6 个 Swift 文件语法、4 个资源、plist/Scheme 通过。第一次运行缺少 openstep_parser；使用项目独立 QA venv 安装依赖后解决。
- `build.ps1`：Android Java 编译、D8、资源打包、v3 签名、zipalign、ZIP 校验通过。
- 新旧 APK 签名证书 SHA-256 一致：`bc3118111d94c8b4ddc2b16ba356f041d2d825c8583eff2a5ea17a320e9a9dab`。
- FFmpeg 完整解码真实样本的全部 4 个媒体文件，全部退出码 0。
- `git diff --check`：无空白错误。

## 没有执行 / 用户测试项

用户明确选择只收 APK、自行测试；没有安装 APK、覆盖应用、读取/操作手机相册或执行手机 UI 测试。

- 在手机安装后解析上述样本，应显示图片 2、实况片段 2，共 4 个文件。
- 选择全部保存，应在相册得到 2 张图和 2 段 MP4；也可只选其中一个素材。
- 验证 GIF/动态 WebP 的播放、后台通知、取消及部分失败行为。桌面解析/文件检查不替代这些运行测试。
- 实况下载为同编号图片 + MP4，不是手机厂商实况容器，不保证相册长按播放。
- iOS 没有执行 Swift 类型检查、Xcode 编译、签名或设备运行；仅更新源码并做静态/JS 测试。
- Firecrawl 真实服务未测试。

## 执行账本

- 已读取技能入口：`C:/Users/aojiao/.codex/skills/l-reverse/SKILL.md`，云入口读取成功；目录中没有与本次普通源码功能开发直接匹配的原子安全模块，未读取额外模块，不声称加载了模块正文。技能内容不写入项目或交付。
- 原始仓库：`D:/csy/测试/douyin-media-original-20261023`，保持克隆状态；所有修改在 `D:/csy/测试/douyin-media.work`。
- 基线提交：`92318e760b19a3718645ef405cf7b4c888ed14b3`。
- 成功命令与报告：Java `scripts/test.py` → `evidence/java-tests.txt`；JS 测试 → `evidence/ios-parser-tests.txt`；GalleryProbe → `evidence/user-gallery/report.json`；FFmpeg → `evidence/user-gallery/decode-report.json`；Android 构建 → `dist/build-report.json`；iOS 静态检查 → `evidence/ios-static.json`。
- 签名密钥复用已存在本地签名目录，不复制到源码或交付目录。工具缓存、原始网络材料、技能目录均不在源码包中。
- 未向 GitHub 推送修改，也没有新建 Release。
