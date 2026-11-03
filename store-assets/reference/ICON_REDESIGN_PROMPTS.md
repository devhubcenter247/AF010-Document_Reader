# Prompt làm lại icon All Document Reader & Editor

## 1. Đánh giá ảnh tham khảo và icon đang dùng

Đã xem ảnh tìm kiếm Google Play bạn gửi: `~/Downloads/Đã dán 02-11-2026 lúc 04:28:46.png`.

- Các icon trong ảnh thường chia bốn vùng đỏ, xanh dương, xanh lá, cam, tương ứng PDF, Word, Excel, PowerPoint. Cách này giúp người xem đoán nhanh công dụng mở nhiều định dạng tài liệu.
- Nhiều mẫu có bố cục gần giống nhau. Làm lại nguyên bốn ô và chữ W/X/P sẽ dễ bị lẫn trong danh sách. Đây là nhận xét thị giác từ ảnh, chưa có dữ liệu chứng minh mẫu nào có tỷ lệ cài đặt cao hơn.
- Nhãn năm và nhiều chữ nhỏ làm icon chật. Nên ưu tiên hình tài liệu lớn, tương phản mạnh, ít chi tiết.

**Icon launcher thực tế:** `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`, foreground tại `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp`.

- Đang có chồng trang nhiều màu, trang trắng phía trước, bút vàng, bốn góc scan, nền xanh nhạt và các vòng sáng.
- Điểm tốt: thể hiện nhiều định dạng, có chỉnh sửa và scan; màu sắc nổi bật.
- Điểm cần sửa: bút, khung scan, các trang phía sau và quầng sáng cùng tranh sự chú ý. Khi thu nhỏ, trang trắng và bút có thể được thấy trước ý nghĩa “đọc mọi tài liệu”. Nền sáng khiến cạnh trang trắng kém tách bạch.
- Hướng nên thử đầu tiên: giữ chồng trang và bộ màu quen thuộc, bỏ vòng sáng, bỏ khung scan, bỏ bút hoặc giảm bút thành điểm nhấn nhỏ; dùng nền xanh đậm hơn.

**Một hướng nhận diện khác có sẵn:** `store-assets/reference/current_icon.png` là mascot tờ giấy trắng cười trên nền xanh, không phải icon launcher hiện đang dùng. Mascot dễ tạo bản sắc riêng nhưng chưa nói rõ khả năng mở nhiều định dạng. Prompt 07 kết hợp hai ý này.

## 2. Cách tạo một loạt để chọn

Các prompt bên dưới độc lập, viết bằng tiếng Anh để tiện dán vào công cụ tạo ảnh. Mỗi lượt tạo **một icon**, không ghép nhiều icon vào một ảnh.

1. Tạo 4 biến thể cho mỗi prompt 01–08: tổng 32 mẫu. Lưu tên `01-A`, `01-B`… `08-D`.
2. Prompt 01, 02, 06: đính kèm **foreground launcher hiện tại** để công cụ hiểu cấu trúc gốc. Prompt 07: đính kèm **mascot current_icon.png**. Các prompt khác có thể chạy không cần ảnh.
3. Nếu đính kèm ảnh Google Play, ghi rõ đây là tham khảo về khả năng nhận biết loại tài liệu; không sao chép chính xác icon nào trong ảnh.
4. So sánh trên cả nền sáng và tối, ở 512, 48 và 32 px. Tạo bảng tổng hợp sau khi đã xuất từng icon riêng.
5. Chọn ba mẫu tốt nhất rồi thử biến thể màu hoặc chất liệu. Mỗi lần chỉ đổi một yếu tố để dễ so sánh.

Các yêu cầu kích thước dưới đây là **brief tạo ảnh**, chưa phải bộ tài nguyên Android sẵn để cài. Bản adaptive cần tách lớp và căn lại sau khi chọn mẫu.

## 3. Các prompt độc lập

### 01 — Làm gọn icon hiện tại • nên thử trước

Giữ khả năng nhận ra app, giảm các chi tiết gây rối.

