# Prompt tạo screenshot giới thiệu app trên Google Play

Bộ gồm **7 screenshot marketing**, sử dụng 9 ảnh UI thật trong thư mục này. Screenshot 01 ghép Home + Tools; screenshot 05 ghép Excel Edit + Excel Report. Bảng màu lấy từ app hiện tại: xanh `#246BFD`, nền `#F1F5FC`, chữ navy `#182544`, thẻ trắng bo tròn. Phong cách: sáng, thân thiện, rõ tính năng; điểm xuyết minh họa tài liệu 3D mềm giống icon ở màn Tools.

## Cách dùng

1. Mở một cuộc trò chuyện tạo ảnh mới, dán **Prompt chung** bên dưới.
2. Với mỗi ảnh, đính kèm đủ một hoặc hai file PNG được ghi ở mục tương ứng, rồi dán prompt của mục đó.
3. Từ ảnh thứ hai, đính kèm thêm ảnh marketing đầu tiên đã chốt làm mẫu phong cách. Nói rõ đâu là ảnh UI gốc và đâu là mẫu phong cách.
4. Dùng cùng canvas dọc **2400 × 3600 px** cho cả bộ. Tất cả PNG gốc hiện là **1080 × 2340 px**: đặt ở **100%, 1 pixel nguồn = 1 pixel canvas**, không scale kể cả thu nhỏ đồng đều. Canvas này đủ chỗ cho hai ảnh gốc cạnh nhau. Nếu công cụ không xuất đúng khổ, chỉ gen nền rồi ghép bằng Canva/Figma ở kích thước trên; không ép ảnh UI vào khổ nhỏ hơn.
5. Headline tiếng Anh bên dưới dành cho listing tiếng Anh. Nếu làm listing tiếng Việt, thay bằng bộ chữ ở bảng cuối và dùng ảnh UI tiếng Việt tương ứng.

**Cách giữ UI chính xác nhất:** tạo nền và đồ trang trí bằng AI, sau đó đặt PNG gốc và chữ bằng Canva/Figma. Lệnh yêu cầu giữ nguyên ảnh trong prompt giúp định hướng, nhưng công cụ tạo ảnh vẫn có thể vẽ sai chữ hoặc số liệu.

## Prompt chung — dán một lần đầu cuộc trò chuyện

