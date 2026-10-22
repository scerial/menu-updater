# Android Port Plan (v2)

Working plan for porting the iOS Weekly Menu app to Android. Requirements are in `port_plan.md`; the iOS behaviour reference is `../ios/HANDOFF.md`.

## Status (2026-10-22)
- **Repo move: done on Windows.** The project moved from iCloud Drive to the private repo `scerial/menu-updater`; the Windows clone is `C:\dev\menu-updater`. First commit `331b793` on `main`.
- **Still to do on the Mac:** clone to `~/Developer/menu-updater`, copy `ios/Sources/AppIcon.icon` and `design/IconComposer/icon.icon` in from iCloud (iCloud for Windows shows these bundles as 0-byte files), run `xcodegen generate` and the 30 unit tests, commit and push.
- **After the Mac check passes:** rename the iCloud folder to `Menu Updater (archived – see GitHub)`. Don't delete it unless the user asks.
- **Next:** step 1, the environment gate. Work on the `android-port` branch.

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
