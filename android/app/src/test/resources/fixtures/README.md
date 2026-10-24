# Test fixtures

Captured from the live site on 2026-10-23/24 for the Android port. Source:
https://www.agoodplacefnb.com/today (Imweb board `t=board`).

## Board / detail HTML (repository tests — step 4)
- `board_page1.html` — board list page 1. Rows `ul.li_body`, title `a.list_text_title`,
  date in `li.time title="YYYY-MM-DD HH:MM"`. Post links: `/today/?q=<base64>&bmode=view&idx=<idx>&t=board`.
- `detail_university_175120835.html` — newest "순천향대학교 식단" post (2026-10-16).
- `detail_medical_175120850.html` — newest "순천향대학교 의과대학 식단" post (2026-10-16).

Post selection matches iOS: newest title containing `순천향대학교` and NOT `의과대학`, plus
newest containing `의과대학`. Menu images are `cdn.imweb.me/upload/.../*.png`; the shared
`20a4b9b3cebc9.png` (500×500) is the site logo and the `.ico` is the favicon — both excluded.

## Menu images (OCR inputs — in `../../../androidTest/assets/menu_images/`)
Week of 2026-10-19..23, all 1920×~1355 PNG.
- `hyangseol1.png` — 향설1관 주간메뉴표 (조식/중식/석식; shared rows 아산쌀밥/김치/샐러드 etc.).
- `faculty.png` — 교직원식당 주간메뉴표 (중식 only).
- `medical.png` — 순천향의대식당 주간메뉴표 (중식 only).

## OCR output (parser tests — step 3) — `ocr/*.ocr.json`
ML Kit Korean (bundled `com.google.mlkit:text-recognition-korean`) run on a physical
Galaxy Note 20 Ultra (Android 13). Each file: image size, `elapsedMs`, `lineCount`,
`fullText`, and `lines[]` with `text` + `box` = `[left, top, right, bottom]` normalized 0..1.
Regenerate via `OcrSpikeTest` (androidTest).

### Spike findings (step 2)
- **On-device OCR is sub-second per image** (향설1관 597 ms / 교직원 335 ms / 의대 309 ms;
  ~1.5 s total incl. model init). Far faster than the iOS simulator's 19–63 s.
- **Accuracy is high** — all five date headers correct; dish misreads are the same minor
  class the iOS app already tolerates (주간**메**뉴표→주간뉴표, 겉절이→걸절이, 콩나물→공나물).
- **ML Kit block/line order is NOT reliable top-to-bottom** (the header and footer lines
  land mid-text), so the parser must reconstruct the table from the bounding boxes — same
  geometry approach as the iOS `MenuTableParser`.
- **Model choice: bundled**, not the unbundled `play-services-mlkit-text-recognition-korean`.
  Bundled works offline on first run (no Play-Services model download, no "downloading"
  UI state), which matches the iOS no-wait UX; the APK-size cost is acceptable. The
  port_plan "downloadable model" wording was a soft preference, not a hard requirement.
