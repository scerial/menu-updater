# Deploy Checklist: SoonMenu 0.2 CBT (Android, Google Play closed track)

**Date:** 2026-10-27 | **App id:** `io.github.scerial.soonmenu` | **Release base:** `main` @ `3d89f2f` (matches `origin/main`) plus 3 local commits on `ios-disclaimer-privacy` (tip `3018c0d`), pending PR

iOS (TestFlight) is not covered here.

## What ships: commits

### Already on `main` (37 commits, all dated 2026-10-22 to 2026-10-26)

| Area | Commits |
| --- | --- |
| Release prep, legal | `3234608` Play privacy/legal compliance, upload-key signing, store assets · `2b391c6` app id → `io.github.scerial.soonmenu` · `3aae29d` privacy policy page · `126fd05` / `071a862` Korean policy tone · `7679516` / `ac9e5ed` remove personal details, harden `.gitignore` · `075da6e` GPL-3.0-or-later |
| English translation | `3966548` English follows the phone's language · `706dde1` shorter English place names, wrapped cafeteria labels · `f792bc1` 천안의대식당 → 순천향의대식당 |
| OCR | `47cabab` vote across three OCR readings · `b38c504` dish-lexicon correction · `d4a1767` OCR quality |
| Menu data | `cd8ba8f` keep the fuller menu on a partial refresh · `f7a6852` iOS board dates from `li.time title` (Android unaffected) |
| Android port | `443d040` → `f8338df` steps 1–6 (Compose baseline, OCR spike, domain/parser/repository, OCR service/cache/prefs, adaptive UI) · `b71ccbc` PR #1 · `7dbd210` launcher icon |
| Merges | `d70eb7d` merge `english` · `a8ca6d5` merge `main` into `english` · `fe5ad38` merge iOS menu-update-issue branch |
| Workflow/docs | `4c5a3dd` bump to 0.2 · `97ccaba` revert the bump and drop the tagging rule · `954ca56` docs · `3d89f2f` trim HTML test fixtures (HEAD) |

### Local commits on `ios-disclaimer-privacy`, not yet pushed or merged

| Commit | What |
| --- | --- |
| `b5a9c0e` | Korean UI text to 합니다체, plus related iOS/Android strings, views, repository, translator, venue status, `MenuPost.kt`, `schedule.json`, HANDOFF and CLAUDE.md notes (11 files, 112 insertions, 63 deletions) |
| `8ec13d5` | Coral background layer for the app icon (cherry-picked from `7642b72` on `english`) |
| `3018c0d` | Android `versionName` 0.2, `versionCode` 2 |

## Decisions (settled 2026-10-27)

- [x] **Version:** 0.2, `versionCode` 2. The earlier 0.1 revert (`97ccaba`) is superseded for this release. The `vMAJOR.MINOR` tagging rule stays dropped.
- [x] **Staged changes:** go into the CBT (`b5a9c0e`).
- [x] **Coral icon:** goes to `main` via this branch (`8ec13d5`).
- [x] **Release branch:** release from `main`. Work is merged by pull request (project workflow).

## Pre-Deploy

- [ ] Push `ios-disclaimer-privacy` and open a PR into `main`. This Mac has no `gh` or stored GitHub credentials, so do it from another machine or after logging in.
- [ ] Merge the PR so `main` contains every commit listed above.
- [ ] Confirm `versionName = "0.2"` and `versionCode = 2` on `main`, and that `versionCode` is higher than anything already uploaded to Play.
- [ ] Run the Android unit tests (`android/app/src/test`), then the instrumented tests (`androidTest`).
- [ ] Run iOS `MenuParsingTests` if the shared parser changed (about 45 s for the OCR test).
- [ ] Build a signed release AAB with the upload key. `*.jks`, `*.keystore` and `key.properties` are gitignored and none are tracked.
- [ ] Confirm the AAB is not signed with a debug key.
- [ ] Confirm the privacy policy (https://scerial.github.io/menu-updater/privacy/, `#ko` / `#en`) is live. If data handling changed, update the 시행일 and both languages.
- [ ] Play Data safety matches the policy: "Device or other IDs" and "App info and performance (diagnostics)" via ML Kit, purpose analytics, not shared, encrypted in transit, no deletion request.
- [ ] Other Play declarations: content rating, target audience, app access. Only the `INTERNET` permission is declared.
- [ ] Store listing: first line says the app is unofficial and not affiliated with 순천향대학교 or the cafeteria operator. No university logo in the icon, screenshots or listing. App name 순대 학식 / SCH Menu.
- [ ] Screenshots in Korean and English, short and full descriptions.
- [ ] Release notes in Korean (합니다체) and English: English translation, OCR voting/lexicon, partial-refresh fix, venue renames.
- [ ] The Play developer name appears in the privacy policy.
- [ ] The hours and serving schedule (2026학년도 2학기, 8/31–12/11) are still current.
- [ ] Tester list or Google Group is set up and added to the closed track.

## Device Testing

`minSdk` is 33, so use Android 13 or newer on a real device.

- [ ] Fresh install in Korean: menu loads, venue names are correct (순천향의대식당), tone is 합니다체.
- [ ] Phone set to English: UI and dishes are translated, place names are short, cafeteria labels wrap without clipping.
- [ ] Tapping a menu card in English flips it to Korean. A dish missing from the dictionary gets a word-by-word guess or stays Korean.
- [ ] Refresh on a flaky network keeps the fuller menu.
- [ ] A missing or broken 의대식당 post does not hide the main menu.
- [ ] OCR voting and lexicon results are sensible on a few real posts.
- [ ] Menu footer shows the OCR warning. Settings > 정보 links to the policy.
- [ ] Refresh no more than every 30 minutes.
- [ ] Upgrade from the previous build keeps settings and cached data.
- [ ] Offline launch does not crash.

## Deploy

- [ ] Upload the AAB to the closed testing track.
- [ ] Wait for Play review. The first review of a new app id can take days.
- [ ] Send the opt-in link to testers.
- [ ] Confirm the install from the Play listing, not a sideload, on at least one tester device.

## Post-Deploy

- [ ] Check Play Console Android vitals and the pre-launch report.
- [ ] Update HANDOFF, PLAN and the README with the release status.
- [ ] Collect tester feedback on translation quality and OCR accuracy.

## Rollback Triggers

Play cannot force-uninstall a build. Rolling back means halting the rollout and shipping a fixed build with a higher `versionCode`.

- Crash on launch, or a crash rate above about 2% of testers.
- The menu fails to load or parse on a normal network.
- A wrong or garbled English translation on core screens, or a broken Korean experience.
- The privacy declarations turn out to be inaccurate.
- Play review or the menu source (agoodplacefnb.com) raises an IP or impersonation complaint.

## Open items (not blocking CBT)

- Ask 어굿플레이스 F&B or the university for written permission to show the menus.
- Add a prominent ML Kit diagnostics disclosure if Play review asks.
- Re-assess the legal risk before adding ads, paid features or accounts.
