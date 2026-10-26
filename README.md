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
- Work on a short-lived branch from `main` (for example `feature/ocr-lexicon`), open a pull request, merge it, and delete the branch. iOS and Android both go through `main`.
- Each store release is tagged on `main` as `vMAJOR.MINOR` (for example `v0.2`), matching the app's `versionName`.
- `ios/WeeklyMenu.xcodeproj` is not committed. After cloning or pulling, run `xcodegen generate` in `ios/`.
- The `.icon` bundles (`ios/Sources/AppIcon.icon`, `design/IconComposer/icon.icon`) are folders on macOS; edit them on the Mac.

## License

Copyright (C) 2026 Youngmin Kim

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>. The full text is in [`LICENSE`](LICENSE).

SPDX-License-Identifier: GPL-3.0-or-later

### Additional permission under GNU GPL version 3 section 7

If you modify this Program, or any covered work, by linking or combining it with Google ML Kit (or a modified version of that library), containing parts covered by the terms of the ML Kit Terms of Service, the licensors of this Program grant you additional permission to convey the resulting work.
