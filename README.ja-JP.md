# xiaowei-sdk-android-demo

本リポジトリは、Android アプリへの SDK 組み込みを確認するためのサンプルプロジェクトです。アプリに基本接続を組み込み、最小構成で会話セッションを体験する流れを確認できます。

詳細情報はこちらをご参照ください: http://www.xiaoweisoul.vip/docs/app-access-overview

## この Demo で確認できること

- Android アプリの基本接続ができているかの確認
- 設定画面での接続パラメータ準備
- 接続、テキスト送信、録音開始、イベント受信までの基本フロー
- メイン画面の言語切り替え、デフォルト元神切り替え、ログ確認の流れ
- 記憶機能を試す際の `End User ID` 設定例

SDK を組み込むこと自体が目的であれば、この Demo を直接改造する必要はありません。通常は次の順番で確認することを推奨します。

1. 先に [Android SDK クイックスタート](http://www.xiaoweisoul.vip/docs/android-sdk-quickstart) を読む
2. その後、この Demo の画面構成と設定項目を参考に自分のアプリへ組み込む

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
- `Soul ID` には元神設定の安定識別子を入力します。例: `soul_acme_companion_main_v1`

これらの値の正式な業務設定は、このリポジトリには含まれていません。利用時は、自分のテスト環境または業務環境の設定値を使用してください。

## 実行方法

### Android Studio

1. Android Studio を開く
2. `Open` を選択する
3. `xiaowei-sdk-android-demo` ディレクトリを開く
4. Gradle Sync の完了を待つ
5. `app` を実行する

### コマンドライン

リポジトリ直下で次を実行します。

```bash
./gradlew :app:assembleDebug
```

## クイック体験

すぐに試せるよう、この Demo には以下のデフォルト設定があらかじめ組み込まれています。

- 内蔵アプリ 1 つ
- 内蔵 API Key 1 組
- 複数の内蔵チャットアシスタント元神

初回起動時は、この内蔵設定をそのまま利用して体験できます。以前に設定を変更している場合は、設定画面で `Restore Defaults` を押して内蔵値へ戻し、メイン画面に戻って `Connect` を押せば再度試せます。

ただし、この内蔵設定はクイック体験専用です。一般ユーザーは直接利用できますが、対応するバックエンドリソースを閲覧したり、自分で管理したりすることはできません。

自分専用のアプリ、API Key、元神情報を閲覧・作成・管理したい場合は、サポートへ連絡してアカウント登録と権限開通を行い、自分の管理コンソールで設定してください。

設定画面でクイック体験に関係する主な項目は次の通りです。

| 項目 | 内蔵値 | 説明 |
|---|---|---|
| `OpenAPI Base URL` | `http://api.xiaoweisoul.vip` | 内蔵 OpenAPI URL |
| `WS URL` | `ws://soul.xiaoweisoul.vip` | 内蔵 WebSocket URL |
| `Access Key ID` | `ak_be60d1530176d7e4b915ed9c` | 内蔵 API Key ID |
| `Access Key Secret` | `sk_672ed90e07f12f657ad913c23f5216bafbe8f74febb19ea7` | 内蔵 API Key Secret |
| `Integration App ID` | `app_remav935` | 内蔵アプリ ID |
| `End User ID` | `sdk-demo-ghtao-01` | Demo の既定ユーザー ID。記憶体験の切り分けに使用 |
| `Protocol Version` | `1` | プロトコルバージョン |
| `Logical Device ID` | `app.demo.device-001` | デフォルトの Logical Device ID |
| `Logical Client ID` | `sdk.demo.client-001` | デフォルトの Logical Client ID |
| `Soul ID` | `soul_demo_chinese_female_chat_assistant_v1` | 現在のデフォルト元神。下表のいずれかに変更して試すこともできます |

内蔵のチャットアシスタント元神 `soul_id` は次の通りです。

| 元神名 | `soul_id` |
|---|---|
| チャットアシスタント（中国語・女性） | `soul_demo_chinese_female_chat_assistant_v1` |
| チャットアシスタント（中国語・男性） | `soul_demo_chinese_male_chat_assistant_v1` |
| チャットアシスタント（日本語・女性） | `soul_demo_japanese_female_chat_assistant_v1` |
| チャットアシスタント（日本語・男性） | `soul_demo_japanese_male_chat_assistant_v1` |
| チャットアシスタント（中日女性） | `soul_demo_chinese_japanese_female_chat_assistant_v1` |

異なる元神をすぐに試したい場合は、設定画面で `Soul ID` だけを上記のいずれかに変更し、保存後に再接続する方法が最も簡単です。

## Demo の使い方

### メイン画面

メイン画面では次の操作ができます。

- 現在の SDK 名称とバージョンを確認する
- 表示言語を切り替える（中国語 / 日本語）
- 設定画面を開いて接続パラメータを入力する
- 内蔵セレクタでデフォルト元神をすばやく切り替える
- `Connect / Disconnect`
- `Start Listen / Stop Listen`
- `Send Text`
- ログをクリアし、セッション状態とログ出力を確認する

### 設定画面

設定画面では接続パラメータと TTS 再生方針を保存し、`Restore Defaults` で公開デフォルト値へ戻せます。`Save` を押すと、次回メイン画面で `Connect` した際にその値がそのまま使用されます。

この Demo は設定をローカルの `SharedPreferences` に保存するため、繰り返しテストしやすくなっています。

設定画面の `Integration App ID` は文字列として保存・送信され、`app_xxxxxxxx` 形式をそのまま入力できます。

設定画面の `End User ID` は、記憶機能を試すときに同じ利用者を継続して識別するために使用します。別の利用者が同じ値を使うと、体験が混ざる可能性があります。

## 記憶機能を試すには

記憶機能を試したい場合は、現在の聯調環境で対象元神の記憶機能が有効になっていることを確認し、設定画面で `End User ID` に安定した利用者 ID を入力してください。

使用時のポイント：

- 同じ利用者には常に同じ `End User ID` を使う
- 別の利用者に同じ `End User ID` を使わせない
- 会話直後ではなく、後続の会話で徐々に記憶効果が見える場合がある
- `Restore Defaults` で既定値に戻すと、単一ユーザー向けの体験設定に戻る

## 重要事項

### 1. この Demo はサンプルであり、本番利用は推奨しません

本番環境では、次の方針を推奨します。

- クライアントに本番用の機密情報を直接持たせない
- 認証や会話関連の制御は自分の業務バックエンド側で安全に扱う
- Demo の実装は体験と疎通確認のための参考例として扱う

### 2. 音声機能にはマイク権限が必要です

`Start Listen` を試す場合は、端末に `RECORD_AUDIO` 権限が付与されていることを確認してください。

## よくある質問

### ビルドに失敗する

次を確認してください。

- ネットワークが正常か
- Android Studio / Gradle 環境が整っているか
- リポジトリ直下でビルドコマンドを実行しているか

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
