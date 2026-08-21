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
- **丰富的内容发送支持**：支持选择任意文件、多媒体图片/视频、纯文本消息录入及一键剪贴板文本提取。
- **完善的交互与控制**：
  - 快速保存模式（无需确认自动接收信任传输）。
  - 实时传输进度、传输速度（MB/s）与剩余时间监控。
  - 传输历史记录归档与一键清理。
  - 沉浸式色彩主题模式切换（跟随系统、浅色、深色、莫奈跟随系统、莫奈浅色、莫奈深色）。

---

## 页面与组件结构

| 页面 / 模块 | 核心 Miuix 组件 | 功能描述 |
|---|---|---|
| 主架构 Shell | `MiuixTheme`, `ThemeController`, `Scaffold`, `FloatingNavigationBar`, `FloatingNavigationBarItem`, `Badge` | 动态主题控制、全局弹窗宿主、悬浮底栏切换 |
| 发送 (Send) | `TopAppBar`, `MiuixScrollBehavior`, `PullToRefresh`, `Card`, `SmallTitle`, `Button`, `IconButton` | 待发送内容清单、下拉刷新局域网设备、附近设备列表、手动 IP 发送 |
| 接收 (Receive) | `TopAppBar`, `Card`, `SmallTitle`, `LinearProgressIndicator`, `SwitchPreference`, `ArrowPreference` | 本机网络状态展示、实时传输进度与速度、传输历史管理 |
| 设置 (Settings) | `TopAppBar`, `Card`, `SmallTitle`, `ArrowPreference`, `SwitchPreference`, `OverlayDropdownPreference` | 设备别名、快速保存、服务端口与多播地址、主题色彩模式切换 |
| 弹窗交互 (Overlays) | `OverlayDialog`, `OverlayBottomSheet`, `TextField`, `ButtonDefaults` | 设备名称修改、手动 IP 发送输入、文本消息发送、传输请求接收/拒绝、发送内容选择菜单 |

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
