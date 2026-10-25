# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android document reader/creator app (PDF, Word, Excel, PPT, TXT, images), shown to users as **Folio**. Kotlin, View system with ViewBinding (no Compose UI), Koin, Room, coroutines/Flow. Built so far: the first-run flow, the data layer, the main shell (Files, Tools, Settings tabs with file browsing, search, multi-select), the document scanner, the PDF tools (merge, split, compress, protect, watermark, redact, OCR, translate, ZIP, images to PDF, PDF to images, Office to PDF), the PDF reader with in-place editing (add text, annotate, sign, fill forms, organize pages) and the Office viewer/editor (Word, Excel, PowerPoint). The Premium screens exist as UI only: there is no Play Billing, nothing is gated and nothing counts free uses.

Gradle modules:

- `:app` — `com.af010.creator.docreader`, the application.
- `:pdfview` — `com.reader.pdfviewer`, the PDF viewer widget (`PDFView`: rendering, text selection, search, ink, annotations, forms, undo/redo, save). Source copied from the earlier app because the document SDK only exposes PDF as view-only; it sits on the SDK's pdfium classes. `SdkInternals.java` is a Java shim that reaches the SDK members Kotlin cannot (`internal`, name-mangled or obfuscated) — it is the first thing to break when the SDK version changes.
- `:opencv` — a slim local OpenCV 4.12 build (core, imgproc, imgcodecs; prebuilt `.so` only) used by the scanner. `opencv/README.md` has the rebuild steps. It relies on `libc++_shared.so` from the document SDK.

## Commands

The app has a `version` flavor dimension (`dev`, `product`), so every variant task needs a flavor: `DevDebug`, `ProductDebug`, `DevRelease`, `ProductRelease`.

```bash
./gradlew :app:assembleDevDebug            # build debug APK
./gradlew :app:installDevDebug             # install on a connected device
./gradlew :app:lintDevDebug                # Android lint
./gradlew :app:testDevDebugUnitTest        # JVM unit tests
./gradlew :app:testDevDebugUnitTest --tests "com.af010.creator.docreader.ExampleUnitTest"   # single test class (append .method for one test)
./gradlew :app:connectedDevDebugAndroidTest   # instrumented tests (device required)
```

Build prerequisites that are not in the repo:

- `com.editor:docsdk:1.0.0` is resolved from a private GitHub Packages Maven repo (`settings.gradle.kts`). It needs `gpr.user` and `gpr.key` Gradle properties, normally in `~/.gradle/gradle.properties`. Without them dependency resolution fails.
- Release builds reference `keystore/lionmen.jks` and `app/proguard-rules.pro`, neither of which exists in the working tree. Only debug variants build as-is.
- Toolchain: Gradle 9.6 wrapper, AGP 9.1, Kotlin 2.4, daemon JVM 25 (auto-provisioned through foojay), Java/Kotlin target 17. Configuration cache is on. `compileSdk`/`targetSdk` 37, `minSdk` 26. Only the `arm64-v8a` and `armeabi-v7a` ABIs are packaged (`abiFilters` in `app/build.gradle.kts`), so the app has no native x86/x86_64 build. The document SDK ships x86 and x86_64 libraries but `:opencv` does not: adding those ABIs means rebuilding OpenCV for them first (`opencv/README.md`). ABI splitting is on for the bundle only: an APK carries both ABIs.

Each flavor has its own `google-services.json` under `app/src/<flavor>/`; `product` also changes the `applicationId` to `com.editdocument.pdfreader.viewer`.

R8 keep rules for the app live in `app/src/main/keepRules/rules.keep` (AGP merges that folder), including the rule that keeps `BaseFragment.onBackPressed` by name.

## Architecture

### Screen base classes (`com.ui.baselib` → app)

Every screen extends a `com.ui.baselib` base class through an app-level subclass:

`BaseActivity<VB>` (baselib) → `BaseAppActivity<VB>` (app) → concrete activity
`BaseFragment<VB>` (baselib) → `BaseAppFragment<VB>` (app) → concrete fragment

Pass the binding inflater to the constructor (`BaseAppActivity<ActivityFooBinding>(ActivityFooBinding::inflate)`) and implement the template methods instead of overriding `onCreate`:

