# 순대 학식 / SCH Menu (Weekly Menu)

Native iPhone and iPad app that loads the latest eligible weekly menu post from A Good Place, downloads its menu images, and recognizes Korean text on-device with Apple Vision.

## Build

Requires macOS with Xcode and XcodeGen installed. From this directory:

```sh
xcodegen generate
open WeeklyMenu.xcodeproj
```

Select an iPhone or iPad simulator in Xcode and run the `WeeklyMenu` scheme. Unit tests are in `Tests` (run them with the `WeeklyMenu` scheme as well).

The app filters post titles to include `순천향대학교` and exclude `의과대학`. Each menu image is parsed into a table of days and meals using Vision's text positions. The Today view matches the current date against the day headers in the image; if the date isn't listed, it says so rather than guessing. The Week view shows every day. The UI follows the phone's language: Korean, or English with translated dishes (tap a menu card to flip it to Korean). English comes from `Sources/Localizable.xcstrings` and the shared `../shared/translations/en.json`. Menu text is processed on-device and the last successful menu is cached locally for offline use.