```text
Create a cohesive series of exactly seven promotional screenshots for the Google Play listing of a document reader and creator Android app.

ART DIRECTION
Clean, friendly, modern productivity app marketing. Rounded shapes, white surfaces, generous spacing, restrained soft 3D document props. Make the actual app interface the hero. Use a rounded sans-serif similar to Nunito ExtraBold for headlines and Nunito Medium for subtitles.

PALETTE
Brand blue #246BFD, deep blue #1858DC, pale cool background #F1F5FC, white #FFFFFF, navy text #182544.
Feature accents: PDF coral #F04458, spreadsheet green #129B62, presentation orange #E76A19, warm yellow #FFC64A.
Pastel supporting colors: #EAF2FF, #DFF7ED, #FFF3C9.

SERIES LAYOUT
Portrait canvas, exactly 2400 × 3600 px. Full bleed, no outer border.
Top headline area: x=160–2240, y=140–650. Two short lines, bold, large and easy to read at thumbnail size. One subtitle below, at most two lines. Preserve the exact supplied text.
NATIVE-SIZE SCREEN PLACEMENT: every attached raw PNG is 1080 × 2340 px. Place each at exactly 1080 × 2340 canvas pixels, 100% scale. Never resize a screenshot to fit a frame.
Single-screen slides 02, 03, 04, 06, and 07: screen rectangle x=660–1740, y=930–3270.
Two-screen slides 01 and 05: left screen x=80–1160, y=820–3160; right screen x=1240–2320, y=1060–3400. Both complete screens must remain visible and separate. These slide-specific two-screen layouts override the single-screen layout.
PHONE GEOMETRY: build the device frame around the native screenshot, not the other way around. Screen aspect ratio must remain exactly 1080:2340 (6:13). Use a slim 12–18 px bezel outside the image, giving an outer device approximately 1104–1116 px wide and 2364–2376 px tall. This makes the phone about two thirds of the canvas height, with realistic tall Android proportions. No excessively wide, short, thick, or stretched phones. No fake notch, camera cutout, rounded mask, or border may cover source pixels. If a rounded device frame would mask source corners, use a thin rectangular screenshot card instead.
Keep all screens upright, front-facing, with no rotation or perspective. Use the same frame thickness, screen size, headline placement, font sizes, and lighting across the series. Keep shadows subtle and outside the source image.
Add at most two small soft 3D props outside the screenshot. Rounded white document shapes, simple feature-colored details, soft studio light from the upper left. Props must not obscure any app controls or screenshot content.
Keep the backgrounds light and quiet. Large translucent blue curves and pastel corner shapes may provide depth without competing with the UI.

SOURCE SCREENSHOT RULES
The attached raw screenshot is an existing app interface, not a sketch to reinterpret.
Preserve its content, aspect ratio, text, numbers, controls, icons, colors, selection handles, document content, and photo content.
NO SCALING: position the source PNG as a locked raster layer at its native 1080 × 2340 px size, 100% scale, with integer pixel coordinates. No enlargement, reduction, uniform scaling, nonuniform scaling, resampling, stretching, cropping, rotation, warping, perspective, or UI reflow. Do not redraw, invent, translate, clean up, recolor, or alter any source pixels. Do not remove existing status bars or dimmed areas. If exact pixel-preserving compositing is unavailable, generate the background only and leave the screen regions empty for manual compositing; never synthesize a replacement UI.
Distinguish the raw screenshot from any attached style reference: the style reference controls only the marketing design outside the source screenshot.

COPY RULES
Outside the source screenshot, include only the exact supplied headline and subtitle.
No extra captions, fake ratings, awards, review quotes, download counts, ranking claims, price claims, or third-party brand logos.
Do not add features absent from the source screenshot. No watermark or signature.
```

## Thứ tự xuất ảnh

| Thứ tự | Ảnh gốc đính kèm | Ảnh marketing đề xuất |
|---|---|---|
| 01 | `01_home.png` + `01_2_tools.png` | `01_all_documents.png` |
| 02 | `02_pdf_sign.png` | `02_sign_pdf.png` |
| 03 | `03_scan_img.png` | `03_scan_documents.png` |
| 04 | `04_word_edit.png` | `04_edit_word.png` |
| 05 | `05_excel_edit.png` + `05b_excel_report.png` | `05_spreadsheets_reports.png` |
| 06 | `06_ppt_edit.png` | `06_edit_slides.png` |
| 07 | `07_create.png` | `07_create_documents.png` |

## 01 — Tất cả tài liệu trong một app

Đính kèm cả hai: **`01_home.png`** và **`01_2_tools.png`**. Ghép thành một screenshot marketing.

```text
Create screenshot 01 of the series, following the shared art direction.
Mandatory: native source screenshots at 1080 × 2340 px, 100% scale, on a 2400 × 3600 px canvas. No resizing, stretching, cropping, perspective, or redrawing. Build a slim realistic phone frame outside each source image.
The two attached raw UI sources are 01_home.png and 01_2_tools.png. Use both in this ONE marketing image.

Exact headline, two lines:
"All your documents"
"in one app"
Exact subtitle:
"PDF, Word, Excel and PowerPoint"

Use a pale #F1F5FC background, navy headline, and brand-blue emphasis on the second headline line. Add a broad translucent light-blue curve behind the screenshot and one pale yellow corner accent.

Use the shared two-screen layout: complete Home screen on the left, complete Toolbox screen on the right, slightly lower. Both screens are upright, equally sized, fully visible, and do not overlap. Preserve the Home document categories, creation cards, and navigation. Preserve the Toolbox search, category chips, tool cards, and navigation. Do not merge their controls into a fabricated interface. The two screens together communicate document management and useful tools.

Outside the frame, place two small soft 3D props: a blue folded-corner document near the left edge and a compact stack of red, green, and orange document tabs near the lower right. No labels on the props. Keep the composition spacious and welcoming. This image establishes the visual template for the entire series.
```

