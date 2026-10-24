# Android Port Plan (v2)

Working plan for porting the iOS Weekly Menu app to Android. Requirements are in `port_plan.md`; the iOS behaviour reference is `../ios/HANDOFF.md`.

## Status (2026-10-24)
- **Steps 1 and 2 done** on a second Windows PC (`C:\dev\menu-updater`, `android-port` branch). Two commits: `443d040` (step 1 smoke-test baseline), `3830e5c` (step 2 OCR spike + fixtures).
- **Step 1 — environment gate: complete.** Android Studio 2026.2.1.8 + bundled JBR 25; SDK platforms 37/36/33, build-tools 36, platform-tools, emulator, Play x86_64 images (33/36); `JAVA_HOME`/`ANDROID_HOME` set. Hyper-V present → emulator works. AVDs `phone_api36/33`, `tablet_api36/33`. Compose smoke app (Gradle 9.8.2, AGP 9.4 built-in Kotlin, Kotlin 2.4.21, Compose BOM 2026.09, **compileSdk 37**/minSdk 33) builds, unit-tests, and ran on the emulator **and** the physical **Note 20 Ultra** (SM-N986N, Android 13).
- **Step 2 — OCR spike: complete.** ML Kit Korean (**bundled** `text-recognition-korean`) on-device: sub-second per image (597/335/309 ms on the Note 20), high accuracy. Block order is unreliable → parser must use geometry (as iOS). Fixtures committed under `app/src/test/resources/fixtures/` (board + 2 detail HTML, 3 `*.ocr.json`) and `app/src/androidTest/assets/menu_images/` (3 PNGs); `OcrSpikeTest` regenerates the JSON. See that folder's `README.md`.
- **Step 3 — domain + parser: complete (not yet committed).** Pure-Kotlin domain in `app/src/main/java/com/menuupdater/weeklymenu/domain/`: `Geometry` (bottom-left `Rect` + `Rect.fromTopDown` to convert ML Kit's top-left boxes), `Menu` (models, `additionalSources` nullable, `WeeklyMenu.entries(LocalDate)`), `MenuTableParser` (verbatim geometry port), `MealSchedule`, and `Schedule` (`Venue`/`VenueStatus` loaded from `assets/schedule.json`, `groups(orderedBy)`). Dates use `java.time`, not `Calendar`. Semester hours now live in `assets/schedule.json` (valid 8/31–12/11) — next semester is a data edit. 18 JVM tests pass (`MenuTableParserTest` 9, `VenueStatusTest` 8, `GreetingTest` 1); parser tests run against the real ML Kit fixtures with misreads pinned as expected (삼계탕→삼계팅, 겉절이→걸절이, 콩나물→공나물, 숭늉→숭능). kotlinx.serialization added (plugin + json); `build.gradle.kts` exposes `src/main/assets` on the test classpath so `schedule.json` is the single source of truth.
  - **Parser divergence from iOS (intentional):** title match loosened from exact `주간메뉴표` to regex `주간\S*표`, because ML Kit drops a char on the 의대 image (`주간메뉴표`→`주간뉴표`) and the exact match left the cafeteria name empty.
  - **Known OCR noise (not handled):** watermark text (`HALAL`, `MAL사`, `AGOO0-PACE`) occasionally leaks in as a menu item. Same tolerance class as iOS dish misreads; revisit if it looks bad in the UI.
- **Step 4 — repository: complete (not yet committed).** `app/src/main/java/com/menuupdater/weeklymenu/data/`: `BoardParser` (pure Jsoup — rows `ul.li_body`, title `a.list_text_title`, date `li.time[title]`, images `cdn.imweb.me/upload` excluding the `20a4b9b3cebc9.png` logo and `.ico`) and `MenuRepository` (OkHttp; pages 1..5 with the base64 `q`+`page` token, breaks on the first page with any match, newest-of-each by date, failure isolation so one bad post/image doesn't sink the load, throws only when neither yields a menu). OCR is injected as `typealias Recognizer = suspend (ByteArray) -> List<RecognizedLine>` so step 5 drops in ML Kit. Deps added: jsoup 1.18.3, okhttp 4.12.0 (+ mockwebserver test), coroutines 1.9.0. 7 new JVM tests (25 total): `BoardParserTest` against the real HTML fixtures; `MenuRepositoryTest` against MockWebServer for merge, one-post-fails isolation, both-fail throws, and page fallback (mock image paths embed `cdn.imweb.me/upload` so BoardParser's host filter routes them back to the mock).
- **Step 5 — OCR integration, cache, prefs: components complete (not yet committed).** Pure, JVM-tested (`domain/`): `MenuCacheCodec` + `CachedMenu` (versioned JSON; also decodes a bare `WeeklyMenu` as stale so old caches still load), `TextSize` (-1/0/1, unknown→보통), `RefreshPolicy` (refresh when >30 min old or the local date changed). Android wiring (compiles into the debug APK, validated by instrumented/manual later): `ocr/OcrService` (ML Kit bundled Korean → `Recognizer`; `modelState` is always READY since bundled is offline-ready), `data/MenuCache` (file IO in filesDir), `prefs/MenuPreferences` (DataStore: default cafeteria, text size, `venue-building-order`). 30 JVM tests pass (5 new). Dep added: androidx.datastore:datastore-preferences 1.1.1. The resume/launch orchestration (show cache immediately, refresh on resume, re-evaluate today) lands with the ViewModel in step 6, which now has every piece it needs.
- **Next:** step 6, adaptive Compose UI (window-size classes; 오늘 / 이번 주 / 운영 시간 / 설정; Theme colours; ties cache + repository + prefs together in a ViewModel).
- **Note:** the earlier "other PC" status below (162 GB, JDK 21) is a different machine; both PCs are used back and forth via git.
- **Build env reminder:** this shell needs `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` and `ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"` set per-invocation (not inherited); unit tests: `.\gradlew.bat :app:testDebugUnitTest`.

## Decisions made
- Android Studio, installed via winget.
- Commit identity: Youngmin Kim <jacobkim0707@gmail.com> (set globally on Windows).
- `gh` CLI at `C:\Program Files\GitHub CLI\gh.exe`, signed in as `scerial`.
- Still open: is a physical Galaxy device available?

## Environment (checked 2026-10-22)
Oracle JDK 21.0.12 on PATH, `JAVA_HOME` unset; no Android Studio, SDK, adb or Gradle; ~162 GB free on C:; winget available.

## Steps

### 1. Environment gate
- Install Android Studio via winget, plus SDK platform-tools, the API 36 platform and build-tools, the API 33 platform, the emulator, and Google Play x86_64 system images for API 33 and 35+. Set `JAVA_HOME` (or use Studio's bundled JBR) and `ANDROID_HOME`.
- Check that the hypervisor is enabled (`systeminfo`, WHPX) so emulators are usable.
- Create AVDs: a phone like the S25 Ultra (1440×3120) and a tablet like the Tab S7 (1600×2560), each on API 33 and 35+.
- Smoke test: a Compose template with the Gradle wrapper and a version catalog in `android/`. Gradle sync, `assembleDebug` and `testDebugUnitTest` must pass, and the app must launch. Commit to `android-port`.

### 2. OCR spike and fixtures (de-risk first)
- Save the current board page, a detail page and both menu images (university and 의과대학) as test fixtures.
- Run ML Kit Korean on them in an instrumented test. Compare `play-services-mlkit-text-recognition-korean` (unbundled: Play Services downloads the model, which matches "downloadable" in port_plan) with `text-recognition-korean` (bundled: works offline on first run). Record which one we pick.
- Save ML Kit's output (text plus normalized boxes) as JSON fixtures. These replace the iOS Vision fixtures for the Android parser tests.

### 3. Domain and parser (pure Kotlin, JVM tests)
- Port the models (keeping `additionalSources` optional), `MealSchedule`, venue status, `VenueDirectory.groups(orderedBy:)` and `MenuTableParser`. The parser covers date headers such as `10월20일(화)`, meal labels, nearest column/meal assignment, top-to-bottom order, the footer cutoff at the highest origin line, and the shared-row heuristic.
- Load semester hours from `assets/schedule.json` with a valid-until date, so next semester is a data edit, not a code change.
- Tests: port the iOS cases, including `testOriginRowsAboveTheLabelAreNotMenuItems` and `testSavedBuildingOrderIsApplied`, and run them against the ML Kit fixtures.

### 4. Repository
- Jsoup and OkHttp: `ul.li_body`, `a.list_text_title`, `li.time`, `cdn.imweb.me/upload` images, base64 `?q=…&page=N` paging, timeouts and HTTP status checks.
- Post selection: the newest `순천향대학교` post without `의과대학`, plus the newest `의과대학` post, both from the first page that has any match. Merge them with failure isolation: throw only if neither yields a menu.
- Tests: the HTML fixtures from step 2, plus MockWebServer for the failure paths.

### 5. OCR integration, cache and lifecycle
- An OCR service that reports the model state (ready / downloading / unavailable offline) to the UI.
- Cache the menu as versioned JSON that still decodes older versions, and show it immediately on launch. On resume, refresh if the data is over 30 minutes old or the date has changed; re-evaluate `today` on every resume.
- DataStore preferences: default cafeteria, text size (-1/0/1; unknown values become 0) and building order.

### 6. Adaptive UI
- Window-size classes. Compact: bottom navigation and a cafeteria picker. Medium/expanded: a navigation rail and cafeterias side by side, with room for three columns including 의대.
- Screens: 오늘 (cards, status pill, main dish and chips), 이번 주 (design option B), 운영 시간 (with reorder mode) and 설정, plus a source footer listing both posts.
- Colours from `ios/Sources/Theme.swift`, in light and dark; the tray icon as a vector drawable.

### 7. Verification
- Unit tests: parser, repository, persistence and schedule.
- Compose UI tests for the main states: loading, error, empty, stale and model downloading.
- Manual checks: phone and tablet AVDs on API 33 and 35+, in portrait, landscape and split-screen, at the largest font size and in dark mode. Also a live load with the first-run model download, a relaunch in airplane mode from the cache, and the first-load time on a real device if one is available.

### Parallel iOS checks (not blocking)
On the Mac in Xcode, check: the three-column iPad layout with 의대, the Week and Settings tabs, dark mode, and first-load time on a physical device. Findings feed into step 6.

## Files
- To create in `android/`: `app/`, `gradle/libs.versions.toml`, `app/src/main/assets/schedule.json` and `app/src/test/resources/fixtures/`.
- Reference only; don't change their behaviour: `ios/Sources/{MenuRepository,MenuTableParser,VenueStatus,Theme,MenuViews,SettingsView,WeeklyMenuApp}.swift` and `ios/Tests/`.
