# Recobook Copilot Instructions

## Build, test, and lint commands

This repository is a Gradle-based Kotlin Multiplatform + Compose Multiplatform project. Use the wrapper from the repo root.

| Task | Command |
| --- | --- |
| Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Android release APK | `./gradlew :androidApp:assembleRelease` |
| Desktop app | `./gradlew :desktopApp:run` |
| Desktop packages | `./gradlew :desktopApp:packageDmg` / `:packageMsi` / `:packageDeb` |
| Web dev server (JS) | `./gradlew :webApp:jsBrowserDevelopmentRun` |
| Web production build (JS) | `./gradlew :webApp:jsBrowserDistribution` |
| Web dev server (Wasm) | `./gradlew :webApp:wasmJsBrowserDevelopmentRun` |
| Web production build (Wasm) | `./gradlew :webApp:wasmJsBrowserDistribution` |
| iOS framework for Xcode | `./gradlew :sharedUI:embedAndSignAppleFrameworkForXcode` |
| JVM/shared tests | `./gradlew :sharedUI:jvmTest` |
| iOS simulator tests | `./gradlew :sharedUI:iosSimulatorArm64Test` |
| Single JVM test class | `./gradlew :sharedUI:jvmTest --tests org.ukky.recobook.data.IsbnUtilsTest` |
| Single JVM test method | `./gradlew :sharedUI:jvmTest --tests 'org.ukky.recobook.data.IsbnUtilsTest.normalizeIsbn_withHyphens_removesHyphens'` |

There is no dedicated lint/format task configured in the checked-in Gradle build scripts.

## High-level architecture

- `sharedUI` is the real application module. `sharedUI/src/commonMain/kotlin/org/ukky/recobook/App.kt` owns the Compose UI, creates the `BooksRepository`, subscribes to `repository.books`, and wires user actions to repository calls.
- `BooksRepository` is the main state boundary: UI code does not talk to storage or Google Books directly. The flow is **ISBN input/scanner -> normalize/validate -> `BooksRepository.addByIsbn()` -> `BooksApi.fetchByIsbn()` -> `KStore<BookCollection>` -> `books: Flow<List<Book>>` -> `collectAsState()` in `App()`**.
- Platform app modules are intentionally thin hosts:
  - `androidApp` only provides the Android `Activity` and initializes `AndroidContextHolder`.
  - `desktopApp` and `webApp` just launch `App()`.
  - `iosApp` is an Xcode wrapper around the `SharedUI` framework built from `sharedUI`.
- Platform-specific behavior is isolated behind `expect`/`actual` seams in `sharedUI`:
  - `createBookStore()` selects per-platform persistence.
  - `rememberIsbnScanner()` exposes barcode scanning only where supported.
- Persistence is local-only and platform-native via KStore, not a database:
  - Android: `filesDir/recobook_books.json`
  - iOS: `Documents/recobook/books.json`
  - Desktop: `~/.recobook/books.json`
  - Web: `localStorage["recobook_books"]`

## Key conventions

- Prefer Japanese in user-facing responses for this repository. Existing repository guidance in `.aiassistant/rules/RecoBookCoding.md` expects Japanese responses, and much of the project documentation is Japanese.
- Keep feature work in `sharedUI` unless it is truly platform-host code. Most behavior changes belong in common code plus `expect`/`actual` implementations, not in `androidApp`, `desktopApp`, or `webApp`.
- Treat the stored book order as the source of truth for shelf order. New books are prepended, duplicate detection checks `id`, `isbn13`, `isbn10`, then `isbn`, and updates preserve the original `addedAt`.
- Reordering is intentionally two-phase: `BookList` mutates a local `SnapshotStateList` for immediate drag feedback, then persists the final move through `BooksRepository.reorderBooks()` on drag end. If a drag is cancelled, restore the pre-drag snapshot instead of partially persisting.
- The scan button must stay gated by `IsbnScanner.isAvailable`. Only Android actually launches ZXing; iOS/Desktop/Web currently return a disabled/no-op scanner implementation.
- Shared strings and fonts live in `sharedUI/src/commonMain/composeResources`. Reuse `stringResource(...)` and `Res.font...` instead of adding platform-local copies.
- Follow twada-style TDD when implementing behavior changes: drive changes from a failing test, make the smallest change to pass, and refactor only after the test suite is green.
- After implementing a change, always review repository documentation such as `docs/BASIC_DESIGN.md` and `README.MD`, and update any affected docs in the same change instead of leaving them stale.
- Most tests belong in `sharedUI`:
  - `commonTest` for pure common logic such as ISBN normalization and API response mapping.
  - `jvmTest` for repository behavior, drag/drop state, and JVM file-backed storage.
  - `iosTest` for iOS storage-path behavior.
