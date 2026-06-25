# xiaowei-sdk-android-demo

本リポジトリは、Android ホストアプリ向けの SDK サンプルプロジェクトです。`vip.xiaoweisoul.sdk:session-core:1.1.4` をアプリへ組み込み、最小構成の会話フローを確認できます。

Demo のコミット済み既定値は Maven Central から SDK を取得する設定です。ローカル SDK で連携テストする場合は、コミット済みの `gradle.properties` を変更せず、コマンドラインで `-PuseLocalSdkRepo=true` を渡すか、ローカル専用の `local.properties` に `useLocalSdkRepo=true` を設定します。

詳細情報はこちらをご参照ください: http://www.xiaoweisoul.vip/docs/app-access-overview

この README では、基本的な実行方法だけでなく、次のような組み込み時によくある疑問もまとめて扱います。

- `onUserInputCommitted`、`onAssistantSentence`、`onAssistantEmotion`、`onAssistantPcm` がそれぞれ何を意味するか
- 1 回の長い応答の中で、複数の AI テキスト文と複数の PCM フレームがどう並ぶか
- `barge_in` や `interrupt=true` のような割り込みをどう理解するか
- なぜ `onAssistantSentence(state=stop)` が「ローカル再生完了」と同じではないのか
- 広告挿入や BGM 復帰のタイミングをどこで判断するべきか

## この Demo で確認できること

- Maven Central から SDK を組み込む基本フロー
- `XiaoweiSessionClient` の作成方法
- 接続パラメータ、session token、`End User ID` の設定例
- 接続、テキスト送信、録音開始、イベント受信までの基本フロー
- `onUserInputCommitted`、`onAssistantSentence`、`onAssistantEmotion`、`onAssistantPcm` という主要出力シグナルの理解
- 長い応答、複数文のテキスト、`barge_in` 割り込み、ローカル PCM 再生の収束タイミングの理解
- メイン画面の言語切り替え、利用可能な元神の読み込み / 切り替え、ログ確認の流れ
- 録音前処理ステータス、Assistant PCM 再生、ローカルツール呼び出し、AI emotion 表示の確認
- MCP ツールごとの任意 `waitingMessage` と、サーバー側デフォルト待機文言へのフォールバック動作の確認
- サーバーからの WebSocket close frame が発生した場合に、`[Session]` ログで `closeCode / closeReason` を確認する方法

SDK を組み込むこと自体が目的であれば、この Demo を直接改造する必要はありません。通常は次の順番で確認することを推奨します。

