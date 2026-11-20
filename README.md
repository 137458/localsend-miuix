# LocalSend Miuix

LocalSend 协议的 Android 重写客户端：与官方及第三方 LocalSend 客户端互通，界面提供 HyperOS（Miuix）与 ColorOS（coUI-Kit）两套皮肤，共用同一份业务状态、路由与设置持久化。

## 特性

- 设备发现：多播 `224.0.0.167:53317` 加按白/黑名单过滤网卡的定向广播双向宣告；路由器屏蔽组播时用 `/24` 子网扫描兜底（32 并发、单探针 3 s）。
- 传输服务：Ktor 嵌入式服务端，纯 HTTP 走 CIO，HTTPS 切换到 Netty 并挂载自签名证书；本机指纹即证书 SHA-256，出站按对端指纹固定校验。
- 内容来源：系统分享（`ACTION_SEND` / `ACTION_SEND_MULTIPLE`）与「打开方式」（`ACTION_VIEW`）、任意文件、媒体、已安装应用 APK、纯文本、剪贴板；待发送列表带图片、视频首帧与应用图标缩略图。
- 网页快传：待发内容挂到 `http://<本机 IP>:<端口>/`，对端浏览器可预览（图片、视频、音频、文本、JSON/XML）、单个或 zip 下载，并可直接拖拽回传；入口附二维码。
- 通知：5 个渠道（实时进度、接收、接收请求、发送、后台服务）；接收请求通知为高优先级并带「接收 / 拒绝」动作；Android 16 及以上走原生 `Notification.ProgressStyle` 提升型常驻通知（焦点胶囊与分段进度），低版本回退兼容实现；`POST_NOTIFICATIONS` 仅在首次启动询问一次。
- 传输完整性：可选端到端 SHA-256 校验，只加自定义请求头，不改标准字段；摘要不一致即删除临时文件并把该文件判为失败，对端不支持时降级为「未校验」；确认应答丢失时先查 `checksum-status` 自愈，不误判失败。
- 吞吐与读数：接收端读写分离（`512 KiB` 分块、4 块在飞、`512 KiB` 写缓冲），发送端 2 路并发上传；会话速率取全程累计平均（`200 ms` 节流快照），单文件行保留瞬时速率。
- 记录与更新：传输历史本地 JSON 持久化，支持分类筛选、详情、重发与清空；更新走 GitHub Releases，进程级下载四态，按摘要与字节校验安装包，失败按网络、校验、写盘分类。
- 外观：6 种主题（跟随系统、浅色、深色、莫奈跟随系统、莫奈浅色、莫奈深色），HyperOS 皮肤使用液态玻璃悬浮底栏（SDF 透镜折射、色散、阻尼滑块），中英双语。

## 下载与安装

