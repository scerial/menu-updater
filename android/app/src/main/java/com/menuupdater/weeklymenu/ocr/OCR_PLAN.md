# OCR: state and plan

Lives next to `OcrService.kt`. The overall Android status is in `android/PLAN.md`.

## Where OCR stands (2026-10-24)
- **Engine:** ML Kit Korean, **bundled** model (`com.google.mlkit:text-recognition-korean`), on-device. Line order is unreliable, so `MenuTableParser` rebuilds the table from the bounding boxes.
- **Junk filter** (`MenuTableParser.cleanItem`): strips runs of Latin letters and drops lines with no Hangul left, or with a single leftover syllable. This removes logo and watermark fragments such as HALAL, SCH, AGOO0-PACE and MAL사. Covered by the `dropsLogoAndWatermarkTokensThatOcrIntoCells` test.
- **Preprocessing** (`OcrService.preprocess`): contrast ×1.6, then a 1.5× upscale. Boxes are normalized against the processed bitmap, so coordinates don't shift.
- **Measurement** (`androidTest/.../OcrQualityTest`): on the Note 20 it checks for 8 dishes that the baseline misreads.

  | variant | targets read correctly |
  |---|---|
  | baseline | 3/8 |
  | grayscale | 3/8 |
  | upscale 2× | 6/8 |
  | grayscale + contrast | 6/8 |
  | contrast only | 6/8 |
  | binarize | 5/8 |
  | contrast + 2× | 7/8 |
  | grayscale + contrast + 2× | 7/8 |
  | **contrast + 1.5× (shipped)** | **7/8** |

  숭늉 is never read correctly. OCR time is about 1 s for all 3 images under every variant.
- **Still wrong:** misreads vary from cell to cell, so a dish can read correctly in one place and wrong in another (공나물국 vs 콩나물국). 숭늉 is read as 숭능.
- **Not measured:** the unbundled "downloadable" model (`play-services-mlkit-text-recognition-korean`). It defines the same `KoreanTextRecognizerOptions` class as the bundled model, so both can't be in one APK. Comparing them needs a separate build flavor. This is deferred.
- **Run:**
  - Status: committed in `d4a1767`.
  - To rerun the measurement: `.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.menuupdater.weeklymenu.OcrQualityTest"`.
  - The report is in logcat under the `OcrQuality` tag. `connectedAndroidTest` uninstalls the app after the run, so the report file it writes is deleted with it.

## Dish lexicon from past menus (implemented 2026-10-25, not committed)

### What was built, and where it differs from the plan below
- `domain/DishCorrector.kt` (+ `DishSegments`, `DishLexicon`), `ocr/OcrService.Preprocess`, `androidTest/.../DishCorpusDump.kt`, `test/.../BuildDishLexicon.kt`, `assets/dish-lexicon.json`. Wired into `MenuRepository` (new parses) and `MenuViewModel` (cached menu too).
- **`RATIO` is 2, not 5.** At 5 the known misreads stayed (공나물불고기 5 vs 콩나물불고기 10; the fixture test failed). At 2 the hold-out on the newest week made 8 changes, all right.
- **Two reviewed lists in the JSON:** `keep` (real dishes never rewritten: 부추겉절이, 곤약메추리알장조림) and `fixes` (장각삼계팅 → 장각삼계탕, too close to call by count, 4 vs 5). They live as constants `KEEP` and `FIXES` in `BuildDishLexicon` and are written into the JSON on every rebuild.
- Corpus: 90 unique images, 270 readings (the dump also re-read about 40 images because the board lists some posts under two URLs; the files overwrite each other). 896 dishes, 205 rewrites to review in `build/lexicon-report.txt`.
- Not fixed: 숭늉 (never read right), a dropped syllable (간장돈육고기 for …불고기), a dropped `*` separator (감자튀김케찹).

### Rebuild each semester (with `schedule.json`)
```
gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class com.menuupdater.weeklymenu.DishCorpusDump com.menuupdater.weeklymenu.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.menuupdater.weeklymenu/files/corpus android/ocr-corpus
$env:BUILD_LEXICON="1"; gradlew :app:testDebugUnitTest --tests "*BuildDishLexicon*"
```
Then read `android/build/lexicon-report.txt`, update `KEEP`/`FIXES` if a real dish was rewritten, and rebuild. If the phone has this app signed by another PC's debug key, `adb uninstall com.menuupdater.weeklymenu` first.

