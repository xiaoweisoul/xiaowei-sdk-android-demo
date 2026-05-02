# xiaowei-sdk-android-demo

本リポジトリは、Android アプリへの SDK 組み込みを確認するためのサンプルプロジェクトです。`vip.xiaoweisoul.sdk:session-core:1.0.8` をアプリに統合し、最小構成で会話セッションを接続する流れを確認できます。

デフォルトでは、この Demo は `mavenCentral()` から SDK を解決します。ローカル SDK を明示的に使いたい場合だけ、ビルド時に `-PuseLocalSdkRepo=true` を指定してください。

詳細情報はこちらをご参照ください: http://www.xiaoweisoul.vip/docs/app-access-overview

## この Demo で確認できること

- Maven Central とローカル Maven リポジトリの両方で SDK を正しく組み込めているかの確認
- `XiaoweiSessionClient` の生成方法
- 接続パラメータと session token 取得ロジックの設定例
- 接続、テキスト送信、録音開始、イベント受信までの基本フロー
- メイン画面の言語切り替え、4 つの内蔵元神切り替え、ログ確認の流れ
- 録音前処理ステータスの起動前プレビュー/録音中チェック、Assistant PCM 再生の確認
- ローカルツール登録、表情アニメーション表示、ツール呼び出しイベントの確認

SDK を組み込むこと自体が目的であれば、この Demo を直接改造する必要はありません。通常は次の順番で確認することを推奨します。

