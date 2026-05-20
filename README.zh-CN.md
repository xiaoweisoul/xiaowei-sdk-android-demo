# xiaowei-sdk-android-demo

这是一个面向 Android 宿主接入方的 SDK 示例工程，用来帮助你在 App 中集成 `vip.xiaoweisoul.sdk:session-core:1.1.1`，并验证最小会话闭环。

公开接入默认走 `mavenCentral()`。普通接入方通常只需要这一条路径；`-PuseLocalSdkRepo=true` 仅保留给 SDK 维护 / 联调场景。

您还可以在这儿获得更详细的信息： http://www.xiaoweisoul.vip/docs/app-access-overview

## 你能用这个 Demo 做什么

- 验证 Maven Central 接入方式
- 演示如何创建 `XiaoweiSessionClient`
- 演示如何配置连接参数、session token 和 `End User ID`
- 演示如何连接、发送文本、打开收音、接收事件回调
- 演示如何理解 `onUserInputCommitted`、`onAssistantSentence`、`onAssistantPcm` 三类核心输出信号
- 演示长回复、多句文本、插话打断 `barge_in`、本地 PCM 播放收口这些典型时序
- 演示主页面语言切换、动态加载可用元神与日志排查流程
- 演示平台录音前处理状态的启动预检/录音实检，以及 Assistant PCM 下行播放
- 演示如何注册本地工具、触发表情动画并观察工具调用事件

如果你只是想接 SDK，不一定需要直接修改这个 Demo。通常更推荐：

