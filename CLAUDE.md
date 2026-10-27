# Menu Updater — Claude context

Development now happens in this repo (Mac clone `~/Developer/menu-updater`, Windows clone `C:\dev\menu-updater`). The old iCloud Drive folder `Menu Updater/` is an archive and is no longer updated; all paths below are relative to the repo root (iOS project in `ios/`, so run `xcodegen generate` and `xcodebuild` from there). The icon sources are in `design/IconComposer/`.

Stable facts that are not in `ios/HANDOFF.md` or `android/PLAN.md`. Read those for architecture, file map and status.

## Preferences and feedback
- Keep replies short. Report outcomes plainly, including what was only build-checked and not looked at.
- Korean UI text in the formal "~합니다/~입니다" tone (changed from "~해요" on 2026-10-27), on both platforms. Text-only menus; no image fallback.
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

## Legal
Reviewed 2026-10-26 for the Google Play release (Play Data policy, a risk assessment and a User Data policy compliance check). Not legal advice; re-check Play Console guidance before each submission.
- Privacy policy: `docs/privacy/index.html`, live on GitHub Pages (main, /docs) at https://scerial.github.io/menu-updater/privacy/ (`#ko` / `#en`). Korean is written in 합니다체 following Naver's privacy policy; English mirrors it. Update the 시행일 and both languages whenever data handling changes (new SDK, analytics, ads, accounts, permissions).
- What the policy states, and must stay true: no accounts, ads, analytics or backend; only the `INTERNET` permission; settings and the menu cache stay on the device; the menu site and its image CDN see IP, time and User-Agent; OCR runs on the device and images are not uploaded; ML Kit sends diagnostics to Google (device model/OS, app package/version, performance, API config, I/O sizes, error codes, a per-install ID); `allowBackup` lets Google/iCloud backups include app data; contact is jacobkim0707@gmail.com.
- Play Data safety must match: data collected = "Device or other IDs" and "App info and performance (diagnostics)" via ML Kit, purpose analytics, not shared, encrypted in transit, no deletion request (developer holds nothing). Source: ML Kit Android data disclosure page.
- In-app: Settings > 정보 links to the policy and its footer says the app is unofficial and that ML Kit may send diagnostics. The menu footer warns that OCR can be wrong and to check allergy/origin info in the original post. No consent screen: ML Kit diagnostics were judged not to be personal/sensitive data, so prominent disclosure was not added; add one if Play review asks.
- User-Agent is `Mozilla/5.0 (compatible; SCHMenu; +https://scerial.github.io/menu-updater/privacy/)` on both platforms (the Mozilla prefix is kept in case the site filters non-browser agents) so the site operator can identify and reach the app.
- Risk assessment (severity x likelihood): YELLOW, OCR misreads relied on for allergies (8, mitigated by the disclaimer); YELLOW, Play IP/impersonation rejection because content comes from agoodplacefnb.com and the name uses 순대/SCH (6-9); YELLOW, Data safety vs policy mismatch (6). GREEN: 부정경쟁방지법 (파)목 / DB rights claim (3), server load (4), stale hours (4), 개인정보 보호법 (2), public personal email (4).
- Crawling precedent: in 야놀자 v 여기어때 the criminal charges (정보통신망법, 저작권법, 업무방해) ended in acquittal (Supreme Court, 2022) because the data was public, but the civil case found 부정경쟁방지법 성과물 도용 and awarded 10억 won. That case was a commercial competitor copying a DB with ~16M requests. This app is free, non-competing and links the source, which is why the civil risk is GREEN. Adding ads or paid features means re-assessing.
- Keep: free and ad-free; no university logo in the icon, screenshots or listing; the store description's first line says it is an unofficial app not affiliated with 순천향대학교 or the cafeteria operator; refresh no more than every 30 minutes.
- Open: ask 어굿플레이스 F&B or the university for written permission to show the menus (strongest answer to an IP complaint); confirm the Play developer name appears in the policy.

## Mac / Xcode setup
- Xcode 27.1, xcodegen 2.46.0. `project.yml` is the source of truth. Run `xcodegen generate` after adding or removing files.
- Keep `GENERATE_INFOPLIST_FILE: YES`. `xcodeVersion: "27.1"` silences the recommended-settings prompt.
- Signing: automatic, team `S9553U34PA`, bundle id `io.github.scerial.soonmenu`.
- iPhone 17 Pro simulator id: `3984BDE0-402B-472E-8E07-B2BBCF95D66A`. The iPad Pro 13" simulator `6C54E96D-…` no longer resolves as a destination.
- Test: `xcodebuild test -project WeeklyMenu.xcodeproj -scheme WeeklyMenu -destination 'id=3984BDE0-402B-472E-8E07-B2BBCF95D66A' -derivedDataPath <temp dir>`. The OCR test takes about 45 s.
- App icon: Icon Composer `Sources/AppIcon.icon`, enabled by `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`. Source layers live in `design/IconComposer/`.
- The `.icon` bundles are folders on macOS but show up as 0-byte files in iCloud for Windows, so edit them on the Mac only.

## Windows / Android setup
- Windows clone: `C:\dev\menu-updater`, outside iCloud Drive and OneDrive. Work goes on a short-lived branch from `main` for both platforms, merged by pull request and then deleted. The user pulls before starting and pushes when done on each machine; agents commit or push only when asked.
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
