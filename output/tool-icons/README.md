# Tool icon set

Generated with the built-in image_gen tool. Final Android assets are in
`app/src/main/res/drawable/ic_tool_*.webp`: 256 × 256, quality 90, alpha quality 100.
Open `preview.html` to review the set on white cards. The app's existing icon bindings
are unchanged. `manifest.json` records the selected original PNG paths.

## Prompt set

Shared generation direction: standalone miniature 3D app icons, rounded molded
plastic / clay forms, orthographic three-quarter perspective, vivid fresh colors,
white paper, subtle local shading, centered on a square transparent canvas with
generous padding, legible at 64dp. No background panel, border, watermark or scene.

| Resource | Subject |
| --- | --- |
| ic_tool_scan | Blue scanner, white sheet, cyan scan corners |
| ic_tool_images_to_pdf | Photo cards and red PDF document |
| ic_tool_office_to_pdf | Word, Excel and Slides documents converting to PDF |
| ic_tool_pdf_to_images | PDF, orange arrow, landscape photo |
| ic_tool_sign | Blue toy pen, white paper, blue signature stroke |
| ic_tool_fill_form | White form, blue fields and checks, blue toy pen |
| ic_tool_annotate | Orange highlighter, white paper, yellow highlighted lines |
| ic_tool_watermark | White document, translucent red stamp mark, red toy stamp |
| ic_tool_merge | White sheets with red corners merging together |
| ic_tool_split | Document halves, orange cut line and arrows |
| ic_tool_organize | Numbered pages 1, 2, 3 and blue reorder arrow |
| ic_tool_compress | White document, two emerald arrows pointing inward |
| ic_tool_protect | Emerald lock and white document |
| ic_tool_redact | Purple privacy bars on a white document |
| ic_tool_ocr | White document, orange OCR letters and scan corners |
| ic_tool_translate | A / 文 document pair and cyan translation arrows |
| ic_tool_zip | Golden folder with a silver zipper |

For sign, annotate, fill_form, watermark and OCR, the final generation prompt
explicitly excludes photography, desks, hands, full-frame paper and background
scenery; it requests an isolated toy-like icon with transparent corners and margins.

When binding these illustrations, clear any ImageView tint and colored background;
use `fitCenter` with no extra icon padding. They are full-color bitmap assets.