- Activity: `initialize()`, `VB.setData()`, `VB.onClick()`, `backPressed()` — called in that order from `onCreate`; `backPressed()` runs on system back once the fragment back stack is empty.
- Fragment: `VB.initView()`, `VB.onClick()`, optional `onBackPressed(): Boolean`.

What the base classes already do, so screens should not redo it:

- Edge-to-edge and immersive mode with hidden system bars. The root view gets status-bar top padding unless the activity sets `fullStatus = true` (fragment: `isFullSc = true`). A fragment skips its own padding when its host activity already pads.
- `BaseHost` (`com/ui/baselib/base/HostInterfaces.kt`) is mixed into both and provides `launchIO/launchMain`, `Flow.collect { }` / `mainCollect` bound to the right lifecycle (fragments collect on the view lifecycle, at `RESUMED`; activities at `STARTED`), `toast`, keyboard helpers, and `post/postDelayed`.
- `launchActivity<T>("key" to value)`, `launchAndFinish<T>()`, and `launcherForResult<T> { result -> }` for activity navigation with extras.
- Tapping outside a focused `EditText` hides the keyboard.
- `BaseAppActivity`/`BaseAppFragment` add the no-internet dialog (shown and dismissed automatically on connectivity changes), `internetClick`/`internetSingleClick`/`runWithInternet`, the Koin-injected `preferenceHelper`, and the locale wrapping in `attachBaseContext`.

`BaseFragment` detects by reflection whether a subclass overrides `onBackPressed()` to decide if it registers a back callback; `rules.keep` keeps that method name, so do not rename it.

### Fragment navigation

Two mechanisms coexist:

- `MainActivity` uses Jetpack Navigation: a `NavHostFragment` (`R.id.navHostFragment`) with `res/navigation/main_nav_graph.xml` (Files, Tools, Settings). `show(destination)` navigates with single-top, restore-state and pop-up-to-start options, so each tab keeps its state and Files stays at the root.
- `ScanActivity` uses the custom route system in `HostInterfaces.kt`: it overrides `fragmentContainerId` and `navGraph` (a `NavGraph` built with `addDestination(name, isStartDestination) { args -> Fragment }`); routes are registered in the global `RouteRegistry`, and fragments call `navigateTo(routeName, "key" to value)`, `popBackTo`, `popToRoot`, `navigatePopUpTo`.

### Main screens

`MainActivity` hosts three tabs (`selectTab(route)` takes `ROUTE_FILES`, `ROUTE_TOOLS`, `ROUTE_SETTINGS`). Back goes to the Files tab first, then asks with `QuitDialog`. The floating bottom bar and the tilted "+" (`CreateBottomSheet`, "What shall we make?") are plain views in `activity_main.xml`; tabs follow the destination through `isSelected`.