1. 先に [Android SDK クイックスタート](http://www.xiaoweisoul.vip/docs/android-sdk-quickstart) を読む
2. その後、この Demo のコード構成を参考に自分のアプリへ組み込む

## ディレクトリ構成

- `app/`: Demo Android アプリモジュール
- `local-sdk-repo/`: ローカル Maven リポジトリ。ローカル SDK モード時のみ使用

## 実行前の準備

### 1. デフォルトでは Maven Central を使う

公開済み SDK を使う場合は、そのまま次を実行します。

```bash
./gradlew :app:assembleDebug
```

### 2. ローカル Maven リポジトリへ切り替える

SDK リポジトリ側で `./build_android_sdk.sh` を実行し、その後このリポジトリ直下に `local-sdk-repo/` が存在することを確認してください。

```text
xiaowei-sdk-android-demo/
  local-sdk-repo/
    vip/
      xiaoweisoul/
        sdk/
          session-core/
            1.0.8/
```

その上で、次のようにローカル SDK モードを明示指定します。

```bash
./gradlew -PuseLocalSdkRepo=true :app:assembleDebug
```

ディレクトリ構成が異なる場合、Gradle ビルド時にそのままエラーになります。

### 2. 接続パラメータを準備する

Demo の実行には、次のパラメータが必要です。

- `OpenAPI Base URL`
- `Access Key ID`
- `Access Key Secret`
- `Integration App ID`
- `Soul ID`
- `WS URL`
- `Protocol Version`
- `Logical Device ID`
- `Logical Client ID`

特に次の点に注意してください。

- `Integration App ID` には管理画面の「应用中心」に表示される `app_id` を入力します。
- 値は文字列形式です。例: `app_g1ht6a8o`
- `Soul ID` には元神設定の安定識別子を入力します。例: `soul_acme_companion_main_v1`

これらの値の実運用向けデフォルト設定は、このリポジトリには含まれていません。利用時は、自分のテスト環境または業務環境の設定値を使用してください。

## 実行方法

### Android Studio

1. Android Studio を開く
2. `Open` を選択する
3. `xiaowei-sdk-android-demo` ディレクトリを開く
4. Gradle Sync の完了を待つ
5. `app` を実行する

### コマンドライン

リポジトリ直下で次を実行します。

デフォルトのローカル SDK モードでは次を実行します。

```bash
./gradlew :app:assembleDebug
```

## クイック体験

すぐに試せるよう、この Demo には以下のデフォルト設定があらかじめ組み込まれています。

- 内蔵アプリ 1 つ
- 内蔵 API Key 1 組
- 内蔵元神 4 つ

初回起動時は、この内蔵設定をそのまま利用して体験できます。以前に設定を変更している場合は、設定画面で `Restore Defaults` を押して内蔵値へ戻し、メイン画面に戻って `Connect` を押せば再度試せます。

ただし、この内蔵設定はクイック体験専用です。一般ユーザーは直接利用できますが、対応するバックエンドリソースを閲覧したり、自分で管理したりすることはできません。

自分専用のアプリ、API Key、元神情報を閲覧・作成・管理したい場合は、サポートへ連絡してアカウント登録と権限開通を行い、自分の管理コンソールで設定してください。

これらのデフォルト値は `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java` に定義されており、設定画面の各フィールドに自動反映されます。

設定画面でクイック体験に関係する主な項目は次の通りです。

| 項目 | 内蔵値 | 説明 |
|---|---|---|
| `OpenAPI Base URL` | `http://api.xiaoweisoul.vip` | 内蔵 OpenAPI URL |
| `WS URL` | `ws://soul.xiaoweisoul.vip` | 内蔵 WebSocket URL |
| `Access Key ID` | `ak_be60d1530176d7e4b915ed9c` | 内蔵 API Key ID |
| `Access Key Secret` | `sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7` | 内蔵 API Key Secret |
| `Integration App ID` | `app_remav935` | 内蔵アプリ ID |
| `Protocol Version` | `1` | プロトコルバージョン |
| `Logical Device ID` | `app.demo.device-001` | デフォルトの Logical Device ID |
| `Logical Client ID` | `sdk.demo.client-001` | デフォルトの Logical Client ID |
| `Soul ID` | `soul_demo_chinese_female_chat_assistant_v1` | 現在のデフォルト元神。下表のいずれかに変更して試すこともできます |

内蔵の 4 つのチャットアシスタント元神 `soul_id` は次の通りです。

| 元神名 | `soul_id` |
|---|---|
| チャットアシスタント（中国語・女性） | `soul_demo_chinese_female_chat_assistant_v1` |
| チャットアシスタント（中国語・男性） | `soul_demo_chinese_male_chat_assistant_v1` |
| チャットアシスタント（日本語・女性） | `soul_demo_japanese_female_chat_assistant_v1` |
| チャットアシスタント（日本語・男性） | `soul_demo_japanese_male_chat_assistant_v1` |

異なる元神をすぐに試したい場合は、設定画面で `Soul ID` だけを上記のいずれかに変更し、保存後に再接続する方法が最も簡単です。

## Demo の使い方

### メイン画面

メイン画面では次の操作ができます。

- 現在の SDK 名称とバージョンを確認する
- 表示言語を切り替える（中国語 / 日本語）
- 設定画面を開いて接続パラメータを入力する
- 内蔵セレクタで 4 つのデフォルト元神をすばやく切り替える
- `Connect / Disconnect`
- `Start Listen / Stop Listen`
- `Send Text`
- ログをクリアし、セッション状態とログ出力を確認する
- 録音前処理ログ、MCP ツール呼び出しログ、表情アニメーションの反映を確認する

### 設定画面

設定画面では接続パラメータと TTS 再生方針を保存し、`Restore Defaults` で公開デフォルト値へ戻せます。`Save` を押すと、次回メイン画面で `Connect` した際にその値がそのまま使用されます。

この Demo は設定をローカルの `SharedPreferences` に保存するため、繰り返しテストしやすくなっています。

設定画面の `Integration App ID` は文字列として保存・送信され、`app_xxxxxxxx` 形式をそのまま入力できます。

## 推奨読書順

初めて組み込む場合は、次の順番でコードを見ることを推奨します。

1. `app/src/main/java/vip/xiaoweisoul/sdk/demo/MainActivity.java`
2. `app/src/main/java/vip/xiaoweisoul/sdk/demo/AppPrefs.java`
3. `app/src/main/java/vip/xiaoweisoul/sdk/demo/DebugOpenApiSessionTokenProvider.java`

それぞれの役割は次の通りです。

- `MainActivity.java`: 接続、録音、テキスト送信、言語切り替え、元神切り替え、イベント監視、ローカルツール登録、ログ確認の基本フロー
- `AppPrefs.java`: 接続パラメータ管理
- `DebugOpenApiSessionTokenProvider.java`: サンプル内での `session token` 取得例

## 重要事項

### 1. この Demo はサンプルであり、本番利用は推奨しません

特に `DebugOpenApiSessionTokenProvider.java` はクライアント側から直接 token を取得する実装であり、テストまたはデモ用途にのみ適しています。

本番環境では、次の構成を推奨します。

- App はまず自分の業務サーバーへリクエストする
- 業務サーバーが安全に短期 `session token` を払い出す
- SDK は `SessionTokenProvider` を通じてその token を使って接続する

### 2. この Demo はデフォルトで Maven Central を使います

現在の SDK コードをローカルで先に検証したい場合だけ `-PuseLocalSdkRepo=true` を指定してください。

### 3. 音声機能にはマイク権限が必要です

`Start Listen` を試す場合は、端末に `RECORD_AUDIO` 権限が付与されていることを確認してください。

## よくある質問

### ビルド時に `vip.xiaoweisoul.sdk:session-core:1.0.8` が見つからない

次を確認してください。

- デフォルトモードでは Maven Central 上に当該バージョンが存在するか
- ネットワークから Maven Central に到達できるか
- `-PuseLocalSdkRepo=true` を付けている場合は `local-sdk-repo/` が存在するか
- `vip/xiaoweisoul/sdk/session-core/1.0.8/` が実際に含まれているか

### `Connect` を押しても失敗する

まず次を優先して確認してください。

- 設定画面の `OpenAPI Base URL` が正しいか
- `WS URL` が正しいか
- `Access Key ID / Secret` が正しいか
- `Integration App ID` が管理画面に表示される文字列 `app_id` になっているか。例: `app_g1ht6a8o`
- `Soul ID` と `Protocol Version` がサーバー要件に一致しているか

### 接続成功後にマイクを開けない

次を確認してください。

- `RECORD_AUDIO` 権限が付与されているか
- 実際に `CONNECTED` 状態になっているか
- `Start Listen` を押しているか
