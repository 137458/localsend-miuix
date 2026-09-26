# localsend-miuix — Agent 约束入口

LocalSend 协议（v2）兼容的 Android 原生重写版，Compose + Miuix（HyperOS 风格），与官方客户端局域网互通。

## 构建与验证

- 环境：JDK 17/21、Android SDK Platform 35/37、Gradle 9.6.1 + AGP 9.2.1
- 编译 Debug APK：`./gradlew assembleDebug`（产物 `app/build/outputs/apk/debug/app-debug.apk`）
- 单元测试：`./gradlew testDebugUnitTest`
- 代码风格：ktlint 校验已接入构建门禁
- 完成标准 = 编译、测试、ktlint 全部通过。

## 项目约定

- 协议实现以官方 LocalSend 为准：参考克隆位于 `../localsend-reference`（冻结于官方 v1.18.2，只读，勿改动）。涉及协议行为（设备发现、握手、传输、Web Share）时先对照官方实现。
- 组播发现地址 `224.0.0.167:53317` 等协议常量保持与官方一致。
- UI 规范：严格使用 `top.yukonga.miuix.kmp` 官方组件，严禁引入 Material 3 控件；组件规范审查记录见 `docs/research/`。

## 文档索引

| 何时读 | 文档 |
|---|---|
| UI 规范审查 / 质量评估 | `docs/research/` |
| 更新日志 | `CHANGELOG.md` |
