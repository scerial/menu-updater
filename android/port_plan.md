# Android Port Handoff: Weekly Menu App

## Goal
Port the existing iOS weekly menu app to native Android phones and tablets with feature parity. Fetch posts and images from https://www.agoodplacefnb.com/today and run Korean OCR on-device. Keep the Android project isolated from the iOS app; do not add a Windows backend or Tailscale dependency.

## Status (2026-10-22)
- This folder is currently empty; no Android project exists here.
- Android SDK, Android Studio, JDK, Gradle, emulator, and connected-device availability have not been verified.
- No Android setup or app implementation has been performed.
- The first work item is environment readiness and setup, before port implementation.

## Environment Setup Gate (Do First)
1. Inspect the Windows environment for a supported JDK, Android Studio, Android SDK, Gradle (or an existing wrapper), required environment variables (`JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT`), available disk space, and attached Android devices.
2. Check Android SDK Manager components: platform tools, Android 13 / API 33 platform and build tools, a current platform for compile/target SDK, and Android Emulator/system images. Android 13 is the minimum supported OS; newer Android 14+ devices should remain compatible.
3. Report what is installed and identify missing components. Get the user's approval before installing or changing machine-level tooling/configuration.
4. Create or use a minimal Kotlin/Jetpack Compose sample to verify Gradle sync, debug build, unit-test task, and launch on an emulator or device. Resolve environment/toolchain blockers before starting feature work.

## Supported Devices and Layouts
- Phones: Galaxy Note 20 Ultra and Galaxy S25 Ultra.
- Tablets: Galaxy Tab S7 and newer.
- Use adaptive window-size classes, not model-specific fixed dimensions. Compact layouts use bottom navigation and a cafeteria picker; expanded layouts use a navigation rail/sidebar and side-by-side cafeteria columns. Verify portrait, landscape, and split-screen/multi-window.
- Support Android 13 (API 33) minimum and Android 14+.

## Port Scope
- Native Kotlin + Jetpack Compose Material 3 application.
- Full current iOS feature parity: Today, Week, Operating Hours, Settings, source links, refresh, loading/error/empty/stale states, cache fallback, and cafeteria/text-size preferences.
- On-device image fetching and Korean OCR using ML Kit Text Recognition with a downloadable model. Show model readiness/download state and handle first-run/offline cases.
- Main post titles must include `순천향대학교` and exclude `의과대학`. Find the newest `의과대학` post separately and merge it as another source/cafeteria.
- Preserve per-post/per-image failure isolation, last-good cache behavior, refresh when data is older than 30 minutes or the date changes, and source attribution.
- Preserve OCR geometry as normalized bounding boxes and port the existing table parsing behavior: date columns, meal labels, nearest column/meal assignment, top-to-bottom item order, origin/footer cutoff, and shared-row heuristic.
- Port venue hours/status and editable building ordering. Current iOS venue schedule is hard-coded through 2026-12-11 and requires a subsequent semester update.

## Suggested Implementation Sequence (After Environment Gate)
1. Establish the standalone Gradle project and Kotlin/Compose baseline.
2. Port domain models and pure date/status/parser logic; add focused unit tests.
3. Implement the Imweb board/detail repository with HTML fixtures, post selection, image extraction, source merge, and independent failure handling.
4. Integrate ML Kit Korean downloadable OCR, normalized geometry, cache, refresh lifecycle, and preferences.
5. Build adaptive Compose Today, Week, Operating Hours, and Settings screens.
6. Add repository and Compose UI tests, then verify on API 33+ phone configurations and Tab S7+ tablet configurations.

## Verification Checklist
- Environment smoke test: Gradle sync, debug build, unit-test task, and launch on an emulator/device.
- Repository fixtures: title inclusion/exclusion, newest matching main and medical posts, pagination/date parsing, image extraction, partial failures, and merged sources.
- Parser tests: Korean date headers, meal classification, geometry/order, shared rows, and origin/footer exclusion.
- Persistence tests: cache immediately available, stale refresh, date rollover, offline fallback, preference defaults/migration, and saved venue order.
- UI matrix: Note 20 Ultra/S25 Ultra phone-sized configurations; Galaxy Tab S7+ tablet size; portrait/landscape/split-screen; large font; dark mode.
- OCR lifecycle: initial model download, model unavailable/offline state, successful recognition after installation, and partial image/OCR failures.

## Reference
The current iOS behavior, site selectors, parser details, known risks, and test status are documented in `../ios/HANDOFF.md`. Treat that file as the behavior reference; the Android app should not alter the iOS project.