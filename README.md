# LocalSend Miuix

LocalSend Miuix 是基于 Xiaomi HyperOS 设计风格与 Miuix KMP 现代化组件库完整复刻重构的跨平台局域网文件传输应用。

本项目完全兼容官方 LocalSend Protocol v2 规范，不仅能与局域网内的官方 LocalSend 客户端（Windows、macOS、iOS、Android、Linux）无缝双向发现与文件传输，还提供了纯正的 HyperOS 视觉语言体验与悬浮底栏（FloatingNavigationBar）导航设计。

---

## 核心特性

- **纯正 HyperOS 视觉规范**：基于 Miuix 0.9.4-rc01 组件库深度构建，全链路采用 Miuix 推荐的最佳实践与组件（MiuixTheme、Scaffold、FloatingNavigationBar、Card、SmallTitle、*Preference、OverlayDialog、OverlayBottomSheet、LinearProgressIndicator 等）。
- **悬浮底栏主导航**：采用 HyperOS 标志性的 `FloatingNavigationBar` 作为应用底栏入口，支持 Badge 动态徽标指示未处理的传入传输请求。
- **全协议兼容**：
  - **UDP 多播与广播发现**：监听与宣告多播地址 `224.0.0.167:53317` 及广播地址 `255.255.255.255:53317`，秒级识别局域网活跃设备。
  - **子网异步并发扫描**：支持对 `192.168.x.1..254` 网段进行并发探测，解决路由器 AP 隔离导致的多播阻塞问题。
  - **HTTP REST 与流式传输**：基于 Ktor 异步引擎实现 `/api/localsend/v2/info`、`/api/localsend/v2/register`、`/api/localsend/v2/prepare-upload`、`/api/localsend/v2/upload` 与 `/api/localsend/v2/cancel` 完整生命周期。
  - **自签名 TLS/HTTPS**：HTTPS 模式下本机指纹对齐协议 §2（fingerprint=证书 SHA-256），启动时生成并持久化自签名证书，并通过指纹加固 TrustManager 校验对端证书，双向防中间人。
  - **Web Share（双向网页快传）**：把选中文件共享为局域网链接，对方浏览器打开即可预览并逐个下载；同时支持对方直接在浏览器中拖拽（Drag & Drop）或选择文件高速回传至手机端，无需安装 LocalSend。
- **丰富的内容发送与系统级分享支持**：支持系统相册、文件管理与第三方应用通过 Android 系统分享菜单（ACTION_SEND / ACTION_SEND_MULTIPLE）直接分享文件/文本至应用，支持从文件管理器“打开方式”（ACTION_VIEW）唤起；同时支持在应用内选择任意文件、多媒体图片/视频、应用 APK 提取、纯文本录入及一键剪贴板文本提取。
- **完善的交互与控制**：
  - 快速保存模式（无需确认自动接收信任传输）。
  - 实时传输进度、传输速度（MB/s）与剩余时间监控。
  - 传输历史记录安全本地 JSON 持久化（重启/更新后永不丢失）与一键清理。
  - 软件在线检测更新系统：支持 GitHub Releases 检查、更新日志展示与 APK 应用内流式下载安装。
  - 沉浸式色彩主题模式切换（跟随系统、浅色、深色、莫奈跟随系统、莫奈浅色、莫奈深色）。

---

## 页面与组件结构

| 页面 / 模块 | 核心 Miuix 组件 | 功能描述 |
|---|---|---|
| 主架构 Shell | `MiuixTheme`, `ThemeController`, `NavDisplay`, `Scaffold`, `LiquidGlassBottomBar`, `Badge` | 动态主题控制、miuix-nav 官方路由调度、全局独立窗口级弹窗宿主、悬浮底栏切换 |
| 接收 (Receive) | `TopAppBar`, `Card`, `SmallTitle`, `LinearProgressIndicator`, `Button` | 本机名称/IP/端口展示、实时传输进度、Web Share 分享链接，点击右上角图标进入传输历史路由 |
| 发送 (Send) | `TopAppBar`, `MiuixScrollBehavior`, `PullToRefresh`, `Card`, `SmallTitle`, `Button`, `IconButton` | 快速选择入口、下拉刷新局域网设备、附近设备列表、待发送内容清单（选择内容后显示） |
| 设置 (Settings) | `TopAppBar`, `Card`, `SmallTitle`, `ArrowPreference`, `SwitchPreference`, `WindowDropdownPreference` | 设备别名、快速保存、服务端口与多播地址、主题色彩模式切换、检查更新入口 |
| 传输历史 (History) | `TopAppBar`, `Card`, `SmallTitle`, `Icon`, `IconButton` | 独立路由页面，支持原生连续深度进退转场与边缘左滑返回手势 |
| 软件更新 (Update) | `SmallTopAppBar`, `BgEffectBackground`, `MarkdownText`, `UpdateDialog`, `Card`, `SmallTitle`, `BasicComponent`, `Switch` | 完整对齐 pixez-flutter-MIUIX 规范，集成 HyperOS 2/3 原生 GPU RuntimeShader 动态流光背景、Markdown 富文本更新日志、官方 UpdateDialog 弹窗与设置通道 |
| 弹窗交互 (Overlays) | `WindowDialog`, `WindowBottomSheet`, `TextField`, `ButtonDefaults` | 设备名称修改、手动 IP 发送输入、文本消息发送、传输请求接收/拒绝、发送内容选择菜单 |

---

## 系统要求与编译构建

### 系统要求
- JDK 17 或 JDK 21 (推荐 Android Studio 自带 JBR 21)
- Android SDK Platform 35 / 37 (Build Tools 35.0.0+)
- Gradle 9.6.1 + Android Gradle Plugin 9.2.1

### 编译与打包
```bash
# 编译并生成 Debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest
```

编译产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

---

## 开源协议
本项目基于 Apache 2.0 许可证开源。
