# localsend-miuix — Agent 约束入口

LocalSend 协议（v2）兼容的 Android 原生重写版，Compose + Miuix（HyperOS 风格），与官方客户端局域网互通。

## 构建与验证

- **环境要求**：JDK 17/21、Android SDK Platform 35/37、Gradle 9.6.1 + AGP 9.2.1
- **构建命令**：
  - 编译 Debug APK：`./gradlew assembleDebug`（产物位于 `app/build/outputs/apk/debug/app-debug.apk`）
  - 单元测试：`./gradlew testDebugUnitTest`
  - 代码风格检查：`./gradlew ktlintCheck`（ktlint 已接入构建门禁）
- **完成标准**：编译、测试、ktlint 全部通过。

## 项目约定

- **协议对齐**：以官方 LocalSend 协议为准，参考源码位于 `../localsend-reference`（冻结于官方 v1.18.2，只读）。设备发现、握手、传输及 Web Share 行为必须对照官方实现。
- **协议常量**：组播发现地址 `224.0.0.167:53317`、默认服务端口 `53317` 严格与官方一致。
- **UI 规范**：严格使用 `top.yukonga.miuix.kmp` 官方组件，严禁引入 Material 3 控件；组件与规范契约审查见 `docs/research/`。

## 内部文档索引

| 业务场景 | 文档路径 | 核心内容 |
|---|---|---|
| **UI 规范审查** | `docs/research/miuix-0.9.4-rc01-ui-review.md` | Miuix 0.9.4-rc01 官方组件契约、偏差改写清单与落点速查 |
| **TabRow 架构调研** | `docs/research/miuix-tabrow-implementation-research.md` | Miuix TabRow 与 TabRowWithContour 契约剖析、测量算法与落地建议 |
| **全量质量评估** | `docs/research/quality-assessment-report.md` | 23 项缺陷矩阵（P0~P3）、根因定位与整改路线图 |
| **对外更新日志** | `CHANGELOG.md` | 外部发布历史与版本变更记录（对外文档） |