- The Files tab (`fragment_files.xml`, a MotionLayout with `res/xml/scene_files_home.xml`) keeps its header fixed; under it the Read and Create cards, "Your documents" (files per type, from `DocumentRepository.documents`, opening `AllFilesActivity` with `EXTRA_TAB`/`EXTRA_TYPE`), "Start something new" (Word/Excel/PowerPoint tiles, Scan and Photos to PDF), the "Edit any document" card and the files opened lately (`RECENT_LIMIT`). Once the cards scroll away two pills take their place: `FilesFragment` sets the scene's progress from the scroll position (no `OnSwipe`, which fought the scroll on flings); without storage access it shows "Your library" with Allow access / Open a file and quick start tiles. All files (`AllFilesActivity`) has Recent / Favourites / All with type chips and the sort sheet.
- Every document list (Files tab, `SearchActivity`, `MultiSelectActivity`, `AllFilesActivity`) uses its own instance of `ui/main/files/DocumentsViewModel`, which narrows `DocumentRepository.documents` by a `DocumentFilter` (tab, sort, type, folder, query). Rows come from `DocumentAdapter` (`item_document.xml`), which also has a selection mode and search highlighting.
- `ui/main/files/DocumentActions` owns open + the options sheet (share, favorite, rename, copy, file info, delete) for a single document; reuse it instead of wiring those per screen.
- Search looks in file names and, through `pdf/ContentIndex`, in the text of PDF (first 50 pages, cached per file version in `cacheDir/content_index`) and TXT files; `SearchScope.Apart` lists the two separately ("In file names" capped at `NAME_LIMIT` rows with Show all, "Inside your documents" with page/line and snippet). Office files are matched by name only: the SDK reads their text only through a view on screen.
- "Open a file" on the Files tab (shown without storage access) uses the system picker and copies the document to `filesDir/opened`, because the readers take file paths; edits are saved to that copy.
- New Word/Excel/PowerPoint files are made at once by `createBlankDocument(type)` (`ui/main/newdocument/BlankDocument.kt`: "Untitled-Oct-18" in Download › Folio, opened in `OfficeActivity` ready to edit); there is no new-document form. The New document launcher shortcut opens the Create sheet.
- All tool shortcuts go through `Activity.openTool(tool, paths)` in `ui/main/tools/ToolLauncher.kt`. `model/Tool.kt` is the single list of tools (group, texts, glyph, tint, premium flag, `inToolbox`); tool tiles are drawn with `ui/common/ToolTile.kt` (`showToolTile`). `model/UseCase.kt` holds the Welcome choices shown in Settings ("I use Folio for").
- Bottom sheets extend `BaseBottomSheet` (`ui/main/files/sheet/`) and share `layout_sheet_header.xml` and the `bg_sheet` background; dialogs extend `BaseDialog` and use `bg_dialog` with the mascot (`ChoiceDialog`, `MessageDialog` with `alert`, `QuitDialog`, `DeleteDialog`, `RenameDialog`/`TextInputDialog`, `OpenPasswordDialog`, `DialogNoInternet`); their main button is `DialogPrimaryButton`, filled with the file type's colour by `ui/dialog/DialogAccent.kt` (`tintAccent`, from an explicit colour or a host activity that implements `DocumentAccent`, as the readers and Organize do; `fillHostAccent` does the same for buttons on the reader screens and their sheets). Rows of colour dots (`addColorDots` in `reader/pdf/ReaderViews.kt`) end with a "+" that opens `ui/common/ColorGridDialog` (the full palette). Reusable pieces: `layout_screen_bar.xml` (round back, title, optional subtitle and Pro chip, two round actions), `layout_segment.xml` + `ui/main/common/Segment.kt` (`setUp`/`select`, and `fillSegment`/`selectSegment` for more than three options built in code), `ui/main/common/FilterChipAdapter`, `layout_step_progress.xml` + `ui/common/StepProgress.kt`, `ui/common/FileTile.kt` (`showTypeTile` for a document or a `File`).
- Secondary activities set `fullStatus = true` and pad their top bar with `statusBarHeight` so the bar's background reaches the top of the screen.
- Theme: `Theme.DocumentReader` is DayNight; dark values are in `values-night/colors.xml`. The chosen `ThemeMode` is saved in `PreferenceHelper.themeMode` (Auto / Light / Dark segment in Settings) and applied with `AppCompatDelegate.setDefaultNightMode` in `DocumentApplication`. Use the semantic color resources (`color_bg_app`, `color_surface_card`, `text_primary`, `primary`, `color_file_*` and their `*_bg`, …) rather than literals so both themes work. The font is Nunito (`res/font/nunito_400…900`); the theme must not set `fontFamily`, or it overrides the weights set in layouts.
- Look and feel (the "Folio" design): floating white cards with soft shadows (`FloatingCard`, `FloatBar` with `outlineSpotShadowColor = color_shadow`), pills for primary actions (`PrimaryPillButton`), picked state through `isSelected` and selector drawables (`bg_option_card`, `bg_radio`, `bg_page_thumb`…) rather than colours set in code, dark "ink" bars for selection actions (`bg_ink_bar` + `InkBarAction`). Icons are `ic_fo_*` vectors converted from the design's SVGs (24 viewport, white, tinted in layouts).
- Sizes follow the design canvas (390 px wide) scaled to sdp: `sdp ≈ px × 0.77` (sdp is based on a 300 dp wide screen), for text sizes too; text never goes below `_9sdp`.

### Tools and scanner

Every tool follows the same path: `PickFileActivity` (skipped when the files are already known) → the tool's setup screen under `ui/main/tools/<tool>/` → `ProcessingActivity` → `SuccessActivity` (translate goes to `TranslateResultActivity` instead).

