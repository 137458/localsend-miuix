# 更新日志 (CHANGELOG)

所有对 LocalSend Miuix 项目的重要更新都将记录在此文件中。格式遵循 Keep a Changelog 规范与语义化版本规则。

---

## [未发布]

### 新增特性
- 完整复刻 LocalSend v2 局域网传输协议与网络引擎（UDP 多播广播发现、局域网子网并发扫描、Ktor 异步 HTTP 服务端与客户端）。
- 全面采用 Miuix 0.9.4-rc01 组件库构建纯正 Xiaomi HyperOS 视觉规范界面。
- 深度参考 Miuix 官方示例重构界面层，引入 HorizontalPager 与 FloatingNavigationBar 联动平滑滑动切换，优化快速选择与全网段探测视觉反馈。
- 实现发送、接收、设置三大核心功能板块及完整交互。
- 提供基于 OverlayDialog 与 OverlayBottomSheet 的设备重命名、手动 IP 发送、纯文本发送与传输确认等弹窗交互。