```text
Redesign the attached current launcher artwork for All Document Reader & Editor, an all-document reader and editor. Preserve the recognizable fan of colorful document cards, but simplify its visual hierarchy dramatically.

Create ONE finished app icon, square 1024x1024, opaque, edge-to-edge background, no outer frame, no pre-rounded canvas corners, no device mockup.

Background: rich royal blue #165DDE with a subtle brighter center. Main symbol: one large white document in front, a folded upper-right corner, and three bold blue horizontal content lines. Behind it, exactly four partially visible document cards: red at upper-left, green at left, orange at upper-right, blue at right. Give the visible colored tabs simple white abstract glyphs: short lines, a spreadsheet grid, a pie chart, and paragraph lines. All cards must read as one compact unified fan.

Use restrained soft 3D depth, crisp silhouettes, smooth matte surfaces and one subtle contact shadow. The complete symbol occupies about 70% of the image width. Keep all meaningful details within the central 64% of the canvas width and height so the outermost decorative edges can tolerate cropping.

No pencil, scan brackets, orbital lights, sparkles, extra purple card, letters, numbers, brand logos, watermark or tiny decorative details. Clear and recognizable at 48x48 pixels.
```

### 02 — Chồng tài liệu phẳng • rõ khi thu nhỏ

```text
Design ONE distinctive launcher icon for an Android all-document reader named All Document Reader & Editor. Use the attached current icon only as a reference for the idea of multiple document formats; rebuild it in a clean flat geometric style.

Square 1024x1024 image, opaque full-bleed deep blue #1254CB background. A large upright white page with a folded upper-right corner sits in the center. Behind it, three broad document tabs fan outward: red to the upper-left, green to the left, orange to the right. The foreground page has three thick blue horizontal lines; the green tab has a simple white 2x2 grid; the orange tab has a single white chart wedge; the red tab has one short white horizontal bar.

Bold shapes, precise geometry, balanced optical spacing, minimal flat shading, no outlines thinner than the page fold. The overall symbol occupies 68% of the canvas width, with the core content inside the central 64% square. Optimize for legibility at 32 and 48 pixels.

No pencil, scan brackets, face, glow, gradients on the document shapes, small text, letters, numbers, recognizable third-party logos, mockups, border, rounded outer canvas corners or watermark. Output a single icon, not a collection.
```

### 03 — Bốn định dạng hợp thành một trang • gần ngôn ngữ ảnh tham khảo

Không chia cả nền thành bốn ô giống các đối thủ; gom bốn màu vào một biểu tượng riêng.

```text
Create ONE app icon for All Document Reader & Editor, a reader for PDF, text documents, spreadsheets and presentations. Communicate four document types through red, blue, green and orange without copying any existing office software logo.

1024x1024 square, opaque full-bleed dark navy-blue #103C95 background. Center one large document silhouette with a clearly folded top-right corner. Inside that single page, arrange four large rounded color panels in a precise 2x2 layout, separated by a bold white cross-shaped gutter: red upper-left, blue upper-right, green lower-left, orange lower-right. Use one simple white glyph per panel: two horizontal lines, three paragraph lines, a 2x2 cell grid, a pie chart. Keep generous colored area around each glyph. A thick white outer page rim unifies all four panels.

Flat vector-like finish with very subtle depth under the whole page only. Strong color contrast, disciplined geometry, icon silhouette about 66% of canvas width. All essential glyphs sit inside the central 60% square.

No letters, PDF text, numbers, year badges, recognizable brand logos, extra objects, diagonal lighting streaks, metallic texture, outer frame, rounded image corners or watermark. One icon only, readable at 48 pixels.
```

### 04 — Bốn thẻ lớn • so sánh trực tiếp với hướng thị trường

```text
Create ONE bold, highly legible app icon for an all-document reader. Square 1024x1024, opaque edge-to-edge royal blue #145CD8 background with no rounded outer corners.

Place exactly four separate chunky document cards in a compact 2x2 group at the center. Each has a small folded upper-right corner and gently rounded lower corners. Red upper-left, blue upper-right, green lower-left, orange lower-right. Use original white document glyphs only: red has two thick horizontal lines, blue has three paragraph bars of unequal length, green has a bold 2x2 spreadsheet grid, orange has a bold pie chart. Give each card the same size and visual weight. Clear blue gutters separate them. The group occupies about 64% of the canvas width and height.

Minimal soft 3D extrusion, frontal view, even studio lighting, crisp edges, strong saturation, no glossy glare. The result must remain clear when reduced to 32 pixels.

No letters W X P, no PDF text, no third-party logos, labels, year badge, pencil, face, scanning brackets, background ornaments, phone mockup, border or watermark. Do not recreate any specific competitor icon.
```

### 05 — Folder tài liệu • cảm giác tiện ích, chuyên nghiệp

