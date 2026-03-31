# Recobook

ISBN を入力またはバーコードスキャンで書籍を検索・登録できる読書記録アプリです。  
Kotlin Multiplatform + Compose Multiplatform で Android / iOS / Desktop / Web に対応しています。

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
# JVM テスト（共通ロジック・API・Repository）
./gradlew :sharedUI:jvmTest

# iOS テスト（iOS シミュレーターが必要）
./gradlew :sharedUI:iosSimulatorArm64Test
```

---

## 主な使用ライブラリ

| ライブラリ | 用途 |
|---|---|
| [Ktor](https://ktor.io/) 3.3.3 | HTTP クライアント（Google Books API） |
| [KStore](https://github.com/xxfast/KStore) 1.0.0 | ローカルデータ永続化 |
| [Coil](https://coil-kt.github.io/coil/) 3.3.0 | 書影の非同期読み込み |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 1.9.0 | JSON シリアライズ |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) 1.10.2 | 非同期処理 |
| [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) 4.3.0 | バーコードスキャン（Android） |
| [Metro](https://github.com/ZacSweers/metro) 0.9.4 | 依存性注入 |