1. 先に [Android SDK クイックスタート](http://www.xiaoweisoul.vip/docs/android-sdk-quickstart) を読む
2. その後、この Demo のコード構成を参考に自分のアプリへ組み込む

## ディレクトリ構成

- `app/`: Demo Android アプリモジュール

## 実行前の準備

### 1. Demo をビルドする

```bash
./gradlew :app:assembleDebug
```

### 2. 接続パラメータを準備する

Demo の実行には、次のパラメータが必要です。

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

特に次の点に注意してください。

- `Integration App ID` には管理画面の「应用中心」に表示される `app_id` を入力します。
- 値は文字列形式です。例: `app_g1ht6a8o`
- `End User ID` には、アプリ側で同じユーザーを継続して識別できる安定した ID を入力します。例: `user_10001`
- 記憶機能を試す場合は、同じ利用者に同じ `End User ID` を継続して使い、別の利用者に使い回さないでください。
- `Soul ID` には元神設定の安定識別子を入力します。例: `soul_acme_companion_main_v1`

これらの値の正式な業務設定は、このリポジトリには含まれていません。利用時は、自分のテスト環境または業務環境の設定値を使用してください。

## 実行方法

### Android Studio

1. Android Studio を開く
2. `Open` を選択する
3. `xiaowei-sdk-android-demo` ディレクトリを開く
4. Gradle Sync の完了を待つ
5. `app` を実行する

Android Studio の Run ボタンでローカル SDK を使う場合は、Demo リポジトリ直下の `local.properties` に次を追加します。

```properties
useLocalSdkRepo=true
```

その後 Sync Project を実行してから Run します。`local.properties` はローカル専用ファイルなのでコミットしません。コミット済みの既定値は引き続き `useLocalSdkRepo=false`、つまり Maven Central 経路です。

### コマンドライン

既定では Maven Central 経路を検証します。リポジトリ直下で次を実行します。

```bash
./gradlew :app:assembleDebug
```

ローカル SDK リポジトリを明示する場合:

```bash
./gradlew -PuseLocalSdkRepo=true :app:assembleDebug
```

ローカルの `local.properties` に `useLocalSdkRepo=true` がある状態で、一時的に Maven Central 経路を検証したい場合:

```bash
./gradlew -PuseLocalSdkRepo=false :app:assembleDebug
```

## クイック体験

すぐに試せるよう、この Demo には以下の公開体験用デフォルト設定があらかじめ組み込まれています。

- 内蔵アプリ 1 つ
- 内蔵 API Key 1 組
- 既定の `End User ID` 1 つ
- 既定の `Soul ID` 1 つ

初回起動時は、この内蔵設定をそのまま利用して体験できます。以前に設定を変更している場合は、設定画面で `Restore Defaults` を押して内蔵値へ戻し、メイン画面に戻って `Connect` を押せば再度試せます。

ただし、この内蔵設定はクイック体験専用です。一般ユーザーは直接利用できますが、対応するバックエンドリソースを閲覧したり、自分で管理したりすることはできません。

自分専用のアプリ、API Key、元神情報を閲覧・作成・管理したい場合は、サポートへ連絡してアカウント登録と権限有効化を行い、自分の管理コンソールで設定してください。

これらの公開デフォルト値の主要部分は `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java` に定義されており、設定画面の各フィールドに自動反映されます。メイン画面の元神セレクタは、現在の `Access Key ID / Secret` に基づいて OpenAPI から利用可能なロールを動的に読み込みます。

設定画面でクイック体験に関係する主な項目は次の通りです。

| 項目 | 内蔵値 | 説明 |
|---|---|---|
| `OpenAPI Base URL` | `http://api.xiaoweisoul.vip` | 内蔵 OpenAPI URL |
| `WS URL` | `ws://soul.xiaoweisoul.vip` | 内蔵 WebSocket URL |
| `Access Key ID` | `ak_be60d1530176d7e4b915ed9c` | 内蔵 API Key ID |
| `Access Key Secret` | `sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7` | 内蔵 API Key Secret |
| `Integration App ID` | `app_remav935` | 内蔵アプリ ID |
| `End User ID` | `app_demo_end_user_001` | Demo の既定ユーザー ID。公開体験内でのユーザー識別に使用 |
| `Protocol Version` | `1` | プロトコルバージョン |
| `Logical Device ID` | `app.demo.device-001` | デフォルトの Logical Device ID |
| `Logical Client ID` | `sdk.demo.client-001` | デフォルトの Logical Client ID |
| `Soul ID` | `soul_demo_chinese_female_chat_assistant_v1` | 現在の既定値。ロール一覧の取得に成功した場合は、下表の例へ切り替えて試すこともできます |

公開体験環境でよく使うチャットアシスタント `soul_id` の例は次の通りです。

| 元神名 | `soul_id` |
|---|---|
| チャットアシスタント（中国語・女性） | `soul_demo_chinese_female_chat_assistant_v1` |
| チャットアシスタント（中国語・男性） | `soul_demo_chinese_male_chat_assistant_v1` |
| チャットアシスタント（日本語・女性） | `soul_demo_japanese_female_chat_assistant_v1` |
| チャットアシスタント（日本語・男性） | `soul_demo_japanese_male_chat_assistant_v1` |
| チャットアシスタント（中日女性） | `soul_demo_chinese_japanese_female_chat_assistant_v1` |

異なる元神をすぐに試したい場合は、次のいずれかが簡単です。

1. メイン画面で利用可能なロール一覧が読み込まれた後、セレクタで切り替える
2. 設定画面で `Soul ID` を手動変更して保存し、再接続する

## Demo の使い方

### メイン画面

メイン画面では次の操作ができます。

- 現在の SDK 名称とバージョンを確認する
- 表示言語を切り替える（中国語 / 日本語）
- 設定画面を開いて接続パラメータを入力する
- ドロップダウンで利用可能な元神を切り替える
- `Connect / Disconnect`
- `Start Listen / Stop Listen`
- Session Prompt エリアで、次回接続時に LLM Emotion を有効化するか切り替える
- `Send Text`
- ログをクリアし、セッション状態とログ出力を確認する
- 録音前処理ログ、MCP ツール呼び出しログ、AI emotion 表情アニメーションの反映を確認する

メイン画面のログでは、特に次のタグを見ることを推奨します。

- `[用户输入已确认]`: サーバーが現在のユーザー入力を正式に受理した
- `[AI文本句子]`: AI の文単位テキストが 1 つ到着した
- `[PCM下发]`: 現在の応答の先頭 PCM フレームが到着した
- `[AI回复结束]`: サーバー側ではこの応答が終了したが、ローカル再生完了はまだ意味しない
- `[AI回复汇总]`: Demo が `responseId` ごとに集約した全文プレビュー
- `[AI表情]`: サーバーからの LLM emotion イベント。右上の表情エリアはこのイベントだけで更新される
- `[TtsPlayer] [本地播放开始]`: ホストアプリ側のローカル再生チェーンが実際に開始した
- `[TtsPlayer] [本地播放收口]`: Demo 側のローカル再生チェーンがアイドルへ戻り始めた。広告挿入タイミングの参考に近い
- `[Session]`: `SessionStateEvent.toString()` をそのまま出力する。サーバーが WebSocket close frame を返した場合は `closeCode` と `closeReason` もここに表示される

### 設定画面

設定画面では接続パラメータと TTS 再生方針を保存し、`Restore Defaults` で公開デフォルト値へ戻せます。`Save` を押すと、次回メイン画面で `Connect` した際にその値がそのまま使用されます。

この Demo は設定をローカルの `SharedPreferences` に保存するため、繰り返しテストしやすくなっています。

設定画面の `Integration App ID` は文字列として保存・送信され、`app_xxxxxxxx` 形式をそのまま入力できます。

設定画面の `End User ID` には、特に記憶機能を試す場合、実際の利用者を継続して識別できる安定 ID を入れてください。

### Demo 内蔵 MCP ツール

現在の Demo は `MainActivity.registerMcpTools()` で次の 5 つの最小デバイス制御ツールを登録しています。表情ツールは登録しません。

- `set_media_volume(percent)`: メディア音量を指定パーセントへ設定します。JSON Schema、引数解析、引数検証、独自 `waitingMessage` の例です
- `increase_media_volume()`: メディア音量を約 10% 上げる
- `decrease_media_volume()`: メディア音量を約 10% 下げる
- `increase_screen_brightness()`: システム全体の画面輝度を約 10% 上げる
- `decrease_screen_brightness()`: システム全体の画面輝度を約 10% 下げる

補足:

- 右上の表情エリアは `onAssistantEmotion()` だけで更新され、リソースは `app/src/main/assets/emotion/` にあります
- `set_media_volume(percent)` は引数付きツールで、`percent` は `0..100` の整数です。`invoke(argumentsJson)` でも再検証し、モデルへ提示した JSON Schema だけには依存しません
- `set_media_volume(percent)` の独自待機文言は `正在为你调整音量` です。残り 4 つの引数なしツールは待機文言を設定せず、サーバー既定文言との比較に使えます
- 音量は端末の離散的な音量段階に丸められ、輝度は毎回システムの現在値を読み直したうえで、全体レンジの 10% ステップでシステム全体へ書き戻します
- 端末が自動画面輝度モードの場合、最初の輝度ツール呼び出しで手動画面輝度モードへ切り替えます
- 輝度ツールを初めて使うときは、Android の「システム設定の変更」権限を求められることがあります。未許可の場合はツールが失敗し、権限設定画面を開きます
- `waitingMessage` が未設定、`null`、または空白文字列だけの場合、サーバーはツール経路が約 `700ms` を超えても可聴テキストがまだ無いとき、既定待機文言 `请稍等一下，处理中~` へフォールバックします
- `waitingMessage` を trim した結果が `30` 文字を超えると、サーバーはその `tools/list` を不正メタデータとして扱い、WebSocket `StatusPolicyViolation` で切断します
- SDK はこの長さ超過をローカルで遮断しません。Demo では `[Session]` ログにサーバー返却の `closeCode / closeReason` がそのまま出るため、原因を追いやすくなっています

## 出力ライフサイクルの説明

この節では、現在の `session-core` においてホストアプリ側で最も混同されやすい 3 つの出力シグナルを説明します。

- ユーザー入力確認
- AI テキスト出力
- AI 音声 PCM ストリーム受信

Demo ログを読んでいるときや、広告挿入・割り込み・BGM 復帰のロジックを実装しているときは、先にこの節を読むのがおすすめです。

### まず覚えるべき 3 つの結論

1. `onUserInputCommitted(event)` は「ユーザー入力がサーバーに正式受理された」という意味であり、AI 応答ではありません。
2. `onAssistantSentence(event)` の `state=start` が AI テキスト文の出力です。
3. `onAssistantSentence(event)` の `state=stop` は「サーバー側の応答終了」を表し、「ローカル再生完了」とは同じではありません。

### 現在の公開コールバックをどう理解するか

#### `onUserInputCommitted(event)`

このコールバックは、サーバーが現在のユーザー入力を受理し、正式に確定したことを意味します。

典型例:

- ユーザーが話した内容について、ASR の最終結果が確定した
- ホストアプリが `sendText(...)` したテキストをサーバーが受理した

主な用途:

- チャット入力欄の更新
- 新しい `turnId` の開始点として扱う
- 音声入力やテキスト入力が会話へ入ったかどうかの確認

これは AI がすでに話し始めた、という意味ではありません。

#### `onAssistantSentence(event)`

これは、現在の SDK がホストアプリに公開している AI テキスト出力の主入口です。

状態は 2 種類だけです。

- `state=start`: 表示可能なテキスト文が 1 つ届いた。本文は `event.getText()`
- `state=stop`: 現在の AI 応答が終了した。終了理由は `event.getStopReason()`

つまり「文単位テキスト + 応答終了シグナル」と考えるのが分かりやすいです。

#### `onAssistantPcm(frame)`

これは AI 音声の PCM データ受信コールバックです。

1 回のコールバックはあくまで 1 フレームの音声データであり、主に次の情報を持ちます。

- `turnId`
- `responseId`
- `seq`
- `ptsUs`
- `data`

ここには次のようなものはありません。

- `finished`
- `eof`
- `isLastFrame`
- テキスト内容

そのため、ホストアプリは `PcmFrame` 単体から「この応答の音声が完全に終わった」という独立シグナルを期待すべきではありません。

### `turnId` と `responseId` の見方

おすすめの理解は次の通りです。

- `turnId`: 1 回のユーザー入力 / 会話ターン寄りの識別子
- `responseId`: 1 回の AI 応答ストリーム寄りの識別子

多くの場面で、1 回の AI 応答は次の形を取ります。

1. 1 つの `turnId`
2. 1 つの `responseId`
3. 複数回の `onAssistantSentence(state=start)`
4. 複数回の `onAssistantPcm(frame)`
5. 1 回の `onAssistantSentence(state=stop)`

複数文の AI テキストを 1 つの応答にまとめたい場合は、`responseId` 単位で集約するのが適切です。

### 蘇州二日旅の例

ここでは「帮我规划一下苏州两日游」という長い応答を例に、現在の時系列を示します。

#### 正常な長い応答

ユーザー入力:

```text
帮我规划一下苏州两日游
```

SDK 側は次のように理解できます。

```text
onUserInputCommitted(
  source=asr or text,
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

ここで重要なのは次の 2 点です。

1. AI テキストは 1 回で全文届くのではなく、複数回・文単位で届きます。
2. `state=stop` が示すのは「サーバー側の応答終了」であり、「ホストアプリのローカル再生チェーン終了」ではありません。

#### ユーザー割り込み `barge_in`

AI が話している途中でユーザーが割り込むケース:

```text
预算两千以内呢？
```

SDK 側は次のように理解できます。

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

この例では:

- `resp-A` は旧応答
- `resp-B` は新応答
- `barge_in` は旧応答が新しい入力により割り込まれたことを意味します

ホストアプリが自前のプレイヤーを持っている場合、通常はローカル側でも旧応答の残り尾音を止める追加処理が必要になります。

### ホストアプリ実装時の推奨

#### AI 応答全文を表示したい場合

`responseId` ごとに複数回の `state=start` テキストを蓄積し、対応する `state=stop` が来た時点で 1 つの応答としてまとめることを推奨します。

#### 広告挿入や BGM 復帰を行いたい場合

`onAssistantSentence(state=stop)` だけを見ないでください。

より安全なやり方は次の通りです。

1. `state=stop` でサーバー側応答終了を判断する
2. さらにホストアプリ側プレイヤーがアイドルへ戻るのを待つ
3. その後で広告再生、BGM 復帰、または次のローカル音声再生へ移る

#### 割り込みを扱いたい場合

stop は少なくとも次の 2 種類に分けて考えるのがおすすめです。

- 正常終了: 例 `reason=eos`
- 割り込み終了: 例 `barge_in`、`input_text`、`stopword`

割り込み終了では、旧応答の残留音声を早めに止める必要があることが多く、通常は `eos` より積極的にローカル停止処理を行います。

## Assistant PCM 再生と広告挿入の参考

この節では、次の高頻度の質問を扱います。

> `onAssistantSentence(state=stop)` を受け取ったのに、広告を流すと AI の尾音が切れるのはなぜか？

原因は多くの場合サーバーではなく、ホストアプリ側が「サーバー側応答終了」と「ローカル再生完了」を同じタイミングとして扱っている点にあります。

### 先に結論

#### 結論 1

`onAssistantSentence(state=stop)` が意味するのは:

```text
サーバー側の現在の応答は終了した
```

であり、次の意味ではありません。

```text
ホストアプリ側プレイヤーが完全に再生し終えた
```

#### 結論 2

`onAssistantPcm(frame)` 自体には `eof` や `isLastFrame` のようなフィールドがないため、特定の PCM フレームから「完全終了」を直接判断することはできません。

#### 結論 3

広告挿入タイミングを比較的正確に制御したいなら、少なくとも次の 2 段階に分けることを推奨します。

1. まずサーバー stop を待つ
2. その後でホストアプリ側プレイヤーがアイドルへ戻るのを待つ

### Demo ではどうしているか

現在の Demo の `AssistantPcmPlayer` は主に次の 3 点を行います。

1. SDK から来た PCM をローカル再生キューへ入れる
2. 割り込み時に旧 `responseId` の末尾フレームを遮断する
3. 新しい PCM が来なくなったら、ローカル再生チェーンをアイドルへ戻す

つまり、この Demo ではすでに次の 2 層が分かれています。

- サーバー stop: `onAssistantSentence(state=stop)` で観測
- ローカル再生のアイドル復帰: プレイヤー自身のアイドル/停止ロジックで観測

### 時系列を 2 層に分けて考える

#### 第 1 層: サーバー出力ライフサイクル

```text
ユーザー入力確認
-> AI テキスト文 start
-> AI PCM が連続配信
-> AI 応答 stop
```

#### 第 2 層: ホストアプリ側のローカル再生ライフサイクル

```text
PCM 受信
-> ローカルプレイヤーが消費を開始
-> 再生キューが徐々に排空
-> ローカル再生チェーンがアイドルへ戻る
```

この 2 層の間には通常タイムラグがあります。

### 蘇州二日旅の例

ユーザー入力:

```text
帮我规划一下苏州两日游
```

サーバーからすでに次が来たとします。

```text
onAssistantSentence(state=stop, responseId=resp-A, reason=eos)
```

この時点で分かるのは:

- `resp-A` という応答はもう新しい内容を配信しない

ということだけです。

一方、まだ分からないのは:

- ホストアプリの AudioTrack 内の最後の音声がすでに再生し終わったかどうか

ここで即座に広告へ切り替えると、尾音を切る可能性があります。

より安全な処理は次の通りです。

```text
サーバー stop を受け取る
-> ローカルプレイヤーがアイドルへ戻るのを待つ
-> その後で広告へ切り替える
```

### `barge_in` のケースをどう理解するか

AI が話している途中でユーザーが割り込んだ場合:

```text
预算两千以内呢？
```

典型的な時系列は次の通りです。

```text
旧応答 resp-A を再生中
-> サーバーが stop(reason=barge_in) を送る
-> ホストアプリが旧再生を停止し、旧末尾フレームを遮断する
-> 新しい入力が確認される
-> 新しい応答 resp-B が開始する
```

このケースと通常の `eos` の最大の違いは次の通りです。

- `eos` は自然終了寄り
- `barge_in` は旧応答を即時に切り、新しい応答へ移る寄り

そのため、ホストアプリは `barge_in` のとき `eos` より積極的にローカル停止を行うことが多いです。

### 推奨する広告挿入戦略

#### 正常終了 `eos`

推奨戦略:

```text
AI 応答 stop(reason=eos)
-> サーバー終了済みとしてマーク
-> ローカル再生がアイドルへ戻るのを待つ
-> その後で広告再生
```

#### 割り込み終了 `barge_in / input_text / stopword`

推奨戦略:

```text
AI 応答 stop(reason=barge_in / input_text / stopword)
-> 旧再生を即時停止し、旧末尾フレームを遮断
-> 新しい入力 / 新しい応答へ移行
```

#### テキスト送信時に `interrupt=true` を付ける場合

推奨戦略:

```text
ホストアプリがまず現在のローカル再生を停止
-> その後で新しいテキスト入力を送信
-> 新しい responseId の開始を待つ
```

### この Demo が助けられる範囲

この Demo の目的は、参考実装を示すことであり、すべてのホストアプリ向けに統一プレイヤーを提供することではありません。

この Demo で分かること:

- 現在の SDK における stop の意味づけ
- 現在の SDK の PCM 配信方式
- ホストアプリ側の参考プレイヤーが割り込み、末尾フレーム、ローカル再生のアイドル復帰をどう扱うか

この Demo でも決められないこと:

- あなたの業務で広告を何ミリ秒遅らせて差し込むべきか
- あなたのプレイヤー内部でいつを完全排空とみなすか
- あなたのミキシング、オーディオフォーカス、音量フェード戦略

これらは引き続きホストアプリ側実装の責務です。

## 推奨読書順

初めて組み込む場合は、次の順番で確認することを推奨します。

1. まず本 README の「出力ライフサイクルの説明」を読む
2. 次に本 README の「Assistant PCM 再生と広告挿入の参考」を読む
3. `app/src/main/java/vip/xiaoweisoul/sdk/demo/MainActivity.java`
4. `app/src/main/java/vip/xiaoweisoul/sdk/demo/SettingsActivity.java`
5. `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java`
6. `app/src/main/java/vip/xiaoweisoul/sdk/demo/DebugOpenApiSessionTokenProvider.java`
7. `app/src/main/java/vip/xiaoweisoul/sdk/demo/DebugOpenApiSoulProfileClient.java`

特に次のファイルを見ると全体像をつかみやすくなります。

- `MainActivity.java`: 接続、録音、テキスト送信、利用可能な元神の読み込み、イベントログ、ローカル PCM 再生の参考実装
- `SettingsActivity.java`: 設定画面、保存、`Restore Defaults`
- `AppPrefs.java`: 公開デフォルト値とローカル永続化
- `DebugOpenApiSessionTokenProvider.java`: サンプルでの `session token` 取得方法
- `DebugOpenApiSoulProfileClient.java`: 現在の Access Key から利用可能な元神一覧を取得する方法

## 記憶機能を試すときの注意点

- 同じ利用者には常に同じ `End User ID` を使う
- 別の利用者に同じ `End User ID` を使わせない
- 会話直後ではなく、後続の会話で徐々に記憶効果が見える場合がある
- `Restore Defaults` で既定値に戻すと `End User ID` は `app_demo_end_user_001` に戻るため、単一ユーザー向けの体験設定になる

## 重要事項

### 1. この Demo はサンプルであり、本番利用は推奨しません

特に `DebugOpenApiSessionTokenProvider.java` はクライアント側から直接 token を取得する実装であり、テストまたはデモ用途にのみ適しています。

本番環境では、次の方針を推奨します。

- クライアントに本番用の機密情報を直接持たせない
- 認証や会話関連の制御は自分の業務バックエンド側で安全に扱う
- Demo の実装は体験と疎通確認のための参考例として扱う

### 2. ローカル SDK 連携テスト

コミット済みの既定値は Maven Central を使います。ローカル連携テストのために `gradle.properties` の `useLocalSdkRepo=false` を `true` に変更してコミットしないでください。

未公開または変更直後の SDK をローカルで確認する場合は、隣接する SDK リポジトリで先に `./build_android_sdk.sh` を実行してください。その後、Demo のローカル `local.properties` に `useLocalSdkRepo=true` を設定するか、コマンドラインで `-PuseLocalSdkRepo=true` を渡します。

`local-sdk-repo/` に `vip/xiaoweisoul/sdk/session-core/1.1.4/` が無い場合は、現在の SDK バージョンが Demo へ同期されていません。隣接する SDK リポジトリで `./build_android_sdk.sh` を再実行してください。

### 3. 音声機能にはマイク権限が必要です

`Start Listen` を試す場合は、端末に `RECORD_AUDIO` 権限が付与されていることを確認してください。

### 4. GitHub Releases の APK は公開テスト用のみです

リリースページで配布している APK は、公開テストや試用デモ向けのものであり、正式な本番配布や署名体系を表すものではありません。

現在の公開テスト APK には Demo 専用の署名を使っています。生産環境や正式商用配布の基準としては扱わないでください。

正式配布または長期運用へ進む場合は、別管理の正式リリース用 keystore に切り替えたうえで、正式なバージョン戦略に沿って再署名・再配布してください。

## よくある質問

### ビルドに失敗する

次を確認してください。

- ネットワークが正常か
- Android Studio / Gradle 環境が整っているか
- リポジトリ直下でビルドコマンドを実行しているか
- デフォルトモードでは Maven Central 上に当該バージョンが存在するか
- ネットワークから Maven Central に到達できるか
- `-PuseLocalSdkRepo=true` またはローカルの `local.properties` で `useLocalSdkRepo=true` を有効にしている場合のみ、`local-sdk-repo/` が存在するか
- `vip/xiaoweisoul/sdk/session-core/1.1.4/` が実際に含まれているか

### `Connect` を押しても失敗する

まず次を優先して確認してください。

- 設定画面の `OpenAPI Base URL` が正しいか
- `WS URL` が正しいか
- `Access Key ID / Secret` が正しいか
- `Integration App ID` が管理画面に表示される文字列 `app_id` になっているか。例: `app_g1ht6a8o`
- 記憶機能を試す場合は `End User ID` が安定した利用者 ID になっているか
- `Soul ID` と `Protocol Version` が正しいか

### 接続成功後にマイクを開けない

次を確認してください。

- `RECORD_AUDIO` 権限が付与されているか
- 実際に `CONNECTED` 状態になっているか
- `Start Listen` を押しているか

### `onAssistantSentence(state=stop)` を受け取ったのに、広告再生で尾音が切れるのはなぜか

この stop は「サーバー側の現在応答終了」を意味しており、「ホストアプリ側プレイヤーの再生完了」とは一致しません。

より安全なやり方は次の通りです。

1. まず `state=stop` でサーバー応答終了を判断する
2. 次にホストアプリ側プレイヤーがアイドルへ戻るのを待つ
3. その後で広告再生や BGM 復帰を行う

詳細は上記の「出力ライフサイクルの説明」と「Assistant PCM 再生と広告挿入の参考」を参照してください。

### PCM にテキストの `null` のような終端記号がないのはなぜか

現在の SDK が公開する `PcmFrame` は純粋な音声フレームモデルであり、PCM 配信だけを担当します。特別な終了フレームは人工的に追加していません。

現在の応答終了の意味づけは `onAssistantSentence(state=stop)` にあり、特定の PCM フレームの中に置かれているわけではありません。

「ローカルですでに再生し終えた」ことに近いタイミングを判断したい場合は、ホストアプリ自身の再生チェーン状態をあわせて扱う必要があります。詳細は上記の「出力ライフサイクルの説明」と「Assistant PCM 再生と広告挿入の参考」を参照してください。