```text
Design ONE polished app icon for All Document Reader & Editor, an app for opening and organizing multiple document formats.

Output 1024x1024 square, opaque full-bleed background in deep royal blue #1555CE. Center one large white open-top document folder with a subtle blue inner face. Exactly four broad colored document tabs emerge from the folder, arranged as a compact fan: red, blue, green and orange. Each tab has a small folded corner. The visible tabs are large enough to distinguish at small sizes. The folder front is plain white with one bold blue page symbol and two horizontal content bars.

Use clean geometric construction, restrained satin 3D material, frontal camera with only slight depth, one soft contact shadow. The complete object occupies 68% of the canvas width; essential content stays within the central 64% square. Premium productivity utility, visually calm, easy to recognize at 48 pixels.

No file labels, text, letters, numbers, office brand logos, magnifying glass, pencil, scan corners, floating particles, orbit rings, outer rounded canvas corners, frame or watermark. One icon only.
```

### 06 — Giữ bút chỉnh sửa • chỉ hai lớp chính

Phù hợp nếu muốn nhấn mạnh đọc và chỉnh sửa; có thể khiến người xem liên tưởng app ghi chú hơn.

```text
Redesign the attached All Document Reader & Editor launcher icon into a much simpler document-editing symbol. Produce ONE square 1024x1024 app icon with an opaque full-bleed royal blue #1559D6 background, no pre-rounded image corners.

One large white folded-corner document is the dominant subject. Three broad colored tabs peek from behind its upper edge: red, green and orange. On the white page, place exactly three thick blue horizontal lines. One short golden-yellow pencil crosses the lower-right edge at a 40-degree angle. The pencil is secondary, no longer than 45% of the main page height, with a simple dark tip and no tiny metal details. It must not cover the content lines.

Simple matte 3D finish, crisp silhouette, soft upper-left lighting, minimal shadow. The full composition occupies 70% of the canvas width and all essential details remain within the central 64% square. Make the document readable first and the pencil second at 48 pixels.

No scan brackets, glowing orbit, sparkles, tiny file symbols, letters, text, third-party logos, face, extra objects, outer border or watermark. Single icon only.
```

### 07 — Mascot All Document Reader & Editor + nhiều định dạng • nhận diện riêng

```text
Use the attached smiling paper mascot as the identity reference for All Document Reader & Editor. Create ONE distinctive all-document reader app icon that preserves its friendly face and folded-page silhouette.

Square 1024x1024, opaque full-bleed royal blue #1F6FEB background, no pre-rounded canvas corners. Center one large white paper mascot with a light blue folded upper-right corner, a strong deep-blue outline, two happy closed curved eyes, one simple open smile, and two subtle pink cheeks. Tilt it only 5 degrees counterclockwise. Remove the small gray text lines from its lower half.

Behind the mascot, add exactly three broad document tabs: red at upper-left, green at left, orange at right. Keep the tabs simple and clearly separated; no glyphs are needed. The mascot dominates the composition, with the tabs providing a secondary multi-format cue. Use flat geometric shapes and very subtle depth, preserving the reference face rather than inventing a new character. The group occupies 68% of the canvas width, with the face and essential tab colors within the central 60% square.

No arms, legs, pencil, scan frame, letters, file labels, numbers, recognizable office logos, sparkles, busy background, outer border, device mockup or watermark. Must remain friendly and readable at 48 pixels. One icon only.
```

### 08 — Trang trắng và bốn tab màu • tối giản nhất

```text
Design ONE minimal, memorable app icon for All Document Reader & Editor, a universal document reader. Output a square 1024x1024 image, opaque full-bleed cobalt blue #175BDD background, no rounded outer image corners.

Use one large upright white document with softly rounded lower corners and one clear folded top-right corner. Its front has exactly three thick blue horizontal lines with generous spacing. Along its left edge, four wide colored index tabs are visible in a vertical sequence: red, blue, green, orange. Make the blue tab lighter than the background so it remains visible. Each tab is a simple rounded rectangle extending from behind the page; together they communicate several document formats without separate miniature documents.

Clean flat vector-like geometry, no gradients on the main symbol, no perspective, no complex shadows. Strong simple silhouette. The page and tabs occupy 62% of the canvas width and 64% of its height, centered optically. Prioritize clarity at 32 and 48 pixels.

No letters, text, numbers, brand logos, mascot face, pen, scanner brackets, charts, decorative rings, background waves, outer frame or watermark. Output one icon, not an icon sheet.
```

## 4. Đánh giá để chọn mẫu

Đây là mức ưu tiên thử nghiệm theo thiết kế, **không phải điểm đo hiệu quả thực tế**:

| Hướng | Điểm mạnh dự kiến | Cần kiểm tra khi có ảnh | Ưu tiên |
|---|---|---|---|
| 01 | Giữ liên hệ với icon đang dùng, thông điệp nhiều định dạng | Các thẻ phía sau có quá nhỏ không? | Rất cao |
| 02 | Rõ, gọn, dễ làm bộ icon đồng nhất | Có quá phổ thông không? | Rất cao |
| 03 | Giữ bốn màu, có một silhouette thống nhất | Bốn glyph có chật trong trang không? | Cao |
| 04 | Người xem dễ liên tưởng bộ tài liệu văn phòng | Có bị lẫn với các icon trong ảnh tham khảo không? | Trung bình |
| 05 | Gọn gàng, có cảm giác quản lý tài liệu | Có bị hiểu chủ yếu là file manager không? | Trung bình |
| 06 | Nói rõ chức năng chỉnh sửa | Bút có che trang hoặc lấn át thông điệp đọc file không? | Cao |
| 07 | Có mascot riêng, nối với nhận diện All Document Reader & Editor | Có bị cảm nhận giống app trẻ em hoặc ghi chú không? | Cao |
| 08 | Ít chi tiết nhất, dễ nhìn ở kích thước nhỏ | Bốn tab có đủ gợi nhiều định dạng không? | Cao |

Chấm từng ảnh từ 1–5 theo bảng sau. Điểm tổng = tổng `(điểm / 5 × trọng số)`.

| Tiêu chí | Trọng số | Cách đánh giá |
|---|---:|---|
| Nhìn rõ khi thu nhỏ | 30 | Xem 32/48 px, vẫn nhận ra trang và các màu chính |
| Gợi đúng app tài liệu | 25 | Nhìn nhanh có hiểu liên quan đọc/mở tài liệu? |
| Có nét riêng | 20 | Đặt cạnh ảnh tham khảo, có dễ tìm lại? |
| Bố cục và tương phản | 15 | Một điểm chính, không cháy sáng, không chìm vào nền |
| Dễ chuyển thành launcher asset | 10 | Dễ tách nền, ít chi tiết sát mép, chịu được crop tròn |

Loại ngay ảnh có trang méo, fold vô lý, glyph lỗi, màu đục, chi tiết dính vào nhau hoặc cần phóng lớn mới hiểu. Nếu hai mẫu điểm gần nhau, ưu tiên mẫu ít chi tiết hơn. Muốn biết tác động tới lượt cài đặt cần thử nghiệm với người dùng hoặc thử nghiệm store; ảnh tham khảo không đủ để kết luận.

## 5. Prompt tinh chỉnh sau khi chọn

Đính kèm đúng mẫu đã chọn. Mỗi lượt dùng một câu để kiểm soát thay đổi:

**Làm rõ ở kích thước nhỏ**
```text
Refine the attached selected icon. Preserve its composition and identity. Enlarge the main document by 10%, simplify the smallest internal details, and increase separation between the white page and colored tabs. Keep the same palette and background. Change nothing else. Output one square 1024x1024 icon.
```

**So sánh nền đậm**
```text
Keep the attached icon's foreground exactly the same in shape, scale, position, color and material. Change only the background to solid deep royal blue #124DBD. No glow or texture. Output one square 1024x1024 opaque image.
```

**So sánh bản phẳng**
```text
Preserve the attached icon's exact composition, proportions and colors. Convert only the rendering style to flat geometric artwork: remove bevels, specular highlights and cast shadows. Keep the folded corner clear. Add no new details. Output one square 1024x1024 icon.
```

**Tách foreground để chuẩn bị adaptive icon**
```text
Extract only the complete central foreground symbol from the attached selected icon onto a truly transparent background. Preserve its shapes, colors, positions and proportions as closely as possible. Remove the background and background glow. No checkerboard pattern, no white matte, no new objects. Output one 1024x1024 PNG with alpha.
```

Việc gen lại có thể làm lệch chi tiết; so với mẫu đã chọn trước khi dùng. Nếu cần giữ chính xác từng pixel, tách lớp bằng công cụ đồ họa.

## 6. Khi đã chốt thiết kế

- Giữ bản gốc 1024 px và bản foreground có alpha riêng.
- Preview crop tròn, squircle và hình vuông bo góc trước khi xuất tài nguyên launcher. Vùng trung tâm trong các prompt chỉ là khoảng đệm thiết kế, không thay cho bước căn adaptive icon.
- Tạo bản monochrome riêng bằng hình khối đơn giản, kiểm tra ở một màu; không lấy nguyên ảnh nhiều màu làm bản monochrome.
- Chưa thay tài nguyên app trong bước này. File này là brief và bộ prompt để tạo mẫu, đánh giá rồi chọn thiết kế.
