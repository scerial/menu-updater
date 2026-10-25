# Android Port Plan (v2)

Working plan for porting the iOS Weekly Menu app to Android. Requirements are in `port_plan.md`; the iOS behaviour reference is `../ios/HANDOFF.md`.

## Handoff (2026-10-25)
- **Everything through the OCR quality pass is committed** (`d4a1767`) and merged into `main` via PR #1.
- **First PC set up (2026-10-25):** Android Studio 2026.2.1.8, SDK in `%LOCALAPPDATA%\Android\Sdk` (platforms 33/36/37.0 — the package is `platforms;android-37.0`, build-tools 36.0.0, platform-tools, emulator, Play x86_64 images 33/36), AVDs `phone_api36/33`, `tablet_api36/33`. `JAVA_HOME`/`ANDROID_HOME` saved as user env vars.
- **Dish-lexicon OCR correction: built and run on the Note 20 (2026-10-25), not committed.** 49 unit tests pass. Details, the RATIO 2 decision and rebuild commands are in `app/src/main/java/com/menuupdater/weeklymenu/ocr/OCR_PLAN.md`. Runtime voting across three OCR readings is also built (`TableVoter`, see OCR_PLAN.md); 61 unit tests pass; first live load 7 s on the Note 20. Step 7 verification is still open.
- **Devices:** Note 20 Ultra (`adb` serial `R3CN80FEZ1T`) is the main test phone. A friend's S25 Ultra is sometimes available for an extra check.

