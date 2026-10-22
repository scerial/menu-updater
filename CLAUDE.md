# Menu Updater — Claude context

Development now happens in this repo (`~/Developer/menu-updater`). The old iCloud Drive folder `Menu Updater/` is an archive and is no longer updated; all paths below are relative to the repo root (iOS project in `ios/`, so run `xcodegen generate` and `xcodebuild` from there). The icon sources are in `design/IconComposer/`.

Stable facts that are not in `ios/HANDOFF.md` or `android/PLAN.md`. Read those for architecture, file map and status.

## Preferences and feedback
- Keep replies short. Report outcomes plainly, including what was only build-checked and not looked at.
- Korean UI text, friendly "~해요" tone. Text-only menus; no image fallback.
- Everything runs on the device. No backend, no Tailscale. The `Backend(Windows host)` folder is empty on purpose.
- iPhone is portrait only (landscape was judged not worth it). iPad supports all four orientations.
- The overall text scale is fixed at 80%. It was chosen on a device with a temporary slider, which was then removed. The in-app size setting has three tiers: 작게 / 보통 / 크게.
- Do not commit or push unless asked.

## Decisions and why
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
- Signing: automatic, team `S9553U34PA`, bundle id `com.menuupdater.weeklymenu`.
- iPhone 17 Pro simulator id: `3984BDE0-402B-472E-8E07-B2BBCF95D66A`. The iPad Pro 13" simulator `6C54E96D-…` no longer resolves as a destination.
- Test: `xcodebuild test -project WeeklyMenu.xcodeproj -scheme WeeklyMenu -destination 'id=3984BDE0-402B-472E-8E07-B2BBCF95D66A' -derivedDataPath <temp dir>`. The OCR test takes about 45 s.
- App icon: Icon Composer `Sources/AppIcon.icon`, enabled by `ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon`. Source layers live in `IconComposer/`.
- The project sits in iCloud Drive. A recursive grep of the whole drive times out, so search inside the project folder only.

## Gotchas
- Previews (`PreviewData.swift`) can only be rendered in the Xcode canvas, not from the command line.
- OCR output varies between runs and can misread dish names.
- Column headers read `10월20일(화)`, and the year is never shown, so matching uses month and day.
- The ingredient-origin line ("쌀: 국내산 / …") sits above the `원산지` label and must stay cut off from the last meal.
- Delete the app from the simulator after changing the icon; the home screen caches icons.
- `gh` is not installed on this Mac, and git has no stored GitHub credentials in the Claude Code environment, so clone, pull and push have to be done by the user or after logging in.