1. 先阅读 [Android SDK 快速接入指南](http://www.xiaoweisoul.vip/docs/android-sdk-quickstart)
2. 再参考这个 Demo 的代码结构完成自己的宿主接入

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
- 如果要验证记忆能力，同一个真实用户应持续使用同一个 `End User ID`，不同用户不要共用。
- `Soul ID` 填的是元神配置里的稳定标识，例如 `soul_acme_companion_main_v1`。

这些值不会在仓库中提供你的正式业务默认值。请根据自己的测试环境或业务环境填写。

此外，当前 Demo 在代码中还会通过 `hello.session_config` 额外上报两项会话级配置，这两项不在设置页中持久化维护：

- `prompt`：用于给当前会话追加一段个性化角色定义提示词
- `idle_timeout_ms`：用于覆盖当前会话的静音超时配置

这两项默认值定义在 `app/src/main/java/vip/xiaoweisoul/sdk/demo/MainActivity.java` 中的：

- `DEMO_HELLO_SESSION_PROMPT`
- `DEMO_HELLO_SESSION_IDLE_TIMEOUT_MS`

## 如何运行

### Android Studio

1. 打开 Android Studio
2. 选择 `Open`
3. 打开当前仓库目录 `xiaowei-sdk-android-demo`
4. 等待 Gradle Sync 完成
5. 运行 `app`

### 命令行

在仓库根目录执行默认模式：

```bash
./gradlew :app:assembleDebug
```

如果你是 SDK 维护者，并且已经准备好了 `local-sdk-repo/`，才需要改用：

```bash
./gradlew -PuseLocalSdkRepo=true :app:assembleDebug
```

## 快速体验

为了方便大家快速体验，这个 Demo 已经内置了一套可直接联调的公开体验配置，包括：

- 一个内置应用
- 一组内置 API Key
- 一个默认 `End User ID`
- 一个默认 `Soul ID`

首次运行后，你可以直接使用这套内置配置进行体验；如果之前改过配置，也可以在设置页点击 `Restore Defaults` 恢复为内置值，再返回主页面点击 `Connect` 开始体验。

需要说明的是：这套内置配置主要用于快速体验，普通用户只能直接使用，不能查看或自行管理这些配置对应的后台资源。

如果你希望查看、创建或维护自己的应用、API Key 和元神信息，需要联系客服注册账号并开通对应权限，然后在你自己的控制台中完成配置。

这些公开默认值主要定义在 `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java` 中，并会回填到设置页对应字段。主页面里的元神下拉框会根据当前 `Access Key ID / Secret` 动态从 OpenAPI 拉取可用角色列表。

设置页里与快速体验直接相关的字段和值如下：

| 设置项 | 内置值 | 说明 |
|---|---|---|
| `OpenAPI Base URL` | `http://api.xiaoweisoul.vip` | 内置 OpenAPI 地址 |
| `WS URL` | `ws://soul.xiaoweisoul.vip` | 内置 WebSocket 地址 |
| `Access Key ID` | `ak_be60d1530176d7e4b915ed9c` | 内置 API Key ID |
| `Access Key Secret` | `sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7` | 内置 API Key Secret |
| `Integration App ID` | `app_remav935` | 内置应用 ID |
| `End User ID` | `app_demo_end_user_001` | Demo 默认终端用户 ID；用于区分公开体验中的用户身份 |
| `Protocol Version` | `1` | 协议版本 |
| `Logical Device ID` | `app.demo.device-001` | 默认逻辑设备 ID |
| `Logical Client ID` | `sdk.demo.client-001` | 默认逻辑客户端 ID |
| `Soul ID` | `soul_demo_chinese_female_chat_assistant_v1` | 当前默认元神；如果角色列表加载成功，也可以切换为下表中的示例值 |

当前公开体验环境里，常见的聊天助手 `soul_id` 示例包括：

| 元神名称 | `soul_id` |
|---|---|
| 通用智能助手 | `soul_demo_chinese_general_ai_assistant_v1` |
| 聊天助手（中文女生） | `soul_demo_chinese_female_chat_assistant_v1` |
| 聊天助手（中文男生） | `soul_demo_chinese_male_chat_assistant_v1` |
| 聊天助手（日文女生） | `soul_demo_japanese_female_chat_assistant_v1` |
| 聊天助手（日文男生） | `soul_demo_japanese_male_chat_assistant_v1` |
| 聊天助手（中日女生） | `soul_demo_chinese_japanese_female_chat_assistant_v1` |

如果你想快速体验不同元神，最直接的方式是：

1. 等主页面成功加载可用角色列表后，直接用下拉框切换
2. 或者在设置页里手动修改 `Soul ID` 后重新连接
3. 如果您想在握手协议里面传入自己的个性化角色提示词，请选择 “通用智能助手” 元神

## Demo 使用说明

### 主页面

主页面提供以下能力：

- 查看当前 SDK 名称和版本
- 切换主页面展示语言（中文 / 日文）
- 打开设置页填写连接参数
- 使用下拉框快速切换当前可用元神
- `Connect / Disconnect`
- `Realtime / Manual` 语音输入模式切换
- `Manual` 模式下按住说话、松开发送
- `Send Text`
- 清空日志、查看会话状态和日志输出
- 观察平台效果器状态日志、MCP 工具调用日志与表情动画反馈

主页面日志里建议重点看下面几类标签：

- `[用户输入已确认]`：服务端已经确认了当前用户输入
- `[AI文本句子]`：当前收到一条 AI 文本句子
- `[PCM下发]`：当前回复的首帧 PCM 已经到达
- `[AI回复结束]`：服务端这轮回复已经结束，但不代表本地播放器已经播完
- `[AI回复汇总]`：Demo 按 `responseId` 聚合后的完整文本预览
- `[TtsPlayer] [本地播放开始]`：宿主本地播放链路已经启动
- `[TtsPlayer] [本地播放收口]`：Demo 里的本地播放链路进入收口/空闲态，更接近广告切入参考时机

### 设置页

设置页用于保存连接参数、TTS 播放策略，并支持 `Restore Defaults` 恢复公开默认值。点击 `Save` 后，主页面下一次 `Connect` 会直接读取这些值。

这个 Demo 会把配置保存在本地 `SharedPreferences` 中，方便重复测试。

设置页里的 `Integration App ID` 现在按字符串保存和提交，允许直接录入 `app_xxxxxxxx` 形式的业务标识。

设置页里的 `End User ID` 建议直接填写真实终端用户的稳定唯一标识，尤其是在验证记忆能力时。

## 语音输入模式与 `abort` 语义

当前 Demo 把“收音模式”和“打断 AI 回复”分成了两类动作，建议按下面的方式理解：

- `Realtime`
  - 连接后进入持续收音。
  - 由服务端按实时对话策略决定何时提交输入、何时收口。
- `Manual`
  - 按住大圆按钮开始收音。
  - 松开按钮时发送 `stopListen()`，作为本轮语音输入的显式收口。
  - 如果当前 AI 还在说话，再次按下前会先发送 `abortSpeaking()`，再开始本轮 manual 收音。
- `abortSpeaking()`
  - 只负责请求服务端立刻中止当前 assistant 回复。
  - 不会断开连接。
  - 不会替代 `stopListen()`。
  - 不会主动停止当前 realtime 收音。

因此，如果宿主在 `Realtime` 模式下误调用 `abortSpeaking()`，最常见的现象是“当前 AI 回复被打断，但实时收音还在继续”。这不是模式切换错误，而是接口职责本身如此。

如果你只想结束收音，请调用 `stopListen()`；如果你只想打断 AI 当前播报，请调用 `abortSpeaking()`。这两个动作在宿主侧应该明确区分。

## 输出生命周期说明

这一节专门解释当前 `session-core` 在宿主侧最常见、也最容易混淆的三个输出信号：

- 用户输入确认
- AI 文本输出
- AI 语音 PCM 下发

如果你正在看 Demo 日志，或者正在接广告插播、打断、背景音恢复之类的逻辑，建议先把这节读完。

### 先记住三个结论

1. `onUserInputCommitted(event)` 是“用户输入被服务端正式确认”，不是 AI 回复。
2. `onAssistantSentence(event)` 里的 `state=start` 才是 AI 文本句子输出。
3. `onAssistantSentence(event)` 里的 `state=stop` 表示“服务端当前回复结束”，不等于“本地播放器已经播完”。

### 当前对外回调怎么理解

#### `onUserInputCommitted(event)`

这个回调表示服务端已经接受并确认了当前用户输入。

常见场景：

- 用户说了一句话，ASR 最终结果确认后回调
- 宿主主动 `sendText(...)` 后，服务端确认了这条文本输入

这个事件通常用于：

- 更新聊天输入区
- 建立新的 `turnId`
- 排查语音输入和文本输入有没有成功进入会话

它不表示 AI 已经开始回答。

#### `onAssistantSentence(event)`

这是当前 SDK 对外暴露的 AI 文本输出主入口。

它只有两种状态：

- `state=start`：服务端下发了一句可展示文本，文本内容在 `event.getText()`
- `state=stop`：服务端当前回复结束，结束原因在 `event.getStopReason()`

可以把它理解成“句子级文本 + 回复结束信号”。

#### `onAssistantPcm(frame)`

这是 AI 语音的下行 PCM 数据回调。

每次回调只是一帧音频数据，包含：

- `turnId`
- `responseId`
- `seq`
- `ptsUs`
- `data`

这里没有：

- `finished`
- `eof`
- `isLastFrame`
- 文本内容

所以宿主不能指望从 `PcmFrame` 自己读出一个“这一轮语音彻底结束”的独立标志。

### `turnId` 和 `responseId` 怎么看

建议这样理解：

- `turnId`：更偏向“这一轮用户输入 / 会话回合”
- `responseId`：更偏向“这一轮 AI 回复流”

在多数场景里，一轮 AI 回复会表现成：

1. 一个 `turnId`
2. 一个 `responseId`
3. 多次 `onAssistantSentence(state=start)`
4. 多次 `onAssistantPcm(frame)`
5. 一次 `onAssistantSentence(state=stop)`

如果宿主要把多句文本拼成完整回复，推荐按 `responseId` 聚合。

### 苏州两日游案例

下面用“帮我规划一下苏州两日游”这个长回复例子，展示当前时序。

#### 正常长回复

用户说：

```text
帮我规划一下苏州两日游
```

SDK 侧可以理解成：

```text
onUserInputCommitted(
  source=asr 或 text,
  text="帮我规划一下苏州两日游",
  turnId=101
)

onAssistantSentence(
  state=start,
  index=1,
  turnId=101,
  responseId=resp-A,
  text="可以，我先按两天一晚给你拆一下。"
)

onAssistantPcm(seq=0, responseId=resp-A)
onAssistantPcm(seq=1, responseId=resp-A)

onAssistantSentence(
  state=start,
  index=2,
  turnId=101,
  responseId=resp-A,
  text="第一天建议你先去拙政园和苏州博物馆。"
)

onAssistantPcm(seq=20, responseId=resp-A)

onAssistantSentence(
  state=start,
  index=3,
  turnId=101,
  responseId=resp-A,
  text="晚上可以去平江路，吃饭和夜游都比较合适。"
)

onAssistantSentence(
  state=stop,
  turnId=101,
  responseId=resp-A,
  reason=eos
)
```

这里有两个很重要的点：

1. AI 文本不是一次整段给出，而是分多次、按句子给出。
2. `state=stop` 收口的是“服务端这一轮回复”，不是“宿主本地播放链路”。

#### 用户插话打断 `barge_in`

AI 说到一半，用户插话：

```text
预算两千以内呢？
```

SDK 侧可以理解成：

```text
onAssistantSentence(
  state=start,
  turnId=101,
  responseId=resp-A,
  text="第一天建议你先去拙政园和苏州博物馆。"
)

onAssistantPcm(seq=30, responseId=resp-A)
onAssistantPcm(seq=31, responseId=resp-A)

onAssistantSentence(
  state=stop,
  turnId=101,
  responseId=resp-A,
  reason=barge_in
)

onUserInputCommitted(
  source=asr,
  text="预算两千以内呢？",
  turnId=102
)

onAssistantSentence(
  state=start,
  turnId=102,
  responseId=resp-B,
  text="如果预算控制在两千以内，可以优先住在观前街附近。"
)
```

这个例子里：

- `resp-A` 是旧回复
- `resp-B` 是新回复
- `barge_in` 表示旧回复被新输入打断

宿主如果自己实现播放器，通常还需要在本地进一步停掉旧回复的剩余尾音。

### 宿主接入建议

#### 如果你要展示完整 AI 回复文本

推荐按 `responseId` 把多次 `state=start` 的 `text` 累积起来，等到对应的 `state=stop` 再收口。

#### 如果你要做广告插播或背景音恢复

不要只看 `onAssistantSentence(state=stop)`。

更稳妥的做法是：

1. 用 `state=stop` 判断服务端已经结束当前回复
2. 再等宿主本地播放器真正收口
3. 最后再切广告、恢复背景音或开始下一段本地音频

#### 如果你要处理打断

推荐区分两类 stop：

- 正常结束：例如 `reason=eos`
- 打断结束：例如 `barge_in`、`input_text`、`stopword`

打断结束通常意味着宿主要更积极地停掉旧回复残留音频，避免尾音串到下一轮。

## Assistant PCM 播放与广告插播参考

这一节专门解释一个高频问题：

> 为什么已经收到了 `onAssistantSentence(state=stop)`，广告一播还是可能把 AI 尾音截断？

根因通常不在服务端，而在于宿主把“服务端回复结束”和“本地播放完成”混成了一个时机。

### 先说结论

#### 结论 1

`onAssistantSentence(state=stop)` 表示：

```text
服务端当前回复已经结束
```

它不表示：

```text
宿主本地播放器已经播完
```

#### 结论 2

`onAssistantPcm(frame)` 本身没有 `eof` 或 `isLastFrame` 之类的字段，所以宿主不能靠某一帧 PCM 自己判断“已经彻底结束”。

#### 结论 3

如果你的业务需要精确决定广告插播时机，推荐至少分成两步：

1. 先等服务端 stop
2. 再等宿主本地播放器收口

### Demo 里是怎么处理的

当前 Demo 里，`AssistantPcmPlayer` 主要做了三件事：

1. 把 SDK 回调的 PCM 放进本地播放队列
2. 在打断场景下屏蔽旧 `responseId` 的尾包
3. 在没有新 PCM 到来后，把本地播放链路收口到空闲态

这意味着 Demo 实际上已经体现了两个层次：

- 服务端 stop：由 `onAssistantSentence(state=stop)` 体现
- 本地播放收口：由播放器自己的空闲/停止逻辑体现

### 推荐把时序拆成两层看

#### 第一层：服务端输出生命周期

```text
用户输入确认
-> AI 文本句子 start
-> AI PCM 连续下发
-> AI 回复 stop
```

#### 第二层：宿主本地播放生命周期

```text
收到 PCM
-> 本地播放器开始消费
-> 本地播放队列逐步排空
-> 本地播放链路进入收口/空闲
```

这两层通常是有时间差的。

### 苏州两日游案例

用户说：

```text
帮我规划一下苏州两日游
```

服务端已经发出：

```text
onAssistantSentence(state=stop, responseId=resp-A, reason=eos)
```

这时候只能说明：

- `resp-A` 这轮回复不再继续下发内容了

但并不能说明：

- 宿主 AudioTrack 里的最后一点语音已经播出来了

如果这时立刻切广告，就容易把尾音截掉。

更稳妥的处理方式是：

```text
收到服务端 stop
-> 继续等待本地播放器收口
-> 本地收口后再切广告
```

### `barge_in` 场景怎么理解

如果用户在 AI 说到一半时插话：

```text
预算两千以内呢？
```

常见时序是：

```text
旧回复 resp-A 正在播
-> 服务端发 stop(reason=barge_in)
-> 宿主停掉旧播放并屏蔽旧尾包
-> 新输入确认
-> 新回复 resp-B 开始
```

这种场景和正常 `eos` 最大的区别在于：

- `eos` 更偏向自然结束
- `barge_in` 更偏向立即切断旧回复，进入新回复

因此宿主在处理 `barge_in` 时，通常会比 `eos` 更激进地执行本地停播。

### 推荐的广告插播策略

#### 正常结束 `eos`

推荐策略：

```text
AI 回复 stop(reason=eos)
-> 标记服务端已结束
-> 等待本地播放收口
-> 再播广告
```

#### 打断结束 `barge_in / input_text / stopword`

推荐策略：

```text
AI 回复 stop(reason=barge_in / input_text / stopword)
-> 立即停止旧播放并屏蔽旧尾包
-> 进入新一轮输入 / 新一轮回复
```

#### 文本发送时带 `interrupt=true`

推荐策略：

```text
宿主先本地停掉当前播放
-> 再发送新的文本输入
-> 等待新的 responseId 开始
```

### 这个 Demo 能帮到什么程度

这个 Demo 的目标是给出参考实现，而不是替所有宿主封装统一播放器。

因此它能帮你看到：

- 当前 SDK 的 stop 语义
- 当前 SDK 的 PCM 下发方式
- 一个宿主侧参考播放器如何处理中断、尾包和本地收口

但它不能替你决定：

- 你的业务广告应该延迟多少毫秒切入
- 你的播放器内部什么时候才算完全排空
- 你的混音、音频焦点、音量淡入淡出策略

这些仍然属于宿主侧实现范畴。

## 推荐阅读顺序

如果你是第一次接入，建议按下面顺序看：

1. 先读本文的“输出生命周期说明”
2. 再读本文的“Assistant PCM 播放与广告插播参考”
3. `app/src/main/java/vip/xiaoweisoul/sdk/demo/MainActivity.java`
4. `app/src/main/java/vip/xiaoweisoul/sdk/demo/SettingsActivity.java`
5. `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java`
6. `app/src/main/java/vip/xiaoweisoul/sdk/demo/DebugOpenApiSessionTokenProvider.java`
7. `app/src/main/java/vip/xiaoweisoul/sdk/demo/DebugOpenApiSoulProfileClient.java`

建议重点关注下面这些文件：

- `MainActivity.java`：连接、收音、发文本、可用元神加载、事件日志、工具注册和本地 PCM 播放参考
- `SettingsActivity.java`：设置页表单、保存和 `Restore Defaults`
- `AppPrefs.java`：公开默认值与本地持久化
- `DebugOpenApiSessionTokenProvider.java`：示例工程如何获取 `session token`
- `DebugOpenApiSoulProfileClient.java`：如何根据当前 Access Key 拉取可用元神列表

## 如果你要验证记忆能力

- 不同真实用户不要共用同一个 `End User ID`，否则记忆会串。
- 同一个真实用户不要今天用 `user_10001`、明天改成 `user_10001_v2`，否则会被视为新的用户。
- 刚说过的话不一定会立刻在当前轮生效；记忆通常需要在后续对话中逐步体现。
- `Restore Defaults` 会把 `End User ID` 恢复成 Demo 默认值 `app_demo_end_user_001`，这更适合单人体验，不适合多人共用测试。

## 重要说明

### 1. 这个 Demo 只是示例，不建议直接用于生产

尤其是 `DebugOpenApiSessionTokenProvider.java`，它会直接在客户端请求 token，只适合测试或演示。

生产环境更推荐：

- App 先请求你自己的业务服务端
- 由业务服务端安全地下发短期 `session token`
- SDK 再通过 `SessionTokenProvider` 使用这个 token 建连

### 2. 这个 Demo 面向公开 SDK 接入，默认使用 Maven Central

普通接入方直接使用默认模式即可。

`-PuseLocalSdkRepo=true` 仅保留给 SDK 维护 / 联调场景，不是公开接入主路径。

### 3. 语音能力需要麦克风权限

如果你要测试 `Start Listen`，请确认设备已经授予 `RECORD_AUDIO` 权限。

### 4. GitHub Releases 中的 APK 仅用于公开测试演示

仓库 Release 页面提供的 APK 仅用于公开测试和试用演示，不代表正式发布签名体系。

当前公开测试包使用单独的 Demo 签名，仅用于方便安装和体验；请不要将其视为生产环境或正式商用发布依据。

如果后续进入正式发布或长期维护阶段，应切换到独立保管的正式发布 keystore，并按正式版本策略重新签名和分发。

## 常见问题

### 构建 Demo 失败

请检查：

- 当前网络是否正常
- Android Studio / Gradle 环境是否完整
- 是否按本文步骤在仓库根目录执行了构建命令
- 如果报的是依赖解析失败，再检查 Maven Central 是否可访问
- 只有在你显式启用了 `-PuseLocalSdkRepo=true` 时，才需要再检查 `local-sdk-repo/` 是否存在，以及是否确实包含 `vip/xiaoweisoul/sdk/session-core/1.1.1/`

### 点击 Connect 后失败

请优先检查：

- 设置页里的 `OpenAPI Base URL` 是否正确
- `WS URL` 是否正确
- `Access Key ID / Secret` 是否正确
- `Integration App ID` 是否填写为应用中心展示的字符串 `app_id`，例如 `app_g1ht6a8o`
- 如果要验证记忆能力，`End User ID` 是否已填写为当前真实用户的稳定唯一标识
- `Soul ID`、`Protocol Version` 是否正确
- 如果服务端返回的是 `invalid hello: ...` 这类握手失败，请再检查 `MainActivity.java` 里硬编码的 `DEMO_HELLO_SESSION_PROMPT` / `DEMO_HELLO_SESSION_IDLE_TIMEOUT_MS` 是否符合当前服务端约束

### 连接成功但无法开麦

请检查：

- 是否已授予 `RECORD_AUDIO`
- 是否真的已经进入 `CONNECTED` 状态
- 是否点击了 `Start Listen`

### 为什么已经收到了 `onAssistantSentence(state=stop)`，广告一播还是会截断尾音

因为这个 stop 表示的是“服务端当前回复结束”，不等于“宿主本地播放器已经播完”。

更稳妥的做法是：

1. 先用 `state=stop` 判断服务端已经结束当前回复
2. 再等待宿主本地播放器收口
3. 最后再播广告或恢复背景音

具体可以直接看上文“输出生命周期说明”和“Assistant PCM 播放与广告插播参考”。

### PCM 为什么没有像文本那样的 `null` 终止符

因为当前 SDK 对外暴露的 `PcmFrame` 是纯音频帧模型，只负责下发 PCM 数据，不额外伪造结束帧。

当前回复的结束语义在 `onAssistantSentence(state=stop)`，而不是在某个特殊 PCM 帧里。

如果你要判断更接近“本地已经播完”的时机，需要继续结合宿主自己的播放链路状态来处理。具体可以直接看上文“输出生命周期说明”和“Assistant PCM 播放与广告插播参考”。
