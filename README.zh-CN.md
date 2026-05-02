# xiaowei-sdk-android-demo

这是一个面向 Android 宿主接入方的 SDK 示例工程，用来演示如何在 App 中集成 `vip.xiaoweisoul.sdk:session-core:1.0.8`，并跑通最小会话闭环。

默认情况下，这个 Demo 会优先通过 `mavenCentral()` 解析 SDK。

如果你希望显式验证当前代码库构建出来的本地 SDK，可在构建时传入 `-PuseLocalSdkRepo=true`。

您还可以在这儿获得更详细的信息： http://www.xiaoweisoul.vip/docs/app-access-overview 

## 你能用这个 Demo 做什么

- 验证 Maven Central 与本地 Maven 仓库两种接入方式
- 演示如何创建 `XiaoweiSessionClient`
- 演示如何配置连接参数和 session token 获取逻辑
- 演示如何连接、发送文本、打开收音、接收事件回调
- 演示主页面语言切换、四个内置元神快速切换与日志排查流程
- 演示平台录音前处理状态的启动预检/录音实检，以及 Assistant PCM 下行播放
- 演示如何注册本地工具、触发表情动画并观察工具调用事件

如果你只是想接 SDK，不一定需要直接修改这个 Demo。通常更推荐：

1. 先阅读 [Android SDK 快速接入指南](http://www.xiaoweisoul.vip/docs/android-sdk-quickstart)
2. 再参考这个 Demo 的代码结构完成自己的宿主接入

## 目录说明

- `app/`：Demo Android 应用模块
- `local-sdk-repo/`：本地 Maven 仓库目录，仅在显式启用本地模式时用于解析 SDK

## 使用前准备

### 1. 默认使用 Maven Central

如果你已经发布了 SDK，直接执行：

```bash
./gradlew :app:assembleDebug
```

### 2. 可选：切换到本地 Maven 仓库

如果你正在验证当前代码库构建出来的 SDK，请先在 SDK 仓库执行：

```bash
./build_android_sdk.sh
```

然后确认本仓库根目录存在：

```text
xiaowei-sdk-android-demo/
  local-sdk-repo/
    vip/
      xiaoweisoul/
        sdk/
          session-core/
            1.0.8/
```

然后在本仓库显式启用本地模式：

```bash
./gradlew -PuseLocalSdkRepo=true :app:assembleDebug
```

如果目录不对，Gradle 会直接报错。

### 3. 准备连接参数

Demo 运行时需要你自己填写以下参数：

- `OpenAPI Base URL`
- `Access Key ID`
- `Access Key Secret`
- `Integration App ID`
- `Soul ID`
- `WS URL`
- `Protocol Version`
- `Logical Device ID`
- `Logical Client ID`

其中需要特别注意：

- `Integration App ID` 填的是控制台“应用中心”列表里展示的 `app_id`。
- 当前值格式是字符串，例如 `app_g1ht6a8o`。
- `Soul ID` 填的是元神配置里的稳定标识，例如 `soul_acme_companion_main_v1`。

这些值不会在仓库中提供真实默认值。请使用你自己的测试环境配置。

## 如何运行

### Android Studio

1. 打开 Android Studio
2. 选择 `Open`
3. 打开当前仓库目录 `xiaowei-sdk-android-demo`
4. 等待 Gradle Sync 完成
5. 运行 `app`

### 命令行

在仓库根目录执行默认 Maven Central 模式：

```bash
./gradlew :app:assembleDebug
```

如果你希望显式验证本地 SDK 模式，请改用：

```bash
./gradlew -PuseLocalSdkRepo=true :app:assembleDebug
```

## 快速体验

为了方便大家快速体验，这个 Demo 已经内置了一套可直接联调的默认配置，包括：

- 一个内置应用
- 一组内置 API Key
- 四个内置元神

首次运行后，你可以直接使用这套内置配置进行体验；如果之前改过配置，也可以在设置页点击 `Restore Defaults` 恢复为内置值，再返回主页面点击 `Connect` 开始体验。

需要说明的是：这套内置配置主要用于快速体验，普通用户只能直接使用，不能查看或自行管理这些配置对应的后台资源。

如果你希望查看、创建或维护自己的应用、API Key 和元神信息，需要联系客服注册账号并开通对应权限，然后在你自己的控制台中完成配置。

这些默认值统一定义在 `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java` 中，并会回填到设置页对应字段。

设置页里与快速体验直接相关的字段和值如下：

| 设置项 | 内置值 | 说明 |
|---|---|---|
| `OpenAPI Base URL` | `http://api.xiaoweisoul.vip` | 内置 OpenAPI 地址 |
| `WS URL` | `ws://soul.xiaoweisoul.vip` | 内置 WebSocket 地址 |
| `Access Key ID` | `ak_be60d1530176d7e4b915ed9c` | 内置 API Key ID |
| `Access Key Secret` | `sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7` | 内置 API Key Secret |
| `Integration App ID` | `app_remav935` | 内置应用 ID |
| `Protocol Version` | `1` | 协议版本 |
| `Logical Device ID` | `app.demo.device-001` | 默认逻辑设备 ID |
| `Logical Client ID` | `sdk.demo.client-001` | 默认逻辑客户端 ID |
| `Soul ID` | `soul_demo_chinese_female_chat_assistant_v1` | 当前默认元神；也可以改成下表中的任意一个内置聊天助手元神 |

内置的 4 个聊天助手元神 `soul_id` 如下：

| 元神名称 | `soul_id` |
|---|---|
| 聊天助手（中文女生） | `soul_demo_chinese_female_chat_assistant_v1` |
| 聊天助手（中文男生） | `soul_demo_chinese_male_chat_assistant_v1` |
| 聊天助手（日文女生） | `soul_demo_japanese_female_chat_assistant_v1` |
| 聊天助手（日文男生） | `soul_demo_japanese_male_chat_assistant_v1` |

如果你想快速体验不同元神，最直接的方式就是进入设置页，只修改 `Soul ID` 字段为上面任意一个值，保存后重新连接。

## Demo 使用说明

### 主页面

主页面提供以下能力：

- 查看当前 SDK 名称和版本
- 切换主页面展示语言（中文 / 日文）
- 打开设置页填写连接参数
- 使用内置下拉框快速切换四个默认元神
- `Connect / Disconnect`
- `Start Listen / Stop Listen`
- `Send Text`
- 清空日志、查看会话状态和日志输出
- 观察平台效果器状态日志、MCP 工具调用日志与表情动画反馈

### 设置页

设置页用于保存连接参数、TTS 播放策略，并支持 `Restore Defaults` 恢复公开默认值。点击 `Save` 后，主页面下一次 `Connect` 会直接读取这些值。

这个 Demo 会把配置保存在本地 `SharedPreferences` 中，方便重复测试。

设置页里的 `Integration App ID` 现在按字符串保存和提交，允许直接录入 `app_xxxxxxxx` 形式的业务标识。

## 推荐阅读顺序

如果你是第一次接入，建议按下面顺序看代码：

1. `app/src/main/java/vip/xiaoweisoul/sdk/demo/MainActivity.java`
2. `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java`
3. `app/src/main/java/vip/xiaoweisoul/sdk/demo/DebugOpenApiSessionTokenProvider.java`

其中：

- `MainActivity.java`：展示连接、收音、发文本、语言切换、元神切换、事件监听、本地工具注册和日志排查的完整流程
- `AppPrefs.java`：展示如何管理连接参数
- `DebugOpenApiSessionTokenProvider.java`：展示如何在示例工程中获取 `session token`

## 重要说明

### 1. 这个 Demo 只是示例，不建议直接用于生产

尤其是 `DebugOpenApiSessionTokenProvider.java`，它会直接在客户端请求 token，只适合测试或演示。

生产环境更推荐：

- App 先请求你自己的业务服务端
- 由业务服务端安全地下发短期 `session token`
- SDK 再通过 `SessionTokenProvider` 使用这个 token 建连

### 2. 这个 Demo 默认使用 Maven Central 接入 SDK

只有在你显式传入 `-PuseLocalSdkRepo=true` 时，才会切换到仓库根目录下的 `local-sdk-repo/`。

### 3. 语音能力需要麦克风权限

如果你要测试 `Start Listen`，请确认设备已经授予 `RECORD_AUDIO` 权限。

### 4. GitHub Releases 中的 APK 仅用于公开测试演示

仓库 Release 页面提供的 APK 仅用于公开测试和试用演示，不代表正式发布签名体系。

当前公开测试包使用单独的 Demo 签名，仅用于方便安装和体验；请不要将其视为生产环境或正式商用发布依据。

如果后续进入正式发布或长期维护阶段，应切换到独立保管的正式发布 keystore，并按正式版本策略重新签名和分发。

## 常见问题

### 构建时报找不到 `vip.xiaoweisoul.sdk:session-core:1.0.8`

请检查：

- 默认模式下，检查 Maven Central 上是否已经发布该版本，以及当前网络是否能访问 Maven Central
- 如果你启用了 `-PuseLocalSdkRepo=true`，再检查 `local-sdk-repo/` 是否存在，以及是否确实包含 `vip/xiaoweisoul/sdk/session-core/1.0.8/`

### 点击 Connect 后失败

请优先检查：

- 设置页里的 `OpenAPI Base URL` 是否正确
- `WS URL` 是否正确
- `Access Key ID / Secret` 是否正确
- `Integration App ID` 是否填写为应用中心展示的字符串 `app_id`，例如 `app_g1ht6a8o`
- `Soul ID`、`Protocol Version` 是否匹配服务端要求

### 连接成功但无法开麦

请检查：

- 是否已授予 `RECORD_AUDIO`
- 是否真的已经进入 `CONNECTED` 状态
- 是否点击了 `Start Listen`
