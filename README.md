# Menu Updater

Shows today's and this week's 순천향대학교 cafeteria menus from https://www.agoodplacefnb.com/today (app name 순대 학식 / SCH Menu). The UI is Korean, and English when the phone is set to English. Menu images are downloaded and read with on-device Korean OCR; there is no backend.

| Folder | What | Built on |
|---|---|---|
| `ios/` | SwiftUI iPhone/iPad app (Apple Vision OCR). See `ios/README.md` and `ios/HANDOFF.md`. | Mac (Xcode + XcodeGen) |
| `android/` | Kotlin + Jetpack Compose port (ML Kit OCR). See `android/port_plan.md` (requirements) and `android/PLAN.md` (working plan and status). | Windows (Android Studio) |
| `shared/translations/` | `en.json`: the English for dishes, cafeterias, venues and meals, used by both apps. See its README. | — |
| `design/IconComposer/` | App icon source layers (the icon in use). `design/IconB/` is an unused alternative. | — |

## Working across Mac and Windows

Clone outside iCloud Drive / OneDrive (e.g. `~/Developer/menu-updater` on the Mac, `C:\dev\menu-updater` on Windows).

- Pull before you start, push when you stop.
- Android work happens on the `android-port` branch; iOS fixes go on `main` or a short-lived branch.
- `ios/WeeklyMenu.xcodeproj` is not committed. After cloning or pulling, run `xcodegen generate` in `ios/`.
- The `.icon` bundles (`ios/Sources/AppIcon.icon`, `design/IconComposer/icon.icon`) are folders on macOS; edit them on the Mac.