## 02 — Ký PDF

Đính kèm: **`02_pdf_sign.png`**.

```text
Create screenshot 02 of the series using the established layout and style.
Mandatory: native source screenshots at 1080 × 2340 px, 100% scale, on a 2400 × 3600 px canvas. No resizing, stretching, cropping, perspective, or redrawing. Build a slim realistic phone frame outside each source image.
The attached raw UI source is 02_pdf_sign.png.

Exact headline, two lines:
"Your signature."
"Your PDF."
Exact subtitle:
"Add, move and resize your signature"

Use the standard pale cool background with a very soft coral shape at the upper right. Headline in navy, second line in brand blue.

Display the complete signature editor screenshot in the standard upright frame. Preserve the rental agreement, the signature, the purple selection handles, and all toolbar controls exactly. The selected signature is the main visual proof of the feature.

Add a small soft 3D blue pen outside the lower-right edge and a white folded-corner paper prop with a coral accent outside the left edge. Do not add a second signature or any new marks to the actual document.
```

## 03 — Scan tài liệu

Đính kèm: **`03_scan_img.png`**.

```text
Create screenshot 03 of the series using the established layout and style.
Mandatory: native source screenshots at 1080 × 2340 px, 100% scale, on a 2400 × 3600 px canvas. No resizing, stretching, cropping, perspective, or redrawing. Build a slim realistic phone frame outside each source image.
The attached raw UI source is 03_scan_img.png.

Exact headline, two lines:
"From paper"
"to PDF"
Exact subtitle:
"Scan pages and adjust the corners"

Use the standard pale cool background with a faint cyan-blue glow behind the frame. Navy headline with brand-blue emphasis on the second line.

Display the complete scan corner-adjustment screenshot in the standard upright frame. Keep the real notebook photo, blue crop polygon, four circular corner handles, Auto-detect control, and Looks good button exactly as supplied. Preserve the photographed notebook and desk; do not replace them with a pristine generated page. This is a corner-adjustment screen, not a live camera preview.

Outside the screenshot, add a small white paper prop surrounded by blue scan brackets near the left edge and a compact blue camera prop at the lower right. Do not add animated scan lines or OCR text inside the app UI.
```

## 04 — Chỉnh sửa Word

Đính kèm: **`04_word_edit.png`**.

```text
Create screenshot 04 of the series using the established layout and style.
Mandatory: native source screenshots at 1080 × 2340 px, 100% scale, on a 2400 × 3600 px canvas. No resizing, stretching, cropping, perspective, or redrawing. Build a slim realistic phone frame outside each source image.
The attached raw UI source is 04_word_edit.png.

Exact headline, two lines:
"Edit Word files"
"wherever you are"
Exact subtitle:
"Format text, lists and tables"

Use the standard pale cool background with soft blue curves. Navy headline with brand-blue emphasis on the second line.

Show the complete Word editor screenshot in the standard upright frame. Keep the selected heading and its blue handles, proposal document, table, Save button, and bottom formatting toolbar unchanged. Do not fill the empty workspace with invented content.

Outside the screenshot, add a small blue document prop with abstract white text bars on the left and a short yellow pencil on the lower right. No letters on the props. Let the real document and formatting controls communicate the feature.
```

## 05 — Chỉnh sửa bảng tính và xem báo cáo

Đính kèm cả hai: **`05_excel_edit.png`** và **`05b_excel_report.png`**. Ghép thành một screenshot marketing.