- A setup screen only builds a `ToolRequest` (`model/ToolRequest.kt`, a Parcelable sealed interface) and calls `ProcessingActivity.start`. To add a tool: add a `ToolRequest` subclass, handle it in `pdf/ToolRunner.run`, map the `Tool` to its setup activity in `ToolLauncher.kt` (sign, annotate and fill form open the reader in that mode instead), and register the activity in the manifest.
- Office to PDF is the one tool whose job needs a screen: `tools/office/OfficeToPdfActivity` opens the file in a `DocumentView` kept under a pager of the PDF's pages (`PdfPageAdapter`, drawn by `OfficePdf.preview`), and on Convert hands `ToolRunner.officeWriter` an `OfficeWriter` that draws from that open document before starting `ToolRequest.OfficeToPdf`. The screen stays under Processing and closes when the job is done. Convert to PDF in the Office reader's options uses the same writer.
- `pdf/ToolRunner` (Koin single) runs one request at a time on the app scope and exposes `state: StateFlow<ToolState>`, so work continues when the Processing screen is left. Outputs go to `AppUtils.documentPath` with a unique name; a failed or cancelled job deletes what it wrote.
- `pdf/PdfToolbox` is the only place that touches PDF internals. Merge, split, compress, protect, OCR and blank Office files use the document SDK's public `com.editor.docsdk.DocumentTools`. Watermark, redact, page text and images-to-PDF call `com.reader.pdfviewer.pdfium.PdfiumCore` directly because the SDK has no API for them; that class is not part of the SDK's documented surface, so re-check those calls when the SDK version changes. All pdfium calls run on one single-threaded dispatcher.
- `pdf/PdfTranslator` translates page text on device with ML Kit and keeps the result as JSON in the cache (`TranslatedDocument`); the PDF is written only when the user saves or shares. `TranslateActivity.FREE_PAGES` is the free-version page limit from the design.
- Shared setup UI ("Step 2 of 3"): `layout_screen_bar` + `layout_step_progress`, `layout_tool_file` (type tile, name, meta, Change), `layout_option` (radio card with optional badge), `layout_tool_action` (note + big pill) and text fields in a `bg_field` box (`FieldLabel`, `FieldInput`); helpers in `ui/main/tools/ToolViews.kt` (`show`, `showSetupStep`, `setChecked`, `FileChanger` for Change). `PickFileActivity` is step 1 (search, "Browse folders", numbered picks and the dark Next bar for Merge and ZIP). `FilesToolActivity` is the base for tools that turn a file list into one file (Merge: ordered rows with move up and drag; ZIP: picture, type tiles, remove). OCR has its own setup screen (`ui/main/tools/ocr/OcrActivity`). Processing shows the ring around the mascot and three steps; Success shows confetti, Open / Share / Rename and three tools to keep going.
- Scanner (`ui/main/scan/`): `ScanActivity` hosts camera → crop → filters fragments over the route system, sharing `ScanSessionViewModel`. `detection/` (edge detector, stabilizer, auto-capture, filters, image processor) is OpenCV code ported unchanged from the earlier app; photos live in `cacheDir/scanner` until the PDF is written. Clean up (`ScanCleanUpFragment`, opened from the filters screen) lets the user paint over marks; `DocumentImageProcessor.cleanUp` fills them with the surrounding paper on the unfiltered crop and the filter is applied again. The slim OpenCV build has no `photo` module, so there is no `inpaint`: the fill is a median paper estimate spread into the hole.

### PDF reader

`ui/main/reader/pdf/PdfReaderActivity` hosts `PDFView` and is a small state machine: `Mode` (Read, Text, Annotate, Sign, Form, Search, Contents) decides which floating top bar and which bottom card of `activity_pdf_reader.xml` is visible, and `render()` is the single place that applies it. In Read mode the bottom card has the page slider and the Text / Annotate / Sign / Fill form / Contents tabs; the star in the top bar bookmarks the page; More opens `sheet/PdfToolsBottomSheet` (reading theme, scroll direction, text view, read aloud, auto-scroll, the tools that make a new file, and Share / Rename / File info / Organize pages). Search lists every match in the bottom card (`SearchMatchAdapter`).

