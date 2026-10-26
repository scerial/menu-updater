# Menu Updater — Claude context

Development now happens in this repo (Mac clone `~/Developer/menu-updater`, Windows clone `C:\dev\menu-updater`). The old iCloud Drive folder `Menu Updater/` is an archive and is no longer updated; all paths below are relative to the repo root (iOS project in `ios/`, so run `xcodegen generate` and `xcodebuild` from there). The icon sources are in `design/IconComposer/`.

Stable facts that are not in `ios/HANDOFF.md` or `android/PLAN.md`. Read those for architecture, file map and status.

## Preferences and feedback
- Keep replies short. Report outcomes plainly, including what was only build-checked and not looked at.
- Korean UI text, friendly "~해요" tone. Text-only menus; no image fallback.
- English follows the phone (or the per-app language in system Settings); there is no in-app toggle. In English, dishes show translated and tapping a menu card flips it to Korean. A dish missing from the dictionary gets a word-by-word guess, or stays Korean.
- Everything runs on the device. No backend, no Tailscale.
- iPhone is portrait only (landscape was judged not worth it). iPad supports all four orientations.
- The overall text scale is fixed at 80%. It was chosen on a device with a temporary slider, which was then removed. The in-app size setting has three tiers: 작게 / 보통 / 크게.
- Do not commit or push unless asked.

## Decisions and why
- Translation is display-only: Korean stays the data and the keys (meal names drive the hours, cafeteria names match the saved default), so caches and prefs are untouched. `shared/translations/en.json` is the one dish/place dictionary for both apps; screen text lives in Android `values-en/strings.xml` and iOS `Localizable.xcstrings`.
- Menus are OCR'd on the device with Apple Vision from the posted images, because the site only publishes images and there is no backend.
- The 의대식당 post is merged in but fails independently, so a missing or broken 의대 post never hides the main menu.
- Cached menu key is `cached-weekly-menu-v2`. Stored text-size raw values stay stable (-1 / 0 / 1); an unknown stored value falls back to 보통.
- Reordering in 운영 시간 uses ▲/▼ buttons, not drag, because the list is a custom card layout and not a `List`.
- Hours and the serving schedule are hard-coded from the official notice (2026학년도 2학기, 8/31–12/11) and must be replaced each semester.
- Accent is the design's #D23312 at 64% flattened to #E27D68. White on it is about 3:1; kept per the design.
- App Store: primary category Food & Drink. The menus come from a public page and the app links to the source post. Say so in the review notes.

## Mac / Xcode setup
- Xcode 27.1, xcodegen 2.46.0. `project.yml` is the source of truth. Run `xcodegen generate` after adding or removing files.
- Keep `GENERATE_INFOPLIST_FILE: YES`. `xcodeVersion: "27.1"` silences the recommended-settings prompt.
- Signing: automatic, team `S9553U34PA`, bundle id `io.github.scerial.soonmenu`.
- iPhone 17 Pro simulator id: `3984BDE0-402B-472E-8E07-B2BBCF95D66A`. The iPad Pro 13" simulator `6C54E96D-…` no longer resolves as a destination.
- Test: `xcodebuild test -project WeeklyMenu.xcodeproj -scheme WeeklyMenu -destination 'id=3984BDE0-402B-472E-8E07-B2BBCF95D66A' -derivedDataPath <temp dir>`. The OCR test takes about 45 s.
- App icon: Icon Composer `Sources/AppIcon.icon`, enabled by `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`. Source layers live in `design/IconComposer/`.
- The `.icon` bundles are folders on macOS but show up as 0-byte files in iCloud for Windows, so edit them on the Mac only.

## Windows / Android setup
- Windows clone: `C:\dev\menu-updater`, outside iCloud Drive and OneDrive. Android work goes on the `android-port` branch; iOS fixes go on `main` or a short-lived branch. The user pulls before starting and pushes when done on each machine; agents commit or push only when asked.
- Why not iCloud: it leaves files as placeholders, can't represent macOS bundles on Windows, can create "file 2" conflict copies, and would sync Gradle and Xcode build output.
- `gh` is installed and signed in on both machines (it may not be on PATH in older Windows shells); `gh auth setup-git` is done. The commit identity is set globally on each machine.
- In the VS Code extension, a `!` command typed in the prompt is sent as text and not run. Run interactive logins as a background task and pass the device code to the user.
- Line endings: `.gitattributes` uses `text=auto`, so the "LF will be replaced by CRLF" warnings on Windows are expected.
- First Windows PC (set up 2026-10-25): Android Studio and the SDK are in `%LOCALAPPDATA%\Android\Sdk`; `JAVA_HOME`/`ANDROID_HOME` are saved as user env vars (Studio's bundled JBR). AVDs: `phone_api36/33`, `tablet_api36/33`. Wifi debugging: `adb mdns services`, then `adb connect <ip:port>`; the address changes after a reconnect. A phone with the app signed by another PC's debug key needs `adb uninstall io.github.scerial.soonmenu` before `installDebug`.
- Gated JVM tools (set the env var, run `:app:testDebugUnitTest --tests "*Name*"`): `BUILD_LEXICON=1`, `EVAL_VOTING=1`, `EXPORT_DISHES=1`, `EVAL_TRANSLATION=1`. They read the local, uncommitted `android/ocr-corpus/` (see `ocr/OCR_PLAN.md`).
- App name: 순대 학식 (Korean) / SCH Menu (English). The app id (Android `applicationId`, iOS bundle id) is `io.github.scerial.soonmenu`; the Kotlin `namespace` and source package stay `com.menuupdater.weeklymenu`.
## Gotchas
- Previews (`PreviewData.swift`) can only be rendered in the Xcode canvas, not from the command line.
- OCR output varies between runs and can misread dish names.
- Column headers read `10월20일(화)`, and the year is never shown, so matching uses month and day.
- The ingredient-origin line ("쌀: 국내산 / …") sits above the `원산지` label and must stay cut off from the last meal.
- Delete the app from the simulator after changing the icon; the home screen caches icons.
- On the Mac, `gh` is not installed and git has no stored GitHub credentials in the Claude Code environment, so clone, pull and push have to be done by the user or after logging in.