```text
Create screenshot 05 of the series using the established layout and style.
Mandatory: native source screenshots at 1080 × 2340 px, 100% scale, on a 2400 × 3600 px canvas. No resizing, stretching, cropping, perspective, or redrawing. Build a slim realistic phone frame outside each source image.
The two attached raw UI sources are 05_excel_edit.png and 05b_excel_report.png. Use both in this ONE marketing image.

Exact headline, two lines:
"Work with"
"spreadsheets"
Exact subtitle:
"Edit data and view charts"

Use the standard pale cool background with a restrained mint-green corner shape. Navy headline; use spreadsheet green #129B62 for the second line. Keep the frame, typography sizes, and spacing consistent with the other images.

Use the shared two-screen layout: complete spreadsheet editor on the left, complete report viewer on the right, slightly lower. Both screens are upright, equally sized, fully visible, and do not overlap. On the editor, preserve the sales table, selected D15 cell, formula bar, all values, sheet tabs, and formatting toolbar. On the report viewer, preserve the revenue/profit/margin summary, both charts and their labels, sheet tabs, and Edit button. Never generate substitute numbers, formulas, or charts. Keep the screens as two distinct app views; do not combine their controls into an invented interface. Demonstrate editing data and viewing an existing report, without implying AI analysis or chart creation.

Add a small green rounded grid tile outside the left edge and a white folded-corner spreadsheet prop outside the lower right. Keep all real cells unobstructed. Do not imply automatic analysis or AI calculations.
```

## 06 — Chỉnh sửa PowerPoint

Đính kèm: **`06_ppt_edit.png`**.

```text
Create screenshot 06 of the series using the established layout and style.
Mandatory: native source screenshots at 1080 × 2340 px, 100% scale, on a 2400 × 3600 px canvas. No resizing, stretching, cropping, perspective, or redrawing. Build a slim realistic phone frame outside each source image.
The attached raw UI source is 06_ppt_edit.png.

Exact headline, two lines:
"Polish your slides"
"on the go"
Exact subtitle:
"Edit text, reorder slides and present"

Use the standard pale cool background with a restrained pale orange corner shape. Navy headline; use presentation orange #E76A19 for the second line. Keep the frame, typography sizes, and spacing consistent with the other images.

Show the complete PowerPoint editor screenshot in the standard upright frame. Keep the orange title slide with the selected "Product Launch 2027" text box and its blue handles, the slide strip with four numbered slides and the grid button, the Play, Undo, Redo and Save controls, and the bottom ribbon exactly as supplied. Do not fill the empty workspace above or below the slide with invented content.

Outside the screenshot, add a small white presentation board prop with an orange bar chart near the left edge and a compact stack of three slide cards with orange, blue and green edges at the lower right. No labels on the props.
```

## 07 — Tạo tài liệu mới

Đính kèm: **`07_create.png`**.

```text
Create screenshot 07 of the series using the established layout and style.
Mandatory: native source screenshots at 1080 × 2340 px, 100% scale, on a 2400 × 3600 px canvas. No resizing, stretching, cropping, perspective, or redrawing. Build a slim realistic phone frame outside each source image.
The attached raw UI source is 07_create.png.

Exact headline, two lines:
"Start something"
"new"
Exact subtitle:
"Create documents, sheets and slides"

Use the standard pale cool background with a soft blue curve and pale orange corner accent. Navy headline with brand-blue emphasis on the second line.

Show the complete creation-menu screenshot in the standard upright frame. The bottom sheet titled What shall we make? is the main content. Preserve the dimmed home screen behind it, the status bar, scan and photo options, and the Word, Excel, and PowerPoint creation cards exactly. The dimmed area is part of the screenshot and must not be brightened independently.

Outside the frame, add a small rounded blue plus badge on the left and three stacked white document sheets with subtle blue, green, and orange edge accents at the lower right. No labels or additional logos on these props.
```

## Khi AI vẽ sai UI: tạo nền riêng rồi ghép

Dùng prompt của ảnh cần làm, thêm đoạn sau ở cuối để thay thế yêu cầu chèn screenshot và chữ:

