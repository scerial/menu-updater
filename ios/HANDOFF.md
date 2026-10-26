# Agent Handoff: Weekly Menu App

## Goal
Build a native SwiftUI iPhone/iPad app that shows today's and the weekly menu from posts on https://www.agoodplacefnb.com/today.

Main posts are those whose title contains `순천향대학교` and not `의과대학`; the 의과대학 (천안 의대식당) post is crawled separately and merged in (see Update 2026-10-22). Download menu image(s), recognize Korean text on-device with Apple Vision, show a Today and Week view, and cache the last successful menu for offline use. The user explicitly chose on-device processing over a Windows desktop service, so do not add a backend or Tailscale dependency for v1. Text-only display is preferred; there is no image fallback requirement.

## Status (verified 2026-10-20)
The app builds, and the full flow was run on an iPhone 17 Pro and an iPad Pro 13" simulator (iOS 27.0) against the live site. Today shows both cafeterias for the current date, with meals grouped correctly, and the cached menu appears immediately on relaunch.

Not yet done: testing on a physical device, and the iPad/iPhone layouts in landscape or split view. The Xcode project is generated: run `xcodegen generate` after adding or removing files (`project.yml` is the source of truth; `GENERATE_INFOPLIST_FILE` must stay on).

### Update 2026-10-21
- Rebuilt the UI to match the design canvas (see Files). Not in the design build: calories, prices, locations, spicy tags (no data), notifications toggles (not implemented).
- Returning to the app now reloads the menu if the last successful load is over 30 minutes old or the date has changed. Today/Week use `MenuViewModel.today`, which updates on every foreground, so the date stays right even when the reload fails offline.
- One menu image failing to download or OCR no longer discards the other cafeteria; the load only fails if no image produces a table (the first error is reported).
- 18 unit tests pass (new: `testOneFailedImageDoesNotDiscardTheOtherCafeteria`, which runs real Vision on a generated image).

### Update 2026-10-22
- **의대식당 crawler:** `MenuRepository.findLatestPosts()` picks the newest university post and the newest 의과대학 post from the first board page that has any match. `fetchLatestMenu()` merges both with failure isolation (it throws only if neither yields a menu). `WeeklyMenu.additionalSources` (optional, so old caches still decode) and `sources` list both posts; `SourceFooter` shows both. `eligiblePosts` still returns university posts only; `medicalSchoolPosts` is new. The 의대 image uses the same table layout, header `순천향의대식당 주간메뉴표`, 중식 only.
- **Parser fix:** the ingredient-origin lines above the `원산지` label ("쌀: 국내산 / 돈육: …") used to leak into the last meal card. The footer cutoff is now the highest line containing any of 원산지/국내산/외국산/중국산/수입산. Test: `testOriginRowsAboveTheLabelAreNotMenuItems`.
- **Text size:** three tiers, 작게 / 보통 / 크게 (`MenuTextSize`, raw values -1 / 0 / 1; 작게 is 16/19 of 보통). 아주 크게 was removed; a stored value of 2 falls back to 보통. Overall `Theme.uiScale` is still 0.8.
- **운영 시간 reordering:** 순서 편집 (top right of the header) shows ▲/▼ per building; the order is stored in `@AppStorage("venue-building-order")` as comma-separated building names (`VenueDirectory.groups(orderedBy:)`; unknown names are ignored, new buildings go last). 기본 순서로 resets. On iPad the first three buildings go in the left column, the rest on the right. Test: `testSavedBuildingOrderIsApplied`.
- **Header:** `ScreenHeader` has more vertical padding and an optional `trailing` view at the right end of the title row.
- **App icon:** `Sources/AppIcon.icon` (Icon Composer; source layers and README in `../design/IconComposer`), enabled by `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon` in `project.yml`.
- **Tray mark:** the fork-and-knife is replaced by the app icon's simplified tray (`Sources/Assets.xcassets/Tray.imageset`, template-rendered; `TrayIcon` / `TabIcon` in `MenuViews.swift`) in the tab bar, sidebar logo and empty state.
- **Orientation / project:** iPad supports all four orientations, iPhone is portrait only; `xcodeVersion: "27.1"` in `project.yml`.
- **Previews:** `Sources/PreviewData.swift` (`#if DEBUG`) has sample data from real posts, a preview-only `MenuViewModel` init and 16 `#Preview`s (iPhone Today states, Week, 운영 시간, Settings, iPad landscape/portrait). They have not been rendered by the agent; check them in the Xcode canvas.
- 30 unit tests passed on the last full run; the header, icon and tray changes after it were build-checked only.

### Update 2026-10-26
- **Board date fix (iOS only):** recent posts show a relative time ("3시간전") in the row text, so the `yyyy-MM-dd` regex found nothing and those posts were skipped. `MenuRepository` now reads the date from `li.time`'s `title` first, then falls back to the row text. Merged from `claude/ios-menu-update-issue-zf8z44`. Android does not have this bug (confirmed by the user), so `BoardParser.kt` needs no change.