### Runtime voting (2026-10-25, not committed)
- Each image is now read three ways (`CONTRAST_UPSCALE` as the reference, plus `CONTRAST` and `NONE`); `domain/TableVoter` keeps the spelling most readings agree on, per dish, with the reference deciding the layout. `MenuRepository(variantRecognizers = …)`, `OcrService.variantRecognizers`. The lexicon corrector runs after the vote.
- **Measured** (`EvaluateVoting`, `EVAL_VOTING=1`, leave-one-week-out on the corpus, share of dish names the other weeks never produced): single 11.2%, lexicon only 9.4%, voting only 9.5%, voting + lexicon 8.9%. Most of the rest are genuinely new dishes.
- **On the Note 20:** first live load 7 s end to end (board, images, 9 OCR passes). The saved menu had 214 dishes and only a new dish (상추야채겉절이) outside the lexicon.
- Added `fixes` for 숭능/중늉 → 숭늉 and 간장돈육고기 → 간장돈육불고기 (the reader never gets 숭늉 right; a dropped syllable is out of the fixer's range).
- Ceiling: when all three readings make the same mistake (교자민두, 사송이버섯, 부추나울) neither voting nor the lexicon can fix it. Options left are manual `fixes` or a weekly shared file produced off-device.

### Original plan (kept for reference)

### Goal
The board keeps about 30 weeks of history: 2026-03-12 to 2026-10-16, about 60 posts and about 90 images. Pages past ~10 return the last page. Dishes recur and usually read correctly, so a frequency count of past dish names can replace an occasional misread with the most common spelling.

Chosen approach: **build the lexicon offline, commit it as `app/src/main/assets/dish-lexicon.json`, and correct dish names at runtime.** This is deterministic, reviewable and free at runtime. Refresh it once per semester, along with `schedule.json`.

### Why it's split this way
The slow device-only work (downloading and running OCR) happens **once**. Everything that needs tuning runs on the JVM and takes seconds.
1. **The device step saves raw OCR output, not a finished lexicon.** It runs OCR on each image with 3 preprocessing variants and saves the raw lines in the existing `*.ocr.json` fixture format. Changing the segmentation, thresholds or parser later never needs the phone.
2. **The JVM step builds the lexicon from that output.** It goes through `MenuTableParser`, so only real dish cells are counted. It also writes a review report and a hold-out check.
3. **The three variants vote.** Each image gets three readings (none, contrast, contrast+1.5×), so the correct spelling outvotes a misread even for dishes that appear only once or twice.
4. **Install and run by hand instead of using `connectedAndroidTest`,** because that task uninstalls the app and deletes the output. Use `installDebug installDebugAndroidTest`, then `adb shell am instrument`, then `adb pull`.

### 1. Shared preprocessing variants (`OcrService.kt`)
- Add `enum class Preprocess { NONE, CONTRAST, CONTRAST_UPSCALE }`.
- Change the signature to `recognize(image: ByteArray, preprocess: Preprocess = CONTRAST_UPSCALE)`. The default keeps production behaviour unchanged.
- Move the contrast and scale code into this enum so the corpus dump reuses it. Point `OcrQualityTest` at it too, which removes its duplicate helpers.

### 2. Device corpus dump (`androidTest/.../DishCorpusDump.kt`, new)
- Walk board pages 1 to 15 with the test's own OkHttp client and the base64 page token (`MenuRepository.PAGE_QUERY`). Stop when a page adds no new post URL. Collect posts with `BoardParser.eligiblePosts` and `BoardParser.medicalPosts`.
- For each post:
  - fetch the detail page;
  - read the image URLs with `BoardParser.menuImageUrls`;
  - download each image once and run `recognize` with each of the 3 variants;
  - write `corpus/<postDate>_<idx>_<imgN>_<variant>.ocr.json` as `{image, variant, postDate, lines:[{text, box}]}`. `box` is `[left, top, right, bottom]`, top-down and normalized, the same as the fixtures.
- Skip and log failures. Log the totals: posts, images, and OCR time.
- Expect about 90 images × 3 variants, about 270 OCR passes, taking roughly 5–8 minutes on the Note 20.
- Commands, from `android/`, with `JAVA_HOME` and `ANDROID_HOME` set:
  ```
  .\gradlew.bat :app:installDebug :app:installDebugAndroidTest
  adb shell am instrument -w -e class com.menuupdater.weeklymenu.DishCorpusDump com.menuupdater.weeklymenu.test/androidx.test.runner.AndroidJUnitRunner
  adb pull /sdcard/Android/data/com.menuupdater.weeklymenu/files/corpus ocr-corpus
  ```
- Put the output in `android/ocr-corpus/` and add that folder to `.gitignore`. It is about 2–3 MB, kept locally only, and rerun only to refresh the lexicon.

### 3. JVM lexicon build (`test/.../BuildDishLexicon.kt`, new)
- Runs only when the environment variable `BUILD_LEXICON=1` is set (checked with `Assume`), so normal test runs stay fast.
- For each corpus file:
  - convert boxes with `Rect.fromTopDown`, then run `MenuTableParser.parse`;
  - split each item with `DishSegments.split`;
  - count each segment once per image and variant.
- Writes `app/src/main/assets/dish-lexicon.json`, keeping segments with a count of 2 or more, sorted:
  ```json
  { "version": 1, "builtFrom": "2026-03-12..2026-10-16", "images": 90, "dishes": { "콩나물국": 41 } }
  ```
- Writes `android/build/lexicon-report.txt` (local only) with two sections:
  - **Mappings:** every distance-1 pair the corrector would rewrite, e.g. `공나물국 (3) → 콩나물국 (41)`. A person reviews this list.
  - **Hold-out:** builds the lexicon without the latest week, corrects that week's parse, and marks every change as right, wrong or unsure. Use it to tune the thresholds on data the lexicon hasn't seen.

### 4. Pure corrector (`domain/DishCorrector.kt`, new)
- `DishSegments.split(item)` splits on the separators `/ * & ,` and keeps them, so rejoining gives back the original exactly. Examples: `김치/샐러드/후식차`, `감자튀김*케찹`.
- `class DishCorrector(lexicon: Map<String, Int>)` has `correct(item: String): String` and `correct(table: MenuTable): MenuTable`. It is idempotent.
- Distance is Levenshtein on `Normalizer.normalize(s, NFD)`, which splits Hangul syllables into jamo, so 콩 and 공 differ by 1. The NFD form of each entry is computed once and bucketed by jamo length.
- **Rule for each segment:**
  1. Skip segments with fewer than 2 Hangul syllables. Words like 국 and 죽 are too risky to change.
  2. Find neighbours: lexicon entries within jamo distance 1, or 2 if the segment is 12 jamo or longer. Only compare length buckets within that distance.
  3. If the segment is **not** in the lexicon, replace it with the top neighbour when that neighbour's count is at least `MIN_COUNT` (3) and no other neighbour ties it.
  4. If the segment **is** in the lexicon with count `c`, replace it only when a neighbour's count is at least `RATIO` (5) × `c`. This catches misreads that themselves recur a little.
  5. Otherwise keep it. A new dish is never changed.
- Cost: about 600 entries × about 250 segments a week, with length buckets, comes in well under 50 ms. No index is needed.
- `companion fun fromJson(text): DishCorrector`. The thresholds are named constants and get tuned with the step 3 report.

### 5. Wiring
- `data/MenuRepository.kt`: add a parameter `corrector: DishCorrector? = null` and pass each parsed table in `fetchTables` through it. The parser itself doesn't use the lexicon, so the expectations in `MenuTableParserTest` stay the same.
- `ui/MenuViewModel.kt`: load `assets/dish-lexicon.json` next to `schedule.json` and pass it to the repository. Also correct the **cached** menu when it loads, so existing installs get the fixes without waiting for a refresh. This is safe because correction is idempotent.
- `android/PLAN.md`: note the approach and the commands, and rebuild the lexicon each semester along with `schedule.json`.

### 6. Tests (JVM, always run)
- `DishCorrectorTest`, using a small hand-written lexicon. It checks that:
  - 공나물국 becomes 콩나물국;
  - an exact match is kept;
  - an unknown dish with no neighbour is kept;
  - a tie between neighbours is kept;
  - a neighbour below `MIN_COUNT` is ignored;
  - an in-lexicon misread with a neighbour at least 5× as common is fixed;
  - a single-syllable segment is skipped;
  - compound items such as `공나물국/김치` and `감자튀김*케참` are corrected segment by segment, with separators kept;
  - running it twice gives the same result.
- `DishLexiconFixtureTest`: parse the 3 OCR fixtures from step 2 and correct them with the committed lexicon.
  - Assert that the known misreads are fixed: 공나물불고기, 상추걸절이, 장각삼계팅.
  - Assert that no dish already dominant in the lexicon changes.

### Order of work
This order needs only two device sessions.
1. Steps 1 and 4, plus `DishCorrectorTest`. JVM only.
2. Step 2: install, run the instrumentation, pull the corpus. **First device session.**
3. Step 3: build the lexicon, read the report, adjust thresholds or segmentation, rebuild. This loop runs on the JVM and takes seconds per round. Add the lexicon to the working tree.
4. Steps 5 and 6, then `testDebugUnitTest`. Everything should pass.
5. `assembleDebug`, install, `pm clear`, launch, and screenshot 이번 주 for all three cafeterias. **Second device session.**

### Verification
- `.\gradlew.bat :app:testDebugUnitTest` passes: the 31 existing tests plus the new ones.
- The hold-out section of `lexicon-report.txt` shows mostly correct fixes and no wrong ones. Spot-check the mappings list.
- On the device, 공나물, 걸절이 and 삼계팅 are corrected in all three cafeterias, with nothing new looking wrong compared with earlier screenshots.
- Report the totals (images and dishes), how long the corpus dump took, what still misreads (likely 숭늉), and anything that was only build-checked. Don't commit until asked.

## Tried and ruled out: on-device Gemini Nano (2026-10-25)
ML Kit GenAI Prompt API (`genai-prompt:1.0.0-beta4`, image input) on a Galaxy S25 Ultra (SM-S938N, Android 16, AICore installed): `checkStatus()` returned 0 (unavailable) and every request failed with `AiCore ... 601-BINDING_FAILURE: SecurityException`. Google's device list names Pixel 9/10 and Galaxy Z Fold7, not the S25 line. Not usable as a foundation. The probe test and dependency were removed.
