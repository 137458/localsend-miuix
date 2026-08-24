# 更新日志 (CHANGELOG)

所有对 LocalSend Miuix 项目的重要更新都将记录在此文件中。格式遵循 Keep a Changelog 规范与语义化版本规则。

---

## [未发布]

### 性能优化
- 优化 Web Share 浏览器下载传输：彻底消除全量内存载入缺陷，升级为 128KB 分块流式管道响应，杜绝大文件下载 OOM 风险。
- 优化文件读写与网络传输 I/O：引入双端 128KB 缓冲流，大幅降低存储系统调用与上下文切换开销。
- 优化局域网全网段探测调度：引入 32 限制并行度与无锁原子计数，避免线程饥饿与网络并发拥塞。
- 优化 Compose 渲染重组性能：为会话列表与文件明细增加派生状态缓存与 Key 绑定，消除高频传输进度刷新时的无效全量重组。

### 新增特性与交互优化
- 接入官方 `miuix-nav` 导航架构：定义可序列化路由体系 `AppRoute`，使用 `NavDisplay` 与 `rememberNavBackStack` 管理全局页面路由与生命周期；传输历史页全面支持 HyperOS 原生连续深度转场动画（Continuous-Depth Transitions）与边缘左滑返回手势（Swipe Dismiss）。
- 升级 Miuix 组件库依赖至 `0.9.4-rc01`，工程统一适配 JVM 21 编译构建规范。
- 重构传输进度交互体验：全面优化 `TransferSessionCard`，新增当前正在传输文件的名称与序号指示、明确的传输百分比、动态预估剩余时间 (ETA) 及可折叠展开的文件明细清单（支持单个文件的独立状态、大小与进度展示）；接收页与发送页统一复用高质感传输进度卡片。
- 完整复刻 LocalSend v2.2 局域网传输协议与网络引擎（UDP 多播广播发现、局域网子网并发扫描、自签名 TLS/HTTPS 互通与 Ktor 异步 HTTP 服务端与客户端）。
- 实现 Web Share（Download API，协议 §5）：可将选中文件共享为局域网链接，接收方浏览器打开即可预览并逐个下载，无需安装应用；链接协议自动适配当前服务端配置（HTTP / HTTPS）。
- HTTPS 模式下本机指纹对齐协议 §2（fingerprint=自签名证书 SHA-256），并通过指纹加固 TrustManager 校验对端证书，杜绝中间人攻击。
- 全面采用 Miuix 0.9.4 组件库构建纯正 Xiaomi HyperOS 视觉规范界面。
- 引入 iOS / HyperOS 液态玻璃（Liquid Glass）悬浮导航栏：集成 GPU 实时 RuntimeShader 折射透镜、色散色差、内阴影及物理阻尼滑动动画。
- 深度结合 HorizontalPager 实现三页横向手势平滑滑屏与底栏指示器流畅联动。
- 实现发送、接收、设置三大核心功能板块及完整交互。
- 全面基于 WindowDialog、WindowBottomSheet 与 WindowDropdownPreference 实现独立窗口级弹窗体系。

### 缺陷修复
- 修复悬浮底栏跨页切换被拦截问题：消除底栏内部状态与 Pager 滚动过程中的双向监听冲突，支持任意跨页（如 0 到 2）平滑直达。
- 修复并加固 HTTPS 协议支持：引入指纹归一化与引用计数管理机制，彻底解决并发与多文件请求下指纹过早解除信任导致的 TLS 握手失败问题；将已知已发现 HTTPS 设备自动加入信任池；自签名证书有效期延长至 10 年并规范添加 KeyUsage 扩展。
- 修复 Web Share 链接协议硬编码问题：由固定 `http://` 改为根据服务端当前协议动态生成。
- 完善传输过程中的单文件进度实时同步：在客户端与服务端流式读写过程中同步计算单文件进度，解决多文件传输时单个文件无进度反馈的问题。
- 优化通知栏传输进度通知：在进度通知中附带当前传输文件名、实时速率与百分比。
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