## Status (2026-10-24)
- **Steps 1 and 2 done** on a second Windows PC (`C:\dev\menu-updater`, `android-port` branch). Two commits: `443d040` (step 1 smoke-test baseline), `3830e5c` (step 2 OCR spike + fixtures).
- **Step 1 — environment gate: complete.** Android Studio 2026.2.1.8 + bundled JBR 25; SDK platforms 37/36/33, build-tools 36, platform-tools, emulator, Play x86_64 images (33/36); `JAVA_HOME`/`ANDROID_HOME` set. Hyper-V present → emulator works. AVDs `phone_api36/33`, `tablet_api36/33`. Compose smoke app (Gradle 9.8.2, AGP 9.4 built-in Kotlin, Kotlin 2.4.21, Compose BOM 2026.09, **compileSdk 37**/minSdk 33) builds, unit-tests, and ran on the emulator **and** the physical **Note 20 Ultra** (SM-N986N, Android 13).
- **Step 2 — OCR spike: complete.** ML Kit Korean (**bundled** `text-recognition-korean`) on-device: sub-second per image (597/335/309 ms on the Note 20), high accuracy. Block order is unreliable → parser must use geometry (as iOS). Fixtures committed under `app/src/test/resources/fixtures/` (board + 2 detail HTML, 3 `*.ocr.json`) and `app/src/androidTest/assets/menu_images/` (3 PNGs); `OcrSpikeTest` regenerates the JSON. See that folder's `README.md`.
- **Step 3 — domain + parser: complete (`31ab9dc`).** Pure-Kotlin domain in `app/src/main/java/com/menuupdater/weeklymenu/domain/`: `Geometry` (bottom-left `Rect` + `Rect.fromTopDown` to convert ML Kit's top-left boxes), `Menu` (models, `additionalSources` nullable, `WeeklyMenu.entries(LocalDate)`), `MenuTableParser` (verbatim geometry port), `MealSchedule`, and `Schedule` (`Venue`/`VenueStatus` loaded from `assets/schedule.json`, `groups(orderedBy)`). Dates use `java.time`, not `Calendar`. Semester hours now live in `assets/schedule.json` (valid 8/31–12/11) — next semester is a data edit. 18 JVM tests pass (`MenuTableParserTest` 9, `VenueStatusTest` 8, `GreetingTest` 1); parser tests run against the real ML Kit fixtures with misreads pinned as expected (삼계탕→삼계팅, 겉절이→걸절이, 콩나물→공나물, 숭늉→숭능). kotlinx.serialization added (plugin + json); `build.gradle.kts` exposes `src/main/assets` on the test classpath so `schedule.json` is the single source of truth.
  - **Parser divergence from iOS (intentional):** title match loosened from exact `주간메뉴표` to regex `주간\S*표`, because ML Kit drops a char on the 의대 image (`주간메뉴표`→`주간뉴표`) and the exact match left the cafeteria name empty.
  - **OCR watermark noise:** watermark text (`HALAL`, `MAL사`, `AGOO0-PACE`) leaked in as menu items. Now filtered out by the OCR quality pass under step 6 (uncommitted).
- **Step 4 — repository: complete (`31ab9dc`).** `app/src/main/java/com/menuupdater/weeklymenu/data/`: `BoardParser` (pure Jsoup — rows `ul.li_body`, title `a.list_text_title`, date `li.time[title]`, images `cdn.imweb.me/upload` excluding the `20a4b9b3cebc9.png` logo and `.ico`) and `MenuRepository` (OkHttp; pages 1..5 with the base64 `q`+`page` token, breaks on the first page with any match, newest-of-each by date, failure isolation so one bad post/image doesn't sink the load, throws only when neither yields a menu). OCR is injected as `typealias Recognizer = suspend (ByteArray) -> List<RecognizedLine>` so step 5 drops in ML Kit. Deps added: jsoup 1.18.3, okhttp 4.12.0 (+ mockwebserver test), coroutines 1.9.0. 7 new JVM tests (25 total): `BoardParserTest` against the real HTML fixtures; `MenuRepositoryTest` against MockWebServer for merge, one-post-fails isolation, both-fail throws, and page fallback (mock image paths embed `cdn.imweb.me/upload` so BoardParser's host filter routes them back to the mock).
- **Step 5 — OCR integration, cache, prefs: components complete (`41ee59f`).** Pure, JVM-tested (`domain/`): `MenuCacheCodec` + `CachedMenu` (versioned JSON; also decodes a bare `WeeklyMenu` as stale so old caches still load), `TextSize` (-1/0/1, unknown→보통), `RefreshPolicy` (refresh when >30 min old or the local date changed). Android wiring (compiles into the debug APK, validated by instrumented/manual later): `ocr/OcrService` (ML Kit bundled Korean → `Recognizer`; `modelState` is always READY since bundled is offline-ready), `data/MenuCache` (file IO in filesDir), `prefs/MenuPreferences` (DataStore: default cafeteria, text size, `venue-building-order`). 30 JVM tests pass (5 new). Dep added: androidx.datastore:datastore-preferences 1.1.1. The resume/launch orchestration (show cache immediately, refresh on resume, re-evaluate today) lands with the ViewModel in step 6, which now has every piece it needs.
- **Step 6 — adaptive Compose UI: complete (`f8338df`); run on the Note 20, see below.** `ui/` package: `Theme` (exact iOS hex, light/dark via `isSystemInDarkTheme`, `UI_SCALE` 0.8, menu-text tiers, `card` modifier), `MenuViewModel` (AndroidViewModel tying cache + repo(OCR) + prefs + `RefreshPolicy`; `didBecomeActive` on `onResume`), `MenuApp` (adaptive: bottom bar <1000dp / sidebar ≥1000dp, cafeteria picker <700dp / side-by-side columns ≥700dp), and the four screens (`TodayWeekScreens`, `HoursScreen`, `SettingsScreen`). Tab icons are vector drawables (`ic_tray/calendar/clock/settings`). `INTERNET` permission added; `MainActivity` now hosts `MenuApp`. `assembleDebug` succeeds; 30 unit tests still pass; debug APK 76.8 MB (bundled ML Kit model — release minify will shrink).
  - **OCR quality pass (2026-10-24, measured on the Note 20; uncommitted).** Two fixes: (1) **junk-token filter** in `MenuTableParser.cleanItem` — strips Latin-letter runs and drops lines with no Hangul, removing logo/watermark fragments (HALAL, SCH, AGOO0-PACE, MAL사) that OCR'd in as fake side dishes; (2) **image preprocessing** in `OcrService` — contrast ×1.6 then 1.5× upscale, chosen from an on-device experiment (`OcrQualityTest`, androidTest). Against 8 dishes the baseline misreads, baseline fixed 3/8, upscale-or-contrast alone 6/8, **contrast+upscale 7/8** (콩나물불고기/콩나물무침/콩나물국/삼계탕/겉절이/후라이/열무된장무침 correct; 숭늉 still unfixable, and misreads remain non-deterministic per cell). grayscale alone did nothing. The **unbundled "downloadable" model was not measured** — it shares `KoreanTextRecognizerOptions` with the bundled model (duplicate class), so comparing it needs a separate build flavor; deferred since preprocessing already gave a solid win. Both fixes verified live on the Note 20 (장각삼계탕 now correct, no watermark junk). 31 unit tests pass (added `dropsLogoAndWatermarkTokensThatOcrIntoCells`).
  - **Run and verified on the physical Note 20 Ultra (2026-10-24, dark mode).** Live load works end-to-end (board scrape → post selection → image download → ML Kit OCR → parse → render); all three cafeterias parse including 순천향의대식당 (title-fix held on real-device OCR); cache shows instantly on relaunch. Checked: Today empty-state (Sat, outside the menu week), Week (석식 rows), 운영 시간 (correct Saturday statuses/휴무/미운영 per the schedule), 설정 (default cafeteria ✓, size picker scales the preview and persists to DataStore, source link, version). Still to do in step 7: phone/tablet AVDs API 33/35+, landscape + split-screen, light mode, a true airplane-mode cache relaunch, and Compose UI tests.
  - **Simplified vs iOS (revisit in step 7):** no pull-to-refresh (retry button + resume auto-refresh instead); picker thumb slide + Today scroll-to-focus animations omitted; iPad equal-height grid approximated with `IntrinsicSize.Min` rows (equal height per row, not whole grid); system font size works (sizes are `sp` × `UI_SCALE` × the 작게/보통/크게 tier; checked at 130% on the tablet emulator 2026-10-25); tray icon is a Material "inbox" vector placeholder, not the branded app-icon artwork; Today status dots refresh on resume, only 운영 시간 has the 30s live ticker.
- **Step 7 progress (2026-10-25, uncommitted):** `tablet_api36` emulator (Pixel Tablet 2560×1600): live load, sidebar layout and 3 cafeterias side by side, light and dark, 이번 주 / 운영 시간 / 설정 / 오늘, system font size at 130% and 200% (wraps, nothing clipped), portrait (bottom bar). Note 20: first live load 7 s with voting. **Still open:** `tablet_api33` and phone AVDs on API 33, split-screen, airplane-mode relaunch from the cache, Compose UI tests, release build (minify, signing).
- **Also done this session:** launcher icon (adaptive, coral + full tray, themed monochrome layer) and the in-app tray icon, both generated from `design/IconComposer/*.svg` (`res/drawable/ic_launcher_foreground.xml`, `ic_tray.xml`, `mipmap-anydpi/`); `MenuMerge.preferFuller` so a partial refresh (one image failed) can't replace a complete cached menu (seen once on the emulator); the repository now logs swallowed image failures (`MenuRepository` tag).
- **Next:** the dish-lexicon OCR correction (`ocr/OCR_PLAN.md`), then step 7, verification — run on the phone/tablet AVDs (API 33/35+) and the Note 20, portrait/landscape/split, dark mode, largest font; live load + first-run; relaunch from cache in airplane mode. Add Compose UI tests for the main states.
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
