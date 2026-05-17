# xiaowei-sdk-android-demo

这是一个面向 Android 宿主接入方的 SDK 示例工程，用来帮助你更快完成基础接入，并验证最小会话闭环。

您还可以在这儿获得更详细的信息： http://www.xiaoweisoul.vip/docs/app-access-overview

## 你能用这个 Demo 做什么

- 快速验证 Android App 是否已经具备基础接入条件
- 参考设置页准备联调参数
- 演示连接、发文本、开麦、收事件的基础流程
- 演示主页面语言切换、角色切换和日志排查方式
- 演示记忆能力场景下 `End User ID` 的填写方式

如果你只是想接 SDK，通常更推荐：

1. 先阅读 [Android SDK 快速接入指南](http://www.xiaoweisoul.vip/docs/android-sdk-quickstart)
2. 再参考这个 Demo 的页面流程和配置项完成自己的宿主接入

## 目录说明

- `app/`：Demo Android 应用模块

## 使用前准备

### 1. 构建 Demo

```bash
./gradlew :app:assembleDebug
```

### 2. 准备连接参数

Demo 运行时需要你自己填写以下参数：

- `OpenAPI Base URL`
- `Access Key ID`
- `Access Key Secret`
- `Integration App ID`
- `End User ID`
- `Soul ID`
- `WS URL`
- `Protocol Version`
- `Logical Device ID`
- `Logical Client ID`

其中需要特别注意：

- `Integration App ID` 填的是控制台“应用中心”列表里展示的 `app_id`。
- 当前值格式是字符串，例如 `app_g1ht6a8o`。
- `End User ID` 填的是 App 侧真实终端用户的稳定唯一标识，例如 `user_10001`。
- 如果要验证记忆能力，`End User ID` 不能多个用户共用，也不应该在同一用户身上频繁变化。
- `Soul ID` 填的是元神配置里的稳定标识，例如 `soul_acme_companion_main_v1`。

这些值不会在仓库中提供你的正式业务默认值。请根据自己的测试环境或业务环境填写。

## 如何运行

### Android Studio

1. 打开 Android Studio
2. 选择 `Open`
3. 打开当前仓库目录 `xiaowei-sdk-android-demo`
4. 等待 Gradle Sync 完成
5. 运行 `app`

### 命令行

在仓库根目录执行：

```bash
./gradlew :app:assembleDebug
```

## 快速体验

为了方便大家快速体验，这个 Demo 已经内置了一套可直接联调的默认配置，包括：

- 一个内置应用
- 一组内置 API Key
- 多个内置聊天助手元神

首次运行后，你可以直接使用这套内置配置进行体验；如果之前改过配置，也可以在设置页点击 `Restore Defaults` 恢复为内置值，再返回主页面点击 `Connect` 开始体验。

需要说明的是：这套内置配置主要用于快速体验，普通用户只能直接使用，不能查看或自行管理这些配置对应的后台资源。

如果你希望查看、创建或维护自己的应用、API Key 和元神信息，需要联系客服注册账号并开通对应权限，然后在你自己的控制台中完成配置。

设置页里与快速体验直接相关的字段和值如下：

| 设置项 | 内置值 | 说明 |
|---|---|---|
| `OpenAPI Base URL` | `http://api.xiaoweisoul.vip` | 内置 OpenAPI 地址 |
| `WS URL` | `ws://soul.xiaoweisoul.vip` | 内置 WebSocket 地址 |
| `Access Key ID` | `ak_be60d1530176d7e4b915ed9c` | 内置 API Key ID |
| `Access Key Secret` | `sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7` | 内置 API Key Secret |
| `Integration App ID` | `app_remav935` | 内置应用 ID |
| `End User ID` | `sdk-demo-ghtao-01` | Demo 默认终端用户 ID；用于隔离记忆体验 |
| `Protocol Version` | `1` | 协议版本 |
| `Logical Device ID` | `app.demo.device-001` | 默认逻辑设备 ID |
| `Logical Client ID` | `sdk.demo.client-001` | 默认逻辑客户端 ID |
| `Soul ID` | `soul_demo_chinese_female_chat_assistant_v1` | 当前默认元神；也可以改成下表中的任意一个内置聊天助手元神 |

内置的聊天助手元神 `soul_id` 如下：

| 元神名称 | `soul_id` |
|---|---|
| 聊天助手（中文女生） | `soul_demo_chinese_female_chat_assistant_v1` |
| 聊天助手（中文男生） | `soul_demo_chinese_male_chat_assistant_v1` |
| 聊天助手（日文女生） | `soul_demo_japanese_female_chat_assistant_v1` |
| 聊天助手（日文男生） | `soul_demo_japanese_male_chat_assistant_v1` |
| 聊天助手（中日女生） | `soul_demo_chinese_japanese_female_chat_assistant_v1` |

如果你想快速体验不同元神，最直接的方式就是进入设置页，只修改 `Soul ID` 字段为上面任意一个值，保存后重新连接。

## Demo 使用说明

### 主页面

主页面提供以下能力：

- 查看当前 SDK 名称和版本
- 切换主页面展示语言（中文 / 日文）
- 打开设置页填写连接参数
- 使用内置下拉框快速切换默认元神
- `Connect / Disconnect`
- `Start Listen / Stop Listen`
- `Send Text`
- 清空日志、查看会话状态和日志输出

### 设置页

设置页用于保存连接参数、TTS 播放策略，并支持 `Restore Defaults` 恢复公开默认值。点击 `Save` 后，主页面下一次 `Connect` 会直接读取这些值。

这个 Demo 会把配置保存在本地 `SharedPreferences` 中，方便重复测试。

设置页里的 `Integration App ID` 现在按字符串保存和提交，允许直接录入 `app_xxxxxxxx` 形式的业务标识。

设置页里的 `End User ID` 用来区分当前 App 中的终端用户；如果你要验证记忆能力，请确保同一个真实用户始终使用稳定且不重复的值。

## 开启记忆能力

如果你希望当前 Demo 能用上记忆能力，需要同时满足两件事：

- 当前联调环境已经为目标元神开启记忆
- Demo 设置页中填写了正确且稳定的 `End User ID`

这个 Android Demo 属于应用接入场景，因此同一个用户是否能持续命中自己的记忆，关键在于你是否一直使用同一个 `End User ID`。

### 操作步骤

1. 确认当前联调环境已经为目标元神开启记忆能力；为了方便体验，当前 Demo 默认联调环境已开启该能力。
2. 打开 Demo 设置页，填写正确的 `Integration App ID`、`Soul ID` 和 `End User ID`。
3. 其中 `End User ID` 必须是你业务里能稳定标识同一个终端用户的值，例如 `user_10001`。
4. 点击 `Save` 保存设置，再回到主页面点击 `Connect`。
5. 使用同一个用户身份持续对话，等待系统在后续会话中逐步体现记忆效果。
6. 后续再次连接时，只要 `Integration App ID`、`Soul ID`、`End User ID` 保持一致，就更容易命中同一位用户的记忆。

### 使用时要注意

- 不同真实用户不要共用同一个 `End User ID`，否则记忆会串。
- 同一个真实用户不要今天用 `user_10001`、明天改成 `user_10001_v2`，否则会被视为新的用户。
- 刚说过的话不一定会立刻在当前轮生效；记忆通常需要在后续对话中逐步体现。
- `Restore Defaults` 会把 `End User ID` 恢复成 Demo 默认值 `sdk-demo-ghtao-01`，这只适合单人联调用途。

## 重要说明

### 1. 这个 Demo 只是示例，不建议直接用于生产

生产环境更推荐：

- 不要在客户端直接放置敏感生产凭证
- 建议由你自己的业务后端安全处理鉴权与会话相关逻辑
- Demo 中的实现仅用于帮助你快速体验和联调

### 2. 语音能力需要麦克风权限

如果你要测试 `Start Listen`，请确认设备已经授予 `RECORD_AUDIO` 权限。

### 3. GitHub Releases 中的 APK 仅用于公开测试演示

仓库 Release 页面提供的 APK 仅用于公开测试和试用演示，不代表正式发布签名体系。

当前公开测试包使用单独的 Demo 签名，仅用于方便安装和体验；请不要将其视为生产环境或正式商用发布依据。

如果后续进入正式发布或长期维护阶段，应切换到独立保管的正式发布 keystore，并按正式版本策略重新签名和分发。

## 常见问题

### 构建失败

请检查：

- 当前网络是否正常
- Android Studio / Gradle 环境是否完整
- 是否按本文步骤在仓库根目录执行了构建命令

### 点击 Connect 后失败

请优先检查：

- 设置页里的 `OpenAPI Base URL` 是否正确
- `WS URL` 是否正确
- `Access Key ID / Secret` 是否正确
- `Integration App ID` 是否填写为应用中心展示的字符串 `app_id`，例如 `app_g1ht6a8o`
- 如果要验证记忆能力，`End User ID` 是否已填写为当前真实用户的稳定唯一标识
- `Soul ID`、`Protocol Version` 是否正确

### 连接成功但无法开麦

请检查：

- 是否已授予 `RECORD_AUDIO`
- 是否真的已经进入 `CONNECTED` 状态
- 是否点击了 `Start Listen`
