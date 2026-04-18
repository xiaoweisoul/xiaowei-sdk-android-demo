# xiaowei-sdk-android-demo

这是一个面向 Android 宿主接入方的 SDK 示例工程，用来演示如何在 App 中集成 `com.xiaowei.sdk:session-core:1.0.6`，并跑通最小会话闭环。

这个仓库本身不内置 SDK 二进制产物。运行前，请先把你拿到的 `xiaowei-sdk-android-1.0.6-maven-repo.zip` 解压到仓库根目录下的 `local-sdk-repo/`。

您还可以在这儿获得更详细的信息： http://www.xiaoweisoul.vip/docs/app-access-overview 

## 你能用这个 Demo 做什么

- 验证本地 Maven 仓库方式是否接入成功
- 演示如何创建 `XiaoweiSessionClient`
- 演示如何配置连接参数和 session token 获取逻辑
- 演示如何连接、发送文本、打开收音、接收事件回调
- 演示如何注册本地工具并观察工具调用事件

如果你只是想接 SDK，不一定需要直接修改这个 Demo。通常更推荐：

1. 先阅读 [Android SDK 快速接入指南](http://www.xiaoweisoul.vip/docs/android-sdk-quickstart)
2. 再参考这个 Demo 的代码结构完成自己的宿主接入

## 目录说明

- `app/`：Demo Android 应用模块
- `local-sdk-repo/`：本地 Maven 仓库目录，默认不提交到 Git

## 使用前准备

### 1. 准备 SDK 本地 Maven 仓库

把你拿到的压缩包解压到仓库根目录：

```text
xiaowei-sdk-android-demo/
  local-sdk-repo/
    com/
      xiaowei/
        sdk/
          session-core/
            1.0.6/
```

如果目录不对，Gradle 会在构建时直接报错。

### 2. 准备连接参数

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

在仓库根目录执行：

```bash
./gradlew :app:assembleDebug
```

如果你还没有把 SDK zip 解压到 `local-sdk-repo/`，构建会直接失败，并提示你先放置本地 Maven 仓库。

## Demo 使用说明

### 主页面

主页面提供以下能力：

- 查看当前 SDK 名称和版本
- 打开设置页填写连接参数
- `Connect / Disconnect`
- `Start Listen / Stop Listen`
- `Send Text`
- 查看会话状态和日志输出

### 设置页

设置页用于保存连接参数。点击 `Save` 后，主页面下一次 `Connect` 会直接读取这些值。

这个 Demo 会把配置保存在本地 `SharedPreferences` 中，方便重复测试。

设置页里的 `Integration App ID` 现在按字符串保存和提交，允许直接录入 `app_xxxxxxxx` 形式的业务标识。

## 推荐阅读顺序

如果你是第一次接入，建议按下面顺序看代码：

1. `app/src/main/java/com/xiaowei/sdk/demo/MainActivity.java`
2. `app/src/main/java/com/xiaowei/sdk/demo/AppPrefs.java`
3. `app/src/main/java/com/xiaowei/sdk/demo/DebugOpenApiSessionTokenProvider.java`

其中：

- `MainActivity.java`：展示连接、收音、发文本、事件监听的完整流程
- `AppPrefs.java`：展示如何管理连接参数
- `DebugOpenApiSessionTokenProvider.java`：展示如何在示例工程中获取 `session token`

## 重要说明

### 1. 这个 Demo 只是示例，不建议直接用于生产

尤其是 `DebugOpenApiSessionTokenProvider.java`，它会直接在客户端请求 token，只适合测试或演示。

生产环境更推荐：

- App 先请求你自己的业务服务端
- 由业务服务端安全地下发短期 `session token`
- SDK 再通过 `SessionTokenProvider` 使用这个 token 建连

### 2. 这个 Demo 默认使用本地 Maven 仓库方式接入 SDK

它的目标是尽量贴近真实客户接入方式，而不是依赖源码模块或私有工程路径。

### 3. 语音能力需要麦克风权限

如果你要测试 `Start Listen`，请确认设备已经授予 `RECORD_AUDIO` 权限。

## 常见问题

### 构建时报找不到 `com.xiaowei.sdk:session-core:1.0.6`

请检查：

- `local-sdk-repo/` 是否存在
- 目录结构是否完整
- 是否确实包含 `com/xiaowei/sdk/session-core/1.0.6/`

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