- 安装包见 [GitHub Releases](https://github.com/137458/localsend-miuix/releases)。
- 系统要求：Android 12（API 31）及以上。
- 自 `v1.24.0` 起沿用同一签名（证书 SHA-256 `48fba404bc562e7287a90a2f932cbdb0a54141b8e642ee5489f4f681d3434909`），该版本及以后可覆盖升级；从 `v1.23.0` 及更早版本升级需先卸载旧版，卸载会清除应用本地数据。

## 页面结构

| 页面 | 关键实现 | 职责 |
|---|---|---|
| 主架构 | `MiuixTheme` / `CouiSkinTheme`、`NavDisplay`、`LiquidGlassBottomBar` / `CouiSkinNavigationBar` | 按 `UiSkin` 在 `ui/App.kt` 根部切换皮肤；底栏三页（首页、传输历史、设置）加全局弹窗宿主 |
| 首页 | `TopAppBar`、`SectionCard`、`PullToRefresh`、`TransferSessionCard` | 本机名称与 IP、正在接收的会话与实时进度、待发送队列、快速选择、收藏设备、附近设备；下拉刷新与子网扫描 |
| 传输历史 | `SegmentedControl`（低版本平替）、`HistoryStore` | 全部 / 接收 / 发送筛选，卡片增删动效，详情含文本内容与文件列表，重新发送，清空二次确认 |
| 设置 | `ArrowPreference`、`SwitchPreference`、`WindowDropdownPreference` | 通用（别名、语言、设备类型、主题、皮肤、通知入口）、接收、网络与安全（端口、HTTPS、PIN、证书指纹）、高级网络（白/黑名单、组播地址、发现超时）、关于 |
| 软件更新 | `BgEffectBackground`、`MarkdownText`、`UpdateDialog` | 检查 Releases、Markdown 更新日志、进程级下载与安装、忽略此版本、HyperOS 3 流光开关 |
| 弹窗体系 | `WindowDialog` / `WindowBottomSheet`、`Coui*` 弹窗 | 改名、端口、手动 IP、纯文本发送、PIN、证书指纹、接收请求、添加内容、APK 选择器、历史详情、网页快传 |

ColorOS 皮肤位于 `app/src/main/java/org/localsend/miuix/ui/coui/`，覆盖首页、传输历史、设置、软件更新四页与全套弹窗；其余组件与 HyperOS 皮肤共享。

## 协议

| 能力 | 实现 | 备注 |
|---|---|---|
| 发现 | 多播与广播宣告、`/api/localsend/v2/register` 回执、子网扫描 | 忽略本机自发宣告；收到对方的 announce 直接登记 |
| 元信息 | `/api/localsend/v2/info`，兼容 `/api/localsend/v1/info` | 协议版本 `2.2` |
| 发送 | `prepare-upload`、`upload`、`cancel` | 支持 PIN 校验、限流与逐项选择接收 |
| 接收 | `prepare-download`、`download`、`download-zip` | 逐文件下载与整包 zip 下载 |
| 网页快传 | `GET /` 返回本地渲染的 HTML | 预览、下载、拖拽上传，不需要对端安装 LocalSend |
| 校验扩展 | `X-Localsend-Miuix-Checksum`（`1` 仅回报、`2` 回报并暂存待确认）、`X-Localsend-Miuix-Sha256`、`/api/localsend/v2/checksum-commit`、`checksum-reject`、`checksum-status` | 私有扩展不修改标准字段；待确认暂存 60 s，结果保留 5 min |

## 已知限制

- 入站不启用双向 TLS：HTTPS 监听端不要求客户端证书，`/register` 仅信任请求体声明的指纹。原因是 Ktor Netty 的 `sslConnector` 没有读取客户端证书的入口，且强制客户端证书会让未携带证书的旧版与第三方客户端直接握手失败。出站方向仍按对端指纹校验证书。
- 跨网段不可发现：多播与子网扫描都限定在本机所在子网，不同网段需手动输入 IP。
- 实时通知（提升型常驻通知与焦点胶囊）需 Android 16 及以上，低版本只提供普通进度通知。
- 端到端校验只在本应用之间生效，对端为官方或第三方客户端时提示「未校验」。
- ColorOS 皮肤的大量原语要求 API 34，Android 12–13 走同文件内平替实现，视觉与原语存在差异。

## 源码构建

环境：JDK 21、Android SDK Platform 37、Gradle 9.6.1、Android Gradle Plugin 9.2.1。

```bash
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 单元测试
./gradlew ktlintCheck          # 代码风格（ignoreFailures = false）
./gradlew :app:lintDebug       # Android Lint
```

## 目录

| 路径 | 内容 |
|---|---|
| `app/src/main/java/org/localsend/miuix/core/` | 协议常量、JSON 编解码与文件存储 |
| `app/src/main/java/org/localsend/miuix/network/` | 发现、HTTP 服务端与客户端、TLS、限流、网页快传渲染 |
| `app/src/main/java/org/localsend/miuix/manager/` | 传输会话编排、设置映射、更新管理 |
| `app/src/main/java/org/localsend/miuix/transfer/` | 读写管道、速率与剩余时间、完整性校验 |
| `app/src/main/java/org/localsend/miuix/notification/` | 通知渠道、提升型常驻通知、发布与动作接收 |
| `app/src/main/java/org/localsend/miuix/service/` | 传输前台服务 |
| `app/src/main/java/org/localsend/miuix/ui/` | HyperOS 皮肤、共享组件与弹窗 |
| `app/src/main/java/org/localsend/miuix/ui/coui/` | ColorOS 皮肤页面、组件与弹窗 |
| `app/src/test/java/org/localsend/miuix/` | 54 个单元测试文件，覆盖协议一致性、校验、速率、权限策略等 |

## 许可

基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源。
