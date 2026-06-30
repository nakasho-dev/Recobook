# Recobook

ISBN を入力またはバーコードスキャンで書籍を検索・登録できる読書記録アプリです。
登録した書籍はカードをタップして詳細を開き、貸出状態や貸出先も管理できます。さらに管理画面からバックアップのエクスポート / インポートや、共有向け一覧の書き出しも行えます。
Kotlin Multiplatform + Compose Multiplatform で Android / iOS / Desktop / Web に対応しています。

## 主な機能

- **ISBN 入力 / バーコードスキャン**（Android）で書籍を検索・登録
- **OpenBD API** から書影・著者・出版社などのメタデータを自動取得
- **カードタップで詳細表示** — 書影・説明・ページ数・カテゴリなどの書誌情報を確認可能
- **貸出管理** — 書籍ごとに貸出先名を自由入力で登録し、返却時は未貸出へ戻せる
- **貸出先履歴候補** — 入力済みの名前を保存し、名前順で候補表示
- **管理画面** — 書籍数・貸出中冊数・貸出先履歴数を確認し、各種データ入出力を実行
- **フルバックアップのエクスポート / インポート** — 書籍データと貸出先履歴を JSONL で保存・復元。大量データでも扱いやすい行指向フォーマットを採用
- **書籍 JSONL エクスポート** — すべての書籍データを 1 行 1 書籍の JSONL で出力し、貸出状態も保持
- **共有向け Markdown / CSV エクスポート** — 他人に見せる本棚一覧を出力。貸出中かどうかは表示しつつ、貸出先名は除外
- **デバッグ環境のみ HTTP 通信ログを出力** — Android は Logcat、iOS は NSLog、Desktop は標準出力、Web はブラウザコンソールに出力。リリース相当環境では無効
- **長押しドラッグで並べ替え** — ホーム画面で書籍カードの左端ハンドルを長押しして上下にドラッグ。長押し確定時にハプティックフィードバックを発生
- **ライト / ダークテーマ**を手動切替
- **ローカル保存**（JSON / localStorage）— 本棚データと貸出先履歴を端末内のみ保存、ネットワーク同期なし

## 管理画面とデータ入出力

- 一覧ヘッダーの **Manage** から管理画面を開けます。
- **Backup export** は `meta` / `borrower` / `book` レコードで構成した JSONL を出力します。
- **Backup import** は現在の本棚データと貸出先履歴を **全置換** で復元します。フルバックアップ JSONL に加えて、書籍のみの JSONL 取り込みにも対応します。
- **Markdown / CSV export** は共有用の一覧出力です。`Available` / `Lent out` の状態は含みますが、貸出先名は含みません。

## 動作環境

| ツール | バージョン |
|---|---|
| JDK | 17 以上 |
| Kotlin | 2.3.0 |
| Compose Multiplatform | 1.10.0 |
| Android Gradle Plugin | 8.12.3 |
| Xcode | 14.2 以上（iOS ビルド時） |

---

## モジュール構成

```
Recobook/
├── sharedUI/      # 共有 UI・ビジネスロジック（Compose Multiplatform）
├── androidApp/    # Android アプリ
├── desktopApp/    # Desktop（JVM）アプリ
└── webApp/        # Web アプリ（JS / WasmJS）
```

---

## Android

**デバッグ APK をビルド**
```bash
./gradlew :androidApp:assembleDebug
# 出力先: androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

**リリースビルド（署名付き）**  
以下の環境変数を設定してからビルドしてください。

```bash
export RECOBOOK_ANDROID_JKS_PATH=/path/to/keystore.jks
export RECOBOOK_ANDROID_JKS_PASSWORD=<keystore_password>
export RECOBOOK_ANDROID_JKS_ALIAS=<key_alias>
export RECOBOOK_ANDROID_JKS_KEY_PASSWORD=<key_password>

./gradlew :androidApp:assembleRelease
```

**Android Studio で実行**  
プロジェクトを Android Studio で開き、`androidApp` 実行構成を使用してください。

> Min SDK: 23 / Target SDK: 36

---

## iOS

**Xcode で実行**
```
iosApp/iosApp.xcodeproj
```
上記を Xcode で開き、標準の実行構成でビルド・実行してください。

> iOS 16.2 以上が必要です。

**Gradle から iOS フレームワークをビルド**
```bash
./gradlew :sharedUI:embedAndSignAppleFrameworkForXcode
```

---

## Desktop

**開発用に起動**
```bash
./gradlew :desktopApp:run
```

**ネイティブパッケージを作成**
```bash
# macOS (.dmg)
./gradlew :desktopApp:packageDmg

# Windows (.msi)
./gradlew :desktopApp:packageMsi

# Linux (.deb)
./gradlew :desktopApp:packageDeb
```

---

## Web

### JS（Kotlin/JS）

```bash
# 開発サーバーを起動
./gradlew :webApp:jsBrowserDevelopmentRun

# プロダクションビルド
./gradlew :webApp:jsBrowserDistribution
# 出力先: webApp/build/dist/js/productionExecutable/
```

### WasmJS（Kotlin/Wasm）

```bash
# 開発サーバーを起動
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# プロダクションビルド
./gradlew :webApp:wasmJsBrowserDistribution
# 出力先: webApp/build/dist/wasmJs/productionExecutable/
```

出力ディレクトリをそのまま Web サーバーにデプロイしてください。

---

## テスト

```bash
# JVM テスト（共通ロジック・API・Repository・並べ替え・貸出管理・入出力）
./gradlew :sharedUI:jvmTest

# iOS テスト（iOS シミュレーターが必要）
./gradlew :sharedUI:iosSimulatorArm64Test
```

### テスト構成

| テストクラス | 対象 | ソース |
|---|---|---|
| `IsbnUtilsTest` | ISBN 正規化・バリデーション | `commonTest` |
| `BooksApiTest` | OpenBD API レスポンス解析 | `commonTest` |
| `BooksRepositoryTest` | 書籍追加・削除・**並べ替え**・**貸出状態更新**・**貸出先履歴保持** | `jvmTest` |
| `BooksRepositoryTransferTest` | **バックアップ JSONL 往復**・**書籍 JSONL 取り込み**・**Markdown / CSV 出力の貸出先非表示** | `jvmTest` |
| `DragDropStateTest` | ドラッグ状態管理（初期値・onDragEnd・onDragCancel など）| `jvmTest` |
| `BookStoreProviderJvmTest` | Desktop ストレージパス | `jvmTest` |
| `BookStoreProviderIosTest` | iOS ストレージパス | `iosTest` |

> **ハプティックフィードバックについて**  
> ハプティック発火（`LocalHapticFeedback.performHapticFeedback`）は UI レイヤーの副作用のため、実機 / シミュレーター上で手動確認する。

---

## 主な使用ライブラリ

| ライブラリ | 用途 |
|---|---|
| [Ktor](https://ktor.io/) 3.3.3 | HTTP クライアント（OpenBD API） |
| [KStore](https://github.com/xxfast/KStore) 1.0.0 | ローカルデータ永続化 |
| [Coil](https://coil-kt.github.io/coil/) 3.3.0 | 書影の非同期読み込み |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 1.9.0 | JSON シリアライズ |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) 1.10.2 | 非同期処理 |
| [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) 4.3.0 | バーコードスキャン（Android） |
| [Metro](https://github.com/ZacSweers/metro) 0.9.4 | 依存性注入 |
