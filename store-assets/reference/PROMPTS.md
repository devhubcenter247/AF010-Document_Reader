# Folio — prompt ChatGPT cho icon, onboarding và ảnh store

Style lấy theo bộ mẫu `~/Downloads/sc_pdf_1..6.png` (ảnh store) và ảnh onboarding của AF06
(`AF06-DocReaderCreator/app/src/main/res/drawable/img_onboard_1..4.webp`): đồ vật 3D bóng kiểu
clay, nền gradient có mảng sóng mờ, headline hai dòng với dòng thứ hai nằm trong pill trắng.
Màu đổi sang màu Folio (xanh #1F6FEB thay cho tím của mẫu).

Ảnh chụp thô: `store-assets/raw/` (1080x2340, chụp bằng `capture.sh`). Màn 05 (Scan) cần camera thật:
chạy `./capture.sh 05`, đặt tờ giấy dưới máy, khi viền xanh bám đúng mép giấy thì bấm Enter.
Script lưu `05a_scan_camera.png` (màn camera, giống mẫu `sc_pdf_3`; dùng ảnh này),
`05b_scan_crop.png` và `05_scan_filters.png`.

Prompt riêng cho mascot vector trong app: `mascot_prompt.md`.

## Cách dùng

- Mỗi nhóm (icon / onboarding / store) làm trong **một chat riêng**, dán Prompt 0 trước, làm hết
  nhóm trong chat đó để các ảnh cùng ánh sáng, cùng chất liệu.
- Đính kèm 1-2 ảnh mẫu style (`sc_pdf_*.png` cho store, `img_onboard_*.webp` cho onboarding) ở
  lượt đầu. Ảnh đầu tiên đạt của nhóm thành **ảnh mẫu** cho các lượt sau.
- ChatGPT chỉ xuất 1024x1024, 1024x1536, 1536x1024. Khổ khác thì xuất khổ gần nhất rồi tự cắt
  (xem từng phần).
- Ảnh nào đạt thì tải ngay. Sửa mỗi lần một lỗi bằng các câu ở cuối file, luôn kết thúc bằng
  "Change nothing else."

---

## Prompt 0: Luật chung (dán đầu mỗi chat mới)

```
You are making production art for "Folio", an Android app that opens, edits and creates
PDF, Word, Excel and PowerPoint files and scans paper. Follow these rules for every image
in this chat. They override anything that conflicts later.

STYLE (match the attached style references)
- Objects are soft 3D renders: rounded, glossy clay/plastic, smooth studio lighting from
  the top-left, gentle rim light, soft contact shadows, a faint coloured glow under each
  object. Clean and premium, like modern app-store art.
- Few objects, generous empty space. Draw only what I list. If unsure, leave it out.
- No photo-real people, hands or rooms. No noise, grain or lens effects.

COLOURS (use these hues for objects and backgrounds)
brand blue #1F6FEB · deep blue #1858C4 · light blue #5B9BFF · page grey #F2F4F8 ·
white #FFFFFF · ink #1F2433 · PDF red #DC2F2F · Word blue #1F6FEB ·
Excel green #128A3C · PowerPoint orange #C9520A · teal #0E7F94 · amber #E8A317 ·
blush #FFB3C1

TEXT
- No text, letters, numbers or logos anywhere, except the exact words I give in quotes.
  Spell them exactly, keep their capitalisation, never repeat them elsewhere.

ATTACHED APP SCREENSHOTS ARE LOCKED
- Do not redraw, retype, translate, recolour, crop, stretch or add perspective.
  Every word, icon and colour inside the screenshot stays exactly as attached.
- Allowed: a uniform resize, and rotating the whole phone by the small angle I give.

OUTPUT
- Exactly the size I ask. No border, watermark or signature.
- If you cannot follow a rule, say which one instead of generating.

Reply "OK" and wait for my first request.
```

---

## 1. Icon app (1024x1024)

Đính kèm `reference/current_icon.png` + 1 ảnh `sc_pdf_*.png` làm mẫu chất liệu.

```
Attached: our current flat icon, and a style reference. Turn the icon into the final
launcher icon in the reference's soft 3D style.
1024x1024, square, full-bleed, no rounded corners, no transparency.

Background: smooth gradient #1F6FEB (bottom-left) to #5B9BFF (top-right), with one large,
soft, lighter wave shape behind the centre.

Centre: the mascot from the attachment as a glossy 3D object: a white sheet of paper with
rounded corners and a folded top-right corner (light grey-blue fold), thick #1858C4 rim,
same face (closed happy curved eyes, open smile, pink #FFB3C1 cheeks), tilted about
-6 degrees, about 55% of the canvas height. It is the focal point.

Behind it, peeking out left and right, two smaller 3D document cards fanned like playing
cards: left one Excel green with a white 2x2 grid, right one PDF red with two white lines.
A chunky glossy amber #E8A317 pencil leans on the mascot's lower-right corner.

Keep everything inside the central 66% circle (adaptive-icon safe zone). Bold shapes,
readable at 48x48 px. 4 objects in total. No text.
```

Khi đã ưng:
```
Same icon, foreground only (mascot, cards, pencil) on a transparent background,
1024x1024 PNG. Identical positions, sizes and lighting, nothing redrawn.
```
```
Same icon as a flat single-colour silhouette: all foreground shapes solid white on a
transparent background, eyes and smile as cut-outs, no shading. 1024x1024 PNG.
```
Dùng: bản full-bleed resize 512x512 cho Play Console; lớp foreground cho adaptive icon (nền
#1F6FEB); bản trắng một màu cho themed icon (`ic_launcher_folio_mono`).

---

## 2. Bốn ảnh onboarding (full màn hình)

Giống AF06: ảnh phủ kín màn hình, nửa trên là minh hoạ 3D trên nền gradient nhạt, nửa dưới
trơn để app đặt tiêu đề, mô tả và nút Next (app có 45 ngôn ngữ nên ảnh **không có chữ**).

Đính kèm icon đã chốt + 1 ảnh `img_onboard_*.webp` của AF06 làm mẫu style (từ slide 2: thêm
slide 1 đã đạt).

Câu mở đầu, dán trước **mỗi** slide:
```
Attached: the final app icon and a style reference. Make a FULL-SCREEN PHONE BACKGROUND
for an onboarding screen, in exactly the reference's soft 3D style.
Tall portrait (1024x1536). It is a wallpaper that fills the whole phone screen, NOT a card,
poster or sticker: no rounded corners, no frame, no border, no shadow around the image,
no vignette, no white margin. The background runs to all four edges.

Composition:
- The illustration sits in the TOP HALF only, large, filling most of it. Its lowest point
  (floor shadow included) is above the middle of the image.
- The BOTTOM HALF is plain empty background: no objects, shadows or shapes. Text and a
  button will be placed there.

Background: very pale blue #DCE7FF at the top, blending into #F2F4F8 by the middle, then
flat #F2F4F8 to the bottom. A faint glow of the slide colour I give behind the scene.
No text, letters or numbers. No phone UI text. No page dots or buttons.
Same camera angle, lighting, mascot size and scene size on every slide.
```

Nếu kết quả chưa full màn hoặc cảnh còn ở giữa ảnh, gõ:
```
Make it a full-bleed phone wallpaper: no rounded corners, frame or margin, background to
every edge. Move the scene up into the top half and make it bigger; keep the bottom half
plain #F2F4F8. Change nothing else.
```

**Slide 1 – Open anything** ("Every document, one cosy home") — slide colour #1F6FEB
```
A glossy white 3D phone, slightly tilted, its screen showing 4 abstract file rows
(a coloured file glyph and two grey bars each: red, blue, green, orange).
Floating around it, larger glossy 3D file icons with folded corners: PDF red (top-left),
Excel green with a white grid (top-right), Word blue with white lines (left),
PowerPoint orange with a white pie glyph (right). At the bottom-right, the Folio mascot
from the icon, small, peeking from behind the phone and smiling. 6 objects in total.
```

**Slide 2 – Edit & sign** ("Sign it from the sofa") — slide colour #DC2F2F
```
A large white 3D document page, slightly tilted, with grey text bars, one amber
highlighter stroke over a line, a small checked checkbox, and a deep-blue ink signature
loop on a signature line (abstract, not readable letters). Above it, a floating white
toolbar pill with 4 glossy tool glyphs (text cursor, highlighter, note, pen).
Around it: a glossy #1F6FEB pen at the right, an amber highlighter at the bottom-left,
a small yellow note bubble, a green round check badge at the bottom-right.
7 objects in total.
```

**Slide 3 – Scan** ("Paper to PDF in a snap") — slide colour #0E7F94
```
A glossy white 3D phone in the centre; on its screen, a paper page framed by four
#1F6FEB corner brackets with a thin glowing scan line across it. At the left, a plain
paper sheet with a curved arrow going into the phone. At the right, a glossy PDF-red
page with a white page glyph and a small magnifier, with a curved arrow coming out of
the phone. 5 objects in total.
```

**Slide 4 – Handy tools** ("A toolbox for every little job") — slide colour #E8A317
```
In the centre, a large glossy PDF-red 3D document with white lines. Around it, six round
white 3D badges joined to it by thin dotted lines, each with one glossy glyph:
merge (two pages joining, blue), split (page with scissors, orange),
compress (arrows pointing inward, green), protect (page with a gold padlock, blue),
translate (globe with two speech bubbles, teal), scan text (page inside corner brackets,
amber). Badges same size, evenly spaced, not touching. 7 objects in total.
```

---

## 3. Ảnh store Google Play (1024x1536)

Mỗi ảnh đính kèm: 1 ảnh mẫu style (`sc_pdf_1.png`, hoặc ảnh store đầu tiên đã đạt) + đúng 1 ảnh
trong `raw/`.

Câu mở đầu, dán trước **mỗi** ảnh:
```
Make a Google Play phone screenshot, 1024x1536 portrait, in exactly the attached style
reference's layout and finish.

- Background: vertical gradient #1F6FEB (top) to #1858C4 (bottom) with 3-4 very large,
  soft, translucent lighter wave shapes curling in from the sides.
- Headline block, top 20% of the canvas, centred:
  line 1 = LINE1, bold heavy rounded sans-serif, white.
  line 2 = PILL, same font in #1F6FEB, inside a rounded white pill at 90% opacity.
  under it = SUB, white, medium weight, one line.
  Use exactly the quoted words below, each once. Nothing else is written anywhere.
- Below: the attached app screenshot inside a thin black modern phone frame with a
  punch-hole camera, centred, about 74% of the canvas height, its bottom about 40 px
  above the canvas bottom, rotated by ANGLE. The screenshot is LOCKED.
- 2 or 3 glossy 3D props (listed below) half-overlapping the phone's left or right edge,
  plus 2-3 small white sparkle ticks. Props never cover the headline or the middle of
  the phone screen.
```

Sau đó thêm đúng 1 khối:

```
01 – attach raw/01_home.png
LINE1 "All your documents"  PILL "in one app"
SUB "PDF, Word, Excel and PowerPoint"   ANGLE 0 degrees
Props: four glossy 3D file icons with folded corners, each with ONE quoted label:
red "PDF" (upper-left), blue "DOC" (upper-right), green "XLS" (lower-left),
orange "PPT" (lower-right).
```
```
02 – attach raw/02_pdf_sign.png
LINE1 "Sign PDFs"  PILL "in seconds"
SUB "Draw your signature, place it anywhere"   ANGLE -5 degrees
Props: a glossy #1F6FEB fountain pen at the lower-right with a glowing ink swirl,
a green round check badge at the middle-left.
```
```
03 – attach raw/03_word_edit.png
LINE1 "Edit Word files"  PILL "on the go"
SUB "Fonts, styles, lists and tables"   ANGLE 0 degrees
Props: a glossy blue 3D document with white lines at the upper-left,
an amber pencil at the lower-right.
```
```
04 – attach raw/04_excel_edit.png
LINE1 "Excel sheets"  PILL "made easy"
SUB "Cells, formulas and formatting"   ANGLE 5 degrees
Props: a glossy green 3D grid cube at the lower-left, an orange-and-pink 3D pie chart at
the lower-right.
```
```
05 – attach raw/05a_scan_camera.png
LINE1 "Scan paper"  PILL "to PDF"
SUB "Auto edges and clean filters"   ANGLE 0 degrees
Props: a white 3D page with #1F6FEB corner brackets at the upper-left,
a glossy white 3D camera with a blue lens at the lower-right.
```
```
06 – attach raw/06_create.png
LINE1 "Create new files"  PILL "in one tap"
SUB "Word, Excel, PowerPoint and scans"   ANGLE -5 degrees
Props: a glossy blue round "+" badge at the upper-right, a small stack of white 3D
pages at the left, the Folio mascot from the icon in 3D, cheering, at the lower-right.
```

Ảnh 07 (tuỳ chọn, như `sc_pdf_5`): chụp thêm tab Tools thành `raw/07_tools.png` rồi dùng
`LINE1 "All the PDF tools"  PILL "you need"  SUB "Merge, split, compress, protect, translate"
ANGLE 0 degrees`, props: một ổ khoá 3D xanh lá bên phải, cây kéo 3D cam góc dưới phải, chồng
trang có mũi tên bên trái.

### Feature graphic (1024x500)

Đính kèm icon đã chốt + `sc_pdf_1.png`. Xuất 1536x1024, cắt giữa lấy dải 1536x750
(y 137–887), resize về 1024x500.

```
1536x1024 landscape banner in the attached reference's style. Keep every element inside
the middle band y 160-864; above and below is plain background that will be cropped.
Background: gradient #1F6FEB to #1858C4 with soft translucent wave shapes.
Left half: the Folio mascot from the icon as a large glossy 3D object, with four smaller
3D file icons around it (red, blue, green, orange, no labels).
Right half, vertically centred, left-aligned: "Folio" in bold heavy rounded white
sans-serif; under it "Read, edit and create documents" in white, medium weight.
No other text, no phone.
```

---

## Câu sửa nhanh

| Lỗi | Gõ |
|---|---|
| Chữ/icon trong màn hình app bị vẽ lại, méo | `The phone screen was redrawn. Place the attached screenshot exactly as-is, only scaled and rotated. Change nothing else.` |
| Ảnh chụp bị kéo giãn, cắt, có phối cảnh | `Keep the screenshot's exact 1080:2340 ratio, flat (no perspective), uncropped. Change nothing else.` |
| Chữ trong pill lặp lại dòng 1 | `Line 1 must read only "<LINE1>" and the pill only "<PILL>". Change nothing else.` |
| Sai chính tả / thêm chữ | `Text must be exactly "<chữ đúng>" and nothing else anywhere. Change nothing else.` |
| Props che màn hình hoặc headline | `Move the props outward so they only overlap the phone's edge. Change nothing else.` |
| Ảnh rối | `Too busy. Remove the smallest prop and add empty space. Change nothing else.` |
| Lệch style | `Match the attached reference's 3D material, lighting and colours exactly. Change nothing else.` |
| Mascot khác lần trước | `Make the mascot identical in shape, face and colours to the attached icon. Change nothing else.` |
| Icon quá nhiều chi tiết ở 48 px | `Simplify: bigger shapes, remove the smallest detail. Change nothing else.` |
| Nền foreground icon không trong suốt | `Same image with a truly transparent background (PNG alpha), no white or checkerboard. Change nothing else.` |

## Nếu ChatGPT vẫn vẽ lại màn hình app

Bộ mẫu `sc_pdf_*` cho thấy ChatGPT thường vẽ lại chữ trong màn hình (chữ nghiêng, lệch). Cách
chắc chắn: thay câu về điện thoại bằng
`inside the phone frame, fill the screen area with solid #00FF00, nothing on it`, rồi dán
`raw/0X_*.png` vào vùng xanh trong Canva/Figma/Photoshop. Trước khi đăng, so từng ảnh với ảnh
gốc trong `raw/`.

## Kiểm trước khi đăng

- Icon: 512x512 PNG, không trong suốt, ≤ 1 MB; nhìn rõ ở 48 px; không chữ.
- Ảnh store: 1024x1536 (2:3, Play chấp nhận), PNG/JPEG không alpha; LINE1/PILL/SUB đúng chính
  tả, không lặp; màn hình app không bị đổi.
- Onboarding: ảnh tràn hết bốn mép (không bo góc, không lề); cảnh ở nửa trên, nửa dưới trơn
  #F2F4F8; cùng góc máy, cùng ánh sáng ở cả 4 slide.
- Feature graphic: đúng 1024x500, chỉ có chữ "Folio" và tagline.
