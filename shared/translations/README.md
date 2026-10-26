# Shared translations

`en.json` is the English for both apps (Android reads it from its assets, iOS bundles it). Korean is the key; nothing in the apps stores English.

- `dishes`: canonical dish → English. `aliases`: common OCR misread → canonical (iOS has no corrector, so it needs these).
- `glossary`: parts for the word-by-word guess when a dish isn't listed. A value starting with `^` goes first in English (`무침` → `^Seasoned`).
- `places`, `meals`, `weekdays`: cafeterias from the menu titles, buildings and venues from the hours notice, 조식/중식/석식, 월–일.

Screen text (tabs, buttons, messages) is not here: see `android/app/src/main/res/values-en/strings.xml` and `ios/Sources/Localizable.xcstrings`.

## Each semester

Rebuild with the dish lexicon (see `android/app/src/main/java/com/menuupdater/weeklymenu/ocr/OCR_PLAN.md`):

1. `$env:EXPORT_DISHES="1"; .\gradlew.bat :app:testDebugUnitTest --tests "*ExportDishList*"` lists every dish (`android/build/dish-export.tsv`) and misread (`dish-aliases.tsv`).
2. Add English for new dishes and aliases here. Update `places` if the hours notice adds venues, and the Android `schedule.json` `semesterLabelEn` / `footerEn` plus the iOS catalog's semester strings.
3. `$env:EVAL_TRANSLATION="1"; .\gradlew.bat :app:testDebugUnitTest --tests "*TranslationCoverage*"` writes `android/build/translation-report.txt` with every guess and every dish left in Korean.
