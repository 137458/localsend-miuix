# 更新日志 (CHANGELOG)

所有对 LocalSend Miuix 项目的重要更新都将记录在此文件中。格式遵循 Keep a Changelog 规范与语义化版本规则。

---

## [未发布]

### 新增特性
- 完整复刻 LocalSend v2.2 局域网传输协议与网络引擎（UDP 多播广播发现、局域网子网并发扫描、自签名 TLS/HTTPS 互通与 Ktor 异步 HTTP 服务端与客户端）。
- 全面采用 Miuix 0.9.4-rc01 组件库构建纯正 Xiaomi HyperOS 视觉规范界面。
- 引入 iOS / HyperOS 液态玻璃（Liquid Glass）悬浮导航栏：集成 GPU 实时 RuntimeShader 折射透镜、色散色差、内阴影及物理阻尼滑动动画。
- 深度结合 HorizontalPager 实现三页横向手势平滑滑屏与底栏指示器流畅联动。
- 实现发送、接收、设置三大核心功能板块及完整交互。
- 全面基于 WindowDialog、WindowBottomSheet 与 WindowDropdownPreference 实现独立窗口级弹窗体系。

### 缺陷修复
- 100% 对齐 LocalSend 官方 v2.2 传输规范，增加自签名证书信任管理器（SslHelper），解决与官方客户端 HTTPS 握手与连接失败问题。
- 引入 Android WifiManager.MulticastLock 多播锁与定向子网广播，解决局域网设备发现与多播接收受阻问题。
- 优化网络接口 IP 获取算法，优先适配 WLAN/Wi-Fi 局域网真实地址。
- 全面迁移所有弹窗为 WindowDialog 与 WindowBottomSheet，彻底解决弹窗在 Backdrop 图层转换下的闪退崩溃问题。
- 紧凑化重构悬浮底栏尺寸（精简至 210~240dp 紧凑胶囊），优化内部图标与文字排版比例。
- 消除子页面顶层多余的 PaddingValues 累加，使 TopAppBar 页面标题位置自然贴合格局。
- 优化文件与媒体选择器 ActivityResult 契约及安全 URI 访问容错机制。