```text
BACKGROUND-ONLY OVERRIDE:
Generate only the full-bleed marketing background and the small decorative props for this slide. Do not render any text, phone, frame, screenshot, interface, placeholder text, or fake document content.
Output canvas: 2400 × 3600 px.
Keep the headline region x=160–2240, y=140–650 empty and quiet.
For single-screen slides 02, 03, 04, 06, and 07, reserve x=630–1770, y=900–3300 for the screenshot and external frame.
For two-screen slides 01 and 05, reserve x=50–1190, y=790–3190 and x=1210–2350, y=1030–3430 for the two screenshots and external frames.
Place decorative props outside these two reserved regions. I will composite the actual screenshot, frame, and headline manually afterward.
```

Trong Canva/Figma: tạo canvas **2400 × 3600 px** → đặt nền → thêm headline/subtitle bằng Nunito → nhập PNG gốc, đặt **W=1080, H=2340, scale=100%**, khóa layer. Ảnh đơn đặt tại **x=660, y=930**; ảnh đôi đặt tại **x=80, y=820** và **x=1240, y=1060**. Không dùng Fit/Fill, không dùng mask cắt góc, không resize PNG. Vẽ viền điện thoại bên ngoài ảnh, dày 12–18 px; không phủ lên UI. Xuất PNG hoặc JPEG nền kín ở **2400 × 3600 px**, không resize bản xuất. Đối chiếu chữ, số liệu và toolbar với ảnh gốc.

## Bộ chữ tiếng Việt tùy chọn

Các dấu `/` dưới đây chỉ vị trí xuống dòng, không đưa dấu `/` vào ảnh. Đổi sang ảnh UI tiếng Việt nếu muốn bộ screenshot đồng nhất ngôn ngữ.

| Ảnh | Headline | Subtitle |
|---|---|---|
| 01 | Mọi tài liệu / trong một ứng dụng | PDF, Word, Excel và PowerPoint |
| 02 | Thêm chữ ký / vào PDF | Di chuyển và đổi kích thước chữ ký |
| 03 | Từ trang giấy / thành PDF | Scan tài liệu và căn chỉnh góc trang |
| 04 | Sửa file Word / ở bất cứ đâu | Định dạng văn bản, danh sách và bảng |
| 05 | Làm việc / với bảng tính | Sửa dữ liệu và xem biểu đồ |
| 06 | Chỉnh slide / mọi lúc mọi nơi | Sửa chữ, sắp xếp slide và trình chiếu |
| 07 | Bắt đầu / tài liệu mới | Tạo văn bản, bảng tính và bài trình chiếu |

## Prompt sửa nhanh

**Đồng bộ bộ ảnh:**
```text
Match the first approved marketing image: same headline placement and size, frame, lighting, background brightness, and prop material. Keep the required two-screen layout for slides 01 and 05 and the single-screen layout for all other slides. Preserve this slide's exact copy and raw screenshot. Change nothing else.
```

**UI bị thay đổi:**
```text
The app screenshot has been altered. Replace each altered screen with its attached original PNG at exactly 1080 × 2340 px, 100% scale, on the 2400 × 3600 px canvas. Preserve every source pixel. Resize only the external frame if needed; never resize, crop, or redraw the screenshot. Change nothing else.
```

**Đồ trang trí che nội dung:**
```text
Move all decorative props completely outside the screenshot and headline regions. Keep the complete original app screenshot visible. Change nothing else.
```

**Sai chữ quảng cáo:**
```text
Replace only the marketing headline and subtitle with the exact supplied text, preserving spelling, punctuation, and line breaks. Do not modify any text inside the original app screenshot. Change nothing else.
```

**Điện thoại sai kích thước hoặc ảnh bị scale:**
```text
Restore each source screenshot to exactly 1080 × 2340 px at 100% scale on the 2400 × 3600 px canvas. Keep the original 6:13 screen aspect ratio. Build a slim 12–18 px external bezel around it. Use the specified single-screen or two-screen coordinates. Do not resize the source image to fit a device mockup. Change nothing else.
```
