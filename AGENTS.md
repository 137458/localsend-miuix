# localsend-miuix — Agent 约束入口

本文件是 AI Agent 与协作者进入本仓库的唯一约束入口：构建验证口径、协议对齐要求、两套皮肤的 UI 边界都在这里，改动前先读完对应小节。

## 构建与验证

环境要求：

- JDK 21（`jvmTarget = JVM_21`、`sourceCompatibility/targetCompatibility = 21`）
- Android SDK Platform 37（`compileSdk = 37`、`targetSdk = 35`、`minSdk = 31`）
- Gradle 9.6.1 + Android Gradle Plugin 9.2.1

验证命令：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew ktlintCheck
./gradlew :app:lintDebug
```

完成标准：以上四项全部通过，才算改动完成。`app/build.gradle.kts` 中 `ktlint.ignoreFailures = false`，格式不合规会直接失败，不做人工豁免。

## 项目约定

### 协议对齐

- 本仓库是官方 LocalSend 客户端的 Android 重写实现，协议行为以官方 Flutter 实现为准：`E:\project\references\localsend-reference`（v1.18.2 / `1.18.2+64`）。
- 协议常量集中在 `app/src/main/java/org/localsend/miuix/core/Constants.kt`：多播地址 `224.0.0.167`、默认端口 `53317`、协议版本 `2.2`，以及 `/api/localsend/v2/*` 路由。
- 新增或修改协议交互，必须同时兼容官方客户端与第三方 LocalSend 实现。私有扩展只能落在 `X-Localsend-Miuix-*` 请求头与 `checksum` 相关路由上，不得改动标准字段语义。

### UI 规范

- HyperOS 皮肤（`ui/screen/`、`ui/component/`）：只使用 Miuix 原件（`top.yukonga.miuix.kmp.*`），禁止引入 Material 3 组件。
- ColorOS 皮肤（`ui/coui/`）：只使用 coUI-Kit 原语（`com.itos.coui.*`）；库能力缺口按下面的回退边界处理，禁止直接把 Miuix 组件混进 `ui/coui/`。
- coUI 代码禁止读取 `MiuixTheme.colorScheme`；危险色必须经 `couiDangerColor()`（`ui/coui/theme/CouiSkinTheme.kt`）取得。
- 两套皮肤共用同一份业务状态、路由与设置持久化，分派点只在 `ui/App.kt` 根部，由 `model/UiSkin.kt` 的 `UiSkin` 枚举决定。

## coUI 能力与回退边界

当前依赖 `io.github.itoseo:coui:0.0.1-alpha11`，模块 `minSdk = 31`，而库中若干原语要求 API 34。处理方式统一为“`SDK_INT` 分派 + 同文件内低版本平替”，不允许为了用库而抬高 `minSdk`。

- TopAppBar 按钮组（`CouiCommonComponents.kt`，`COUI_TOP_BAR_BUTTON_MIN_SDK = 34`）：API 34 及以上用 `TopAppBarButtonGroup` / `TopAppBarButton`，低版本回退 `CouiIconAction` 与紧凑 `Row`。
- 悬浮导航栏（`CouiSkinNavigationBar.kt`，`COUI_FLOATING_NAV_BAR_MIN_SDK = 34`）：API 34 及以上用库 `NavigationBar`，低版本用同文件的 `CouiPlainNavigationBar`。
- 分段控件（`CouiHistoryScreen.kt`，`COUI_SEGMENTED_CONTROL_MIN_SDK = 34`）：API 34 及以上用 `SegmentedControl`，低版本用 `CouiPlainFilterTabs`。
- 命中区：库原语的视觉尺寸小于触控目标，图标按钮统一走 `CouiMinTouchTargetSize = 48.dp` 与 `Modifier.couiMinTouchTarget()`，视觉圆钮 40dp、命中区 48dp。
- 数量角标：库没有 Badge 原语，角标自绘，统一用 `CouiCountBadge` / `CouiBadgedIcon` / `CouiIncomingBadgeAction`，语义走 accent，禁止借用危险红。
- 文本输入：库 `InputDialog` 未暴露键盘类型槽位，需要数字键盘或多行、附加内容的场景统一用项目自建的 `CouiTextInputDialog`（`ui/coui/dialog/CouiDeviceAndInputDialogs.kt`，`keyboardType` 参数 + `CouiDialogTextField`），不要在 `ui/coui/` 里回退到 Miuix 输入弹窗。
- 嵌套 Scaffold：`ui/coui/` 内嵌在 `App` 底栏之上的页面必须显式传 `windowInsets = WindowInsets(0, 0, 0, 0)`，避免重复消费系统栏 inset；独立顶层路由（如 `CouiUpdateScreen`）保留默认 insets。
- 开关、单选、复选框、菜单、下拉、滚动与过度滚动等其余原语，优先用库实现（`Switch`、`RadioButton`、`Checkbox`、`Menu`、`couiVerticalScroll` / `rememberCouiOverscrollEffect`），不要手写等价控件。

## 文档与提交

- 活文档只有 `README.md`、本文件与 `CHANGELOG.md`。仓库不保留时点调研报告、代码审查报告与实施计划，能力边界与约束写进本文件。
- `CHANGELOG.md` 只向顶部的“未发布”区块追加条目，不改历史版本区块，不为文档修正单独开版本条目。
- 中文技术文档遵循中英文与数字之间加空格、路径与命令用反引号、标题层级不跳级、不使用 Emoji。
- 提交信息使用 Conventional Commits 中文描述，禁止 Emoji；一个逻辑变更一个提交，只 `git add` 实际改动路径。
