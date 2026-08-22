# 更新日志 (CHANGELOG)

所有对 LocalSend Miuix 项目的重要更新都将记录在此文件中。格式遵循 Keep a Changelog 规范与语义化版本规则。

---

## [未发布]

### 新增特性
- 完整复刻 LocalSend v2.2 局域网传输协议与网络引擎（UDP 多播广播发现、局域网子网并发扫描、自签名 TLS/HTTPS 互通与 Ktor 异步 HTTP 服务端与客户端）。
- 实现 Web Share（Download API，协议 §5）：可将选中文件共享为局域网链接，接收方浏览器打开 `http://设备IP:端口` 即可预览并逐个下载，无需安装应用；发送页新增"通过链接分享"卡片（复制链接 / 结束共享）。
- HTTPS 模式下本机指纹对齐协议 §2（fingerprint=自签名证书 SHA-256），并通过指纹加固 TrustManager 校验对端证书，杜绝中间人攻击。
- 全面采用 Miuix 0.9.4-rc01 组件库构建纯正 Xiaomi HyperOS 视觉规范界面。
- 引入 iOS / HyperOS 液态玻璃（Liquid Glass）悬浮导航栏：集成 GPU 实时 RuntimeShader 折射透镜、色散色差、内阴影及物理阻尼滑动动画。
- 深度结合 HorizontalPager 实现三页横向手势平滑滑屏与底栏指示器流畅联动。
- 实现发送、接收、设置三大核心功能板块及完整交互。
- 全面基于 WindowDialog、WindowBottomSheet 与 WindowDropdownPreference 实现独立窗口级弹窗体系。

### 缺陷修复
- 修复发送到原版 LocalSend 失败：updateFile 上传处 fingerprint 的 pin 过早解除（unpin 发生在真正 TLS 握手之前），导致 HTTPS 目标握手必然失败；现改为整个上传结束（finally）才 unpin。
- 快速保存（默认接收）路径补充系统通知：收到文件时弹"收到文件"通知，接收过程中实时更新进度条，完成/取消/失败后弹最终结果，即使应用退到后台也有反馈。
- 修复 HTTPS/端口开关切换时界面卡顿：服务引擎（Netty+证书解析）初始化较重，原先在主线程执行导致冻结；现切换到 IO 线程执行。
- 100% 对齐 LocalSend 官方 v2.2 传输规范，增加自签名证书信任管理器（SslHelper），解决与官方客户端 HTTPS 握手与连接失败问题。
- 引入 Android WifiManager.MulticastLock 多播锁与定向子网广播，解决局域网设备发现与多播接收受阻问题。
- 优化网络接口 IP 获取算法，优先适配 WLAN/Wi-Fi 局域网真实地址。
- 全面迁移所有弹窗为 WindowDialog 与 WindowBottomSheet，彻底解决弹窗在 Backdrop 图层转换下的闪退崩溃问题。
- 紧凑化重构悬浮底栏尺寸（精简至 210~240dp 紧凑胶囊），优化内部图标与文字排版比例。
- 消除子页面顶层多余的 PaddingValues 累加，使 TopAppBar 页面标题位置自然贴合格局。
- 优化文件与媒体选择器 ActivityResult 契约及安全 URI 访问容错机制。
- 在 MainActivity 提供 NavigationEventDispatcherOwner，修复 ManualIpDialog / 设备别名 / 发送文本等 WindowDialog 弹窗打开即闪退的问题。
- 对齐官方将默认接收保存路径改为公共 Download/LocalSend 目录：改用 MediaStore（`IS_PENDING` + `RELATIVE_PATH`）写入，Android 10+ 无需存储权限，文件直接落盘到系统下载目录，彻底告别安卓 data 应用私有目录。
- 发送页新增"正在传输"区域，与接收页共用 TransferSessionCard 组件实时展示发送进度条、已传/总量与速率。
- 已结束（完成/失败/已取消）的传输会话自动从"正在传输"区移除并移入传输历史，不再残留。
- 接收方拒绝传输后，发送端通过 Toast 明确提示"对方拒绝接收"，避免发送端无任何反馈。
- 调整主导航页序：接收页置于第 0 页、发送页置于第 1 页；接收页精简为本机名称/IP/端口展示 + 实时接收进度 + Web Share 分享链接，右上角新增图标进入独立传输历史页（支持一键清空）。
- 发送页精简：待发送内容清单默认隐藏，仅在选择内容后显示；移除发送页中的"通过链接分享"与"手动 IP"入口，快速选择保留全部支持类型。
- 修复原版 LocalSend 会搜出两个一模一样名称与内容设备的问题：集中设备发现 upsert 逻辑，以 fingerprint + ip:port 作为去重键，并按 TTL 清理过期设备。
- 优化页面切换卡顿：设备内容未变化时避免无意义的状态更新与列表重组。