## Files
- `project.yml`: XcodeGen manifest; iOS 17.0, SwiftSoup dependency, app and unit-test targets.
- `Sources/WeeklyMenuApp.swift`: app entry and the `@Observable` view model (load, refresh, foreground refresh, cache).
- `Sources/MenuViews.swift`: UI from the "Today's Menu" design (claude.ai/code/artifact/c5274f53-e299-4c59-885d-a93f573715be), Korean throughout. 오늘 / 이번 주 / 운영 시간 / 설정 tabs; bottom tab bar on iPhone and narrow iPad, sidebar at ≥1000pt wide; cafeteria picker on phone, both cafeterias side by side at ≥700pt. Today: meal cards with hours, status pill (운영 중/예정/종료), main dish (first item) and side-dish chips. Week: design option B (pick a meal, one row per day).
- `Sources/SettingsView.swift`: default cafeteria, menu text size, source link, app version. Overall text scale is fixed at 80% (`Theme.uiScale`).
- `Sources/VenueStatus.swift`: the 운영 시간 tab (after 이번 주) — every restaurant in the 운영 안내 notice with a green / yellow (≤30 min to close) / grey dot, Friday early closes and weekend hours; 향설 lunch counts as ended at 13:30. Hours are hard-coded for 8/31–12/11; replace when the next notice is out. `MealSchedule` (Theme.swift) applies the same weekend/Friday/13:30 rules to the Today pills.
- `Sources/PreviewData.swift`: debug-only sample menu and `#Preview`s.
- `Sources/Assets.xcassets`: the tray image; `Sources/AppIcon.icon`: the app icon.
- `Sources/Theme.swift`: design colours (with dark variants), `appFont`, `MealSchedule` (weekday serving hours from the 2026 fall-semester 학내식당 운영 안내, valid 8/31–12/11; update next semester), `FlowLayout`.
- `Sources/MenuRepository.swift`: board and detail parsing, title filtering, pagination, downloads (timeout + HTTP status checks), Vision OCR, cache (`cached-weekly-menu-v2`).
- `Sources/MenuTableParser.swift`: rebuilds the weekly table (cafeteria → days → meals → items) from OCR text and bounding boxes.
- `Tests/`: parsing tests, parser tests using real Vision output (`OCRFixtures.swift`), and failure-path tests with a stubbed `URLProtocol`.

## How the live site works (verified)
- Board rows are `ul.li_body`; the title link is `a.list_text_title`, the date is `li.time` (`yyyy-MM-dd`). The board is newest-first, so page 1 normally holds the latest eligible post. Page 2+ use `?q=<base64>&page=N`.
- Each weekly post has one image per cafeteria (`img` with `cdn.imweb.me/upload` src): 향설 1관 (조식/중식/석식) and 교직원식당 (중식 only). Titles can carry suffixes such as `_9/28수정`.
- Column headers read `10월20일(화)`, not `화요일`. Today is matched on month and day.

## Known limitations / risks
- The "shared row" (a full-width line such as `아산쌀밥/김치/샐러드`) is detected heuristically: the lowest centre-column item that sits below every other column. A table with a genuinely longer centre column and no shared row would misplace one item.
- OCR can misread characters (seen: 콩나물해자국, 열무된정무침, 된정국). Results vary between runs. There is no dictionary correction.
- The parser assumes a Mon–Fri date-header table with 조식/중식/석식 labels on the left and an `원산지` footer. A different layout produces the "not identified" state, or no table at all.
- Visually unverified: Week and Settings tabs, 운영 시간 edit mode, iPad portrait, dark mode, the three-column iPad Today/Week with the 의대 menu added (may be cramped), and the tray at tab-bar size. White on the coral accent is about 3:1 contrast (kept per the design).
- Serving hours and venue hours are hard-coded for 8/31–12/11 and need updating next semester.
- The iPad Pro 13" simulator `6C54E96D-…` no longer resolves as a destination; tests use the iPhone 17 Pro simulator `3984BDE0-402B-472E-8E07-B2BBCF95D66A`.
- Imweb markup can change; the date lookup still uses `link.parent().parent()`.
- First load on a cold simulator took 19–63 seconds (mostly OCR and simulator start-up). Not yet timed on a device.
- The year is not part of the headers, so matching uses month/day only.

## Suggested next steps
1. Run on a physical iPhone and time the first load; consider showing OCR progress.
2. Consider tolerating more layouts (weekend columns, a missing footer) and add fixtures for any new ones.
3. Consider a small correction pass for common menu words if OCR errors are a problem in practice.
4. App Store: suggested primary category Food & Drink; mention in the review notes that the menus come from a public page and the app links to the source post.

## English translation (2026-10-26, written on Windows, not yet built)
- New: `Sources/Translator.swift` (dish translator, `Tr` display language via `\.tr`, `AppDate`, `LanguageFlip` tap-to-flip), `Sources/Localizable.xcstrings` (Korean source, English), `Sources/InfoPlist.xcstrings` (display name 순대 학식 / SCH Menu), `Tests/TranslatorTests.swift`.
- `project.yml`: `developmentLanguage: ko`, bundles `../shared/translations/en.json` (shared with Android), `INFOPLIST_KEY_CFBundleDisplayName`.
- Translation is display-only; `VenueStatus.label` and `MealSchedule.Status.label` stay Korean (tests unchanged), views use `displayLabel`.
- On the Mac: `xcodegen generate`, then `xcodebuild test`. Check the String Catalog picked up every key (Xcode marks new ones), and run the simulator with the app language set to English (Edit Scheme → Options → App Language) and Korean.