- Edits live in the viewer's memory until Save (`pdfView.saveDocument(file)` overwrites the file); closing a mode with unsaved edits asks to discard and reloads the file. Tools that produce a new file (compress, watermark, …) are started from the Tools sheet through `openTool` and read the file on disk.
- `edit/PdfOverlayView` sits above the viewer and takes touches only while a `Mode` object is set: `AnnotateMode` (select/move/resize, shapes, placing pictures) or `FormMode`. A gesture the overlay declines falls through to `PDFView`, so scrolling keeps working; taps that reach the viewer come back through `onPageTap` (add text, note, eraser). The pen and text markup are the viewer's own (`setDrawingMode`, text selection + `addTextMarkupToSelection`).
- A long press on a scanned page (`pdfView.isScannedPage`) in Read mode offers OCR of that page only (`PdfToolbox.recognizePage`, the SDK's `PdfTools.addTextLayer(..., only = listOf(page))`), written over the opened file, which is then reloaded. Protected or read-only files go to the OCR tool instead.
- `PdfReadingPrefs` keeps the last page, bookmarks, reading theme, scroll direction and text-view size per file path. The Night theme sets `delegate.localNightMode`, which recreates the activity so the bars turn dark too.
- Around it: `PdfContentsPanel` (Outline, Bookmarks, Pages), `ReadAloud` (TTS sentence by sentence with highlight), `sheet/` (More, signature), `organize/PdfOrganizeActivity` (reorder/rotate/duplicate/delete via `PdfToolbox.organize`, returns `RESULT_OK` so the reader reloads), `reflow/PdfReflowActivity` (text view).
- `res/layout` files for this page were generated by a script in one pass; edit the XML directly from now on.

### Office viewer and editor

`ui/main/reader/office/OfficeActivity` shows Word, Excel and PowerPoint files (floating bars with the file type badge, an Edit pill while viewing) in the document SDK's `com.editor.docsdk.DocumentView` and edits `.docx`/`.xlsx`/`.xlsm`/`.pptx` (legacy `.doc`/`.xls`/`.ppt` are view-only: the SDK cannot convert them). The SDK does the editing; the screen is the bar around it, switched by `Mode` (View, Edit, Find, Sorter) in `render()`.

- Editing starts with `documentView.startEditing(EditFeature.all(), false)`: no SDK toolbar, the app runs `EditAction`s through `DocumentEditor`. `Ribbon.kt` holds the tabs of each format (`Ribbons.of(editor)` drops commands the document lacks) and `RibbonAdapter`; icons and labels come from the SDK's `EditAction.icon/label`. Always call `editor.runSafely(...)`, not `run`: some SDK commands throw.
- Questions the SDK asks go through `EditDialogs` (`onEditRequest`): find and replace becomes the top bar, font and size become lists (so the chips can show what was picked); anything else falls back to the SDK's own dialog (colours, borders, animations, transitions…).
- While editing the bars are docked flat (`dockTop`, `bg_docked_top`/`bg_docked_bottom`, flat ribbon commands in the file type's colours) instead of floating, so the page keeps the room; viewing keeps the floating bar. `editor.panel.toolbar = layoutRibbon` makes the SDK keep the caret above the ribbon and lift the ribbon's parent (`layoutBottom`) until its bottom edge meets the keyboard; do not add IME inset handling on top. While the keyboard is up `fitKeyboard` swaps the tab row for a tab chip (`btnTabPick`, a menu of the tabs) so the ribbon is one row.
- Beyond the documented API the screen uses classes the SDK keeps by name for custom edit screens (`com.wxiwei.office.editor.ui.*`, `com.wxiwei.office.reader.*`): `ExcelEditPanel.selectedRange/cellInput` for the formula box, `EditDrafts` for the restore-draft prompt, and the `OfficeDocumentView` found among `documentView.children` for slide pictures (`thumbnails.get/render`, pages count from 1) and its `state` flow. The slide strip and the sorter draw from those pictures.
- The slideshow is `SlideShowActivity` (landscape): the SDK's `SlideShowView` plays transitions, animations, links, pen and eraser, and the screen is only the bar over it. `OfficeActivity.startShow` gets the `Source` from `documentView.prepareSlideShow` and hands it over in `SlideShowActivity.source` (it cannot go through an Intent); the source reads from the open `DocumentView`, so `OfficeActivity` must stay open under the show.
- SDK 1.0.0 workarounds, re-check on upgrade: `paintBehindPages()` gives the SDK's page view a background because `DocumentView` opens Word files without one and the view then never draws (grey screen); `EditAction.NUMBER_FORMAT` always throws (a label with a bare `%` goes through `String.format`), so number formats are left out of the ribbon and of the cell-format sheet.
- `res/layout` files for this page were generated by a script in one pass too; edit the XML directly from now on.
- `OfficePdf` writes an open document as a vector PDF (Word pages at their size, slides, sheets on A4 landscape: shrunk onto one page down to 60%, else cut at column and row edges) and draws the same pages for the preview. The SDK has no API for it, so it calls the SDK's view classes by their R8 names in docsdk 1.0.0 (`com.editor.docsdk.internal.xf` = Word, `xa` = Presentation, `l4` = ExcelView…, from `mappings/docsdk-1.0.0.txt` in the Pdf-Document-Reader project), all in one block at the end of the file: check every name there when the SDK version changes. `SheetView.printEdges` is not in the published library, so it is copied; Word's page frame and margin marks are hidden by giving the page's paint a transparent colour filter while drawing; Word pages and slides keep coming in after the document reports ready, so the page count is awaited until it is steady.

### Premium (UI only)

`ui/main/premium/` holds `IntroPaywallActivity` (after the first-run permission screen), `PaywallActivity` (`start(context, tool)` names the Premium tool; yearly and monthly cards, lifetime as a link), `SubscriptionActivity` ("Your plan") and `QuotaBottomSheet(tool, used, limit)`. They are the design's screens without the logic behind them: prices and the trial length are constants in `model/PremiumPlan.kt`, the purchase and restore buttons only show a toast, the plan screen always shows the free plan, and no screen shows the quota sheet yet. Every Premium entry point is hidden while the remote config flag `paywall_enabled` (`app/remote/RemoteConfig.kt`, default false) is off. Wiring Play Billing means replacing those constants with store products, adding an entitlement flag (e.g. in `PreferenceHelper`), and then gating `Tool.isPremium` tools in `ToolLauncher.openTool`. Every such place is marked `TODO`.

### Ads

There is no ads SDK in the app. The design shows a native ad on Processing, Success and the quit dialog; the layouts have no placeholder for it, so add a container there when the SDK is added.

### First-run flow

`SplashActivity` → `LanguageActivity` → `OnboardingActivity` → `WelcomeActivity` → `PermissionActivity` → (`IntroPaywallActivity` when access was allowed) → `MainActivity`. `PermissionActivity` sets `preferenceHelper.isFinishFirstFlow = true`; after that, splash goes straight to `MainActivity`. Storage access is `MANAGE_EXTERNAL_STORAGE` on API 30+ and legacy read/write permissions below.

`SplashActivity` is the only exported way in, and also takes the launcher shortcuts and documents from other apps (`ui/entry/`):

- `AppShortcut` is the list of launcher shortcuts (Scan, Search, Images to PDF, New document). They are dynamic, published from `MainActivity`, not a `shortcuts.xml`: the labels follow the in-app language and the target does not depend on the flavor's `applicationId`.
- The `ACTION_VIEW` filter on the splash lists the MIME types the readers show. The splash resolves the uri with `ui/main/files/OpenedFiles` (shared with "Open a file": a copy in `filesDir/opened`, or the file itself for a readable `file://` uri) and opens the reader directly, so back returns to the calling app.
- `PendingLaunch` holds a shortcut or file while the splash or the first-run screens are shown; `MainActivity` consumes it.

Language is applied per activity in `BaseAppActivity.attachBaseContext` through `LocateManager.createLocale`, using codes in Android resource form (`en`, `pt_BR`, `fa_rIR`). `BaseActivity.isFinishFirstFlow()` defaults to `true` and nothing overrides it, so the saved language (default `en`) is always used and the device-language branch is dead for now. Translations live in 45 `values-<lang>/strings.xml` folders; strings added for the Folio design are named `fo_*`.

### Data layer

- DI: `di/ViewModelModule.kt` registers ViewModels (`viewModelOf`); `di/AppModule.kt` (Koin) provides `SharedPreference`, `PreferenceHelper`, the Room `AppDatabase` (`documents.db`), both DAOs, an application `CoroutineScope` named `"appScope"`, and `DocumentRepository`.
- `DocumentRepository` / `DocumentRepositoryImpl` (`database/`) is the single source for file lists. `documents` and `images` are `StateFlow<DataResponse<List<RecentDocument>>>` that re-scan when the MediaStore `ContentObserver` fires, when `refresh()` is called, or when storage permission flips (`refresh()` re-checks it; without permission the flow emits `DataError(PERMISSION_DENIED)`). Rename and delete go through the repository so Room rows and MediaStore stay in sync. The scanning, rename, delete, and id-migration logic is in `utils/AppUtils.kt`.
- Room holds only user state: `RecentDocument` and `FavoriteDocument`, both keyed by the MediaStore `mediaId`. `RecentDocument` doubles as the general document model across the app. `exportSchema` is false and the DB is at version 1 with no migrations.
- `DataResponse` (`Idle`/`Loading`/`Success`/`Error`) wraps async results.
- Preferences: add new keys as delegated properties in `PreferenceHelper` (`preference.boolean/int/string(...)`, or `preference.observable(...)` for a value exposing `flow`/`liveData`). `SharedPreference` stores non-primitive values as Gson JSON.
- `data/AppDataRepo.kt` is an older in-memory holder that is not registered in Koin; prefer `DocumentRepository`.

### `com.ui.baselib` (former `:lib` module)

The old `:lib` module now lives as plain source in `:app` under `app/src/main/java/com/ui/baselib` (package names kept, so imports did not change; its resources use the app's `R`). Besides the base classes: `BaseAdapter` / `DiffAdapter` (ViewBinding RecyclerView adapters — implement `createBinding` and `VB.bind(item, position)`), `BaseDialog`, `BaseBottomSheet`, popup builders, click helpers (`click`, `clickSingle` for debounced taps), safe Bundle/Intent helpers (`safeBundleOf`, `getData<T>(key)`), lifecycle/coroutine extensions, network observer, bitmap/file/URI utilities, and the widgets with real behaviour (a vendored Flexbox under `widget/flexbox`, `IndicatorView`). Check here before adding a helper.

Layouts use platform views (`TextView`, `LinearLayout`, `ImageView`…) with XML drawables in `res/drawable` instead of styling views: `bg_*` shapes and selectors for fill, corners and picked state (tint a neutral shape such as `bg_badge`, `bg_rounded_panel` or `bg_soft_pill` with `android:backgroundTint` rather than adding a drawable per colour), `<icon>_<size>` layer-lists for sized compound drawables, and `android:clipToOutline="true"` to round a layout's content. A button whose icon must sit next to centred text is a `MaterialButton` (`app:icon`, `app:iconGravity="textStart"`, `app:backgroundTint="@null"`). Colours that change at runtime go through `utils/extensions/Shape.kt` (`setFillColor`, `setStroke`, `setCircle`, which mutate the view's own copy of the shape); icons set from code at a fixed size use `TextView.setCompoundDrawablesSized`.

## Repo quirks

- Two files declare packages left over from the project this was forked from, which do not match their directories: `base/Aso.kt` is `com.d010.pdf.reader.base` and `data/DataResponse.kt` is `com.d010.pdf.reader.viewmodel`. Import them under those names (as `DocumentRepositoryImpl` does). The APK base name (`D010-PDF-Reader-…`) comes from the same origin.
- `com.ui.baselib` helpers to avoid: `getData<String>(key)` throws when the extra was put as null (read optional extras with `intent.getStringExtra`), and `shareFile`/`shareFiles` in `com.ui.baselib.file` use the authority `<package>.provider` while the app's provider is `<package>.fileprovider` (use the ones in `Aso.kt` or `DocumentRepository.shareUri`).
- A `StateFlow` must be collected with `mainCollect { }` from `BaseHost`; a bare `collect { }` resolves to the suspending member instead.
- Any `ViewGroup` with padding clips its children to the padding box; decorations placed with negative margins need `android:clipToPadding="false"`.
- Several helpers exist both in `com.ui.baselib` and in the app packages (`setFullScreen`, `hideSystemBar`, `parcelable`, `isInternetAvailable`, `statusBarHeight`). Prefer the `com.ui.baselib` versions; watch for ambiguous imports.
- Layout dimensions use the `sdp`/`ssp` scalable-size libraries (`@dimen/_16sdp`, `@dimen/_14ssp`).
- Comments and some error messages in `com.ui.baselib` are in Vietnamese.
- Work on the Folio reskin is on the `reskin-v2` branch; `master` has the earlier look.
