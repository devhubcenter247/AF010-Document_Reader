# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android document reader/creator app (PDF, Word, Excel, PPT, TXT, images). Kotlin, View system with ViewBinding (no Compose UI), Koin, Room, coroutines/Flow. Built so far: the first-run flow, the data layer, the main shell (Files, Tools, Settings tabs with file browsing, search, multi-select), the document scanner, the PDF tools (merge, split, compress, protect, watermark, redact, OCR, translate, ZIP, images to PDF, PDF to images), the PDF reader with in-place editing (add text, annotate, sign, fill forms, organize pages) and the Office viewer/editor (Word, Excel, PowerPoint). Office to PDF is not built yet (a "Coming soon" toast, marked with `TODO`). The Premium screens exist as UI only: there is no Play Billing, nothing is gated and nothing counts free uses.

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

Navigation between fragments uses a custom route system in `HostInterfaces.kt`, not Jetpack Navigation XML graphs (there is no `res/navigation`). An activity overrides `fragmentContainerId` and `navGraph` (a `NavGraph` built with `addDestination(name, isStartDestination) { args -> Fragment }`); routes are registered in the global `RouteRegistry`. Fragments then call `navigateTo(routeName, "key" to value)`, `popBackTo`, `popToRoot`, `navigatePopUpTo`. The `NavController` helpers in `com/ui/baselib/extensions/NavigationExtensions.kt` are a separate, currently unused path.

### Main screens

`MainActivity` hosts three tabs through the route system above (`ROUTE_FILES`, `ROUTE_TOOLS`, `ROUTE_SETTINGS`); `selectTab(route)` swaps the fragment without a back stack entry, so a tab fragment is recreated each time it is selected. Back goes to the Files tab first, then asks with `QuitDialog`. The bottom bar is plain views in `activity_main.xml` driven by `isSelected`.

- Every document list (Files tab, `FileListActivity`, `SearchActivity`, `MultiSelectActivity`, `AllFilesActivity`) uses its own instance of `ui/main/files/DocumentsViewModel`, which narrows `DocumentRepository.documents` by a `DocumentFilter` (tab, sort, type, folder, query). Rows come from `DocumentAdapter` (`item_document.xml`), which also has a selection mode and search highlighting.
- `ui/main/files/DocumentActions` owns open + the options sheet (share, favorite, rename, copy, file info, delete) for a single document; reuse it instead of wiring those per screen.
- Search looks in file names and, through `pdf/ContentIndex`, in the text of PDF (first 50 pages, cached per file version in `cacheDir/content_index`) and TXT files. Office files are matched by name only: the SDK reads their text only through a view on screen.
- "Open a file" on the Files tab (shown without storage access) uses the system picker and copies the document to `filesDir/opened`, because the readers take file paths; edits are saved to that copy.
- All tool shortcuts go through `Activity.openTool(tool, paths)` in `ui/main/tools/ToolLauncher.kt`. `model/Tool.kt` is the single list of tools (group, texts, icons, premium flag); `model/UseCase.kt` maps the Welcome choices to the suggested tool chips on the Files tab.
- Bottom sheets extend `BaseBottomSheet` (`ui/main/files/sheet/`, `ui/main/settings/ThemeBottomSheet`) and share `layout_sheet_header.xml`; dialogs extend `BaseDialog`. Reusable pieces: `layout_toolbar.xml`, `layout_segment.xml` + `ui/main/common/Segment.kt`, `ui/main/common/FilterChipAdapter`.
- Secondary activities set `fullStatus = true` and pad their top bar with `statusBarHeight` so the bar's background reaches the top of the screen.
- Theme: `Theme.DocumentReader` is DayNight; dark values are in `values-night/colors.xml`. The chosen `ThemeMode` is saved in `PreferenceHelper.themeMode` and applied with `AppCompatDelegate.setDefaultNightMode` in `DocumentApplication`. Use the semantic color resources (`color_bg_app`, `color_surface_card`, `text_primary`, …) rather than literals so both themes work.
- Sizes follow the design canvas (390 px wide) scaled to sdp: `sdp ≈ px × 0.77` (sdp is based on a 300 dp wide screen), for text sizes too; text never goes below `_9sdp`.

### Tools and scanner

Every tool follows the same path: `PickFileActivity` (skipped when the files are already known) → the tool's setup screen under `ui/main/tools/<tool>/` → `ProcessingActivity` → `SuccessActivity` (translate goes to `TranslateResultActivity` instead).

- A setup screen only builds a `ToolRequest` (`model/ToolRequest.kt`, a Parcelable sealed interface) and calls `ProcessingActivity.start`. To add a tool: add a `ToolRequest` subclass, handle it in `pdf/ToolRunner.run`, map the `Tool` to its setup activity in `ToolLauncher.kt` (sign, annotate and fill form open the reader in that mode instead), and register the activity in the manifest.
- `pdf/ToolRunner` (Koin single) runs one request at a time on the app scope and exposes `state: StateFlow<ToolState>`, so work continues when the Processing screen is left. Outputs go to `AppUtils.documentPath` with a unique name; a failed or cancelled job deletes what it wrote.
- `pdf/PdfToolbox` is the only place that touches PDF internals. Merge, split, compress, protect, OCR and blank Office files use the document SDK's public `com.editor.docsdk.DocumentTools`. Watermark, redact, page text and images-to-PDF call `com.reader.pdfviewer.pdfium.PdfiumCore` directly because the SDK has no API for them; that class is not part of the SDK's documented surface, so re-check those calls when the SDK version changes. All pdfium calls run on one single-threaded dispatcher.
- `pdf/PdfTranslator` translates page text on device with ML Kit and keeps the result as JSON in the cache (`TranslatedDocument`); the PDF is written only when the user saves or shares. `TranslateActivity.FREE_PAGES` is the free-version page limit from the design.
- Shared setup UI: `layout_tool_topbar`, `layout_tool_header`, `layout_tool_file`, `layout_option`, `layout_tool_action` with the helpers in `ui/main/tools/ToolViews.kt`; `FilesToolActivity` is the base for tools that turn a file list into one file (Merge, ZIP).
- Scanner (`ui/main/scan/`): `ScanActivity` hosts camera → crop → filters fragments over the route system, sharing `ScanSessionViewModel`. `detection/` (edge detector, stabilizer, auto-capture, filters, image processor) is OpenCV code ported unchanged from the earlier app; photos live in `cacheDir/scanner` until the PDF is written. Clean up (`ScanCleanUpFragment`, opened from the filters screen) lets the user paint over marks; `DocumentImageProcessor.cleanUp` fills them with the surrounding paper on the unfiltered crop and the filter is applied again. The slim OpenCV build has no `photo` module, so there is no `inpaint`: the fill is a median paper estimate spread into the hole.

### PDF reader

`ui/main/reader/pdf/PdfReaderActivity` hosts `PDFView` and is a small state machine: `Mode` (Read, Text, Annotate, Sign, Form, Search, Contents) decides which top bar and which bottom bar of `activity_pdf_reader.xml` is visible, and `render()` is the single place that applies it.

- Edits live in the viewer's memory until Save (`pdfView.saveDocument(file)` overwrites the file); closing a mode with unsaved edits asks to discard and reloads the file. Tools that produce a new file (compress, watermark, …) are started from the Tools sheet through `openTool` and read the file on disk.
- `edit/PdfOverlayView` sits above the viewer and takes touches only while a `Mode` object is set: `AnnotateMode` (select/move/resize, shapes, placing pictures) or `FormMode`. A gesture the overlay declines falls through to `PDFView`, so scrolling keeps working; taps that reach the viewer come back through `onPageTap` (add text, note, eraser). The pen and text markup are the viewer's own (`setDrawingMode`, text selection + `addTextMarkupToSelection`).
- A long press on a scanned page (`pdfView.isScannedPage`) in Read mode offers OCR of that page only (`PdfToolbox.recognizePage`, the SDK's `PdfTools.addTextLayer(..., only = listOf(page))`), written over the opened file, which is then reloaded. Protected or read-only files go to the OCR tool instead.
- `PdfReadingPrefs` keeps the last page, bookmarks, reading theme, scroll direction and text-view size per file path. The Night theme sets `delegate.localNightMode`, which recreates the activity so the bars turn dark too.
- Around it: `PdfContentsPanel` (outline, bookmarks, page thumbnails), `ReadAloud` (TTS sentence by sentence with highlight), `sheet/` (tools, reading mode, signature), `organize/PdfOrganizeActivity` (reorder/rotate/duplicate/delete via `PdfToolbox.organize`, returns `RESULT_OK` so the reader reloads), `reflow/PdfReflowActivity` (text view).
- `res/layout` files for this page were generated by a script in one pass; edit the XML directly from now on.

### Office viewer and editor

`ui/main/reader/office/OfficeActivity` shows Word, Excel and PowerPoint files in the document SDK's `com.editor.docsdk.DocumentView` and edits `.docx`/`.xlsx`/`.xlsm`/`.pptx` (legacy `.doc`/`.xls`/`.ppt` are view-only: the SDK cannot convert them). The SDK does the editing; the screen is the bar around it, switched by `Mode` (View, Edit, Find, Sorter) in `render()`.

- Editing starts with `documentView.startEditing(EditFeature.all(), false)`: no SDK toolbar, the app runs `EditAction`s through `DocumentEditor`. `Ribbon.kt` holds the tabs of each format (`Ribbons.of(editor)` drops commands the document lacks) and `RibbonAdapter`; icons and labels come from the SDK's `EditAction.icon/label`. Always call `editor.runSafely(...)`, not `run`: some SDK commands throw.
- Questions the SDK asks go through `EditDialogs` (`onEditRequest`): find and replace becomes the top bar, font and size become lists (so the chips can show what was picked); anything else falls back to the SDK's own dialog (colours, borders, animations, transitions…).
- `editor.panel.toolbar = layoutRibbon` makes the SDK keep the caret above the ribbon and lift the ribbon's parent (`layoutBottom`) over the keyboard; do not add IME inset handling on top.
- Beyond the documented API the screen uses classes the SDK keeps by name for custom edit screens (`com.wxiwei.office.editor.ui.*`, `com.wxiwei.office.reader.*`): `ExcelEditPanel.selectedRange/cellInput` for the formula box, `EditDrafts` for the restore-draft prompt, and the `OfficeDocumentView` found among `documentView.children` for slide pictures (`thumbnails.get/render`, pages count from 1) and its `state` flow. The slide strip and the sorter draw from those pictures.
- The slideshow is `SlideShowActivity` (landscape): the SDK's `SlideShowView` plays transitions, animations, links, pen and eraser, and the screen is only the bar over it. `OfficeActivity.startShow` gets the `Source` from `documentView.prepareSlideShow` and hands it over in `SlideShowActivity.source` (it cannot go through an Intent); the source reads from the open `DocumentView`, so `OfficeActivity` must stay open under the show.
- SDK 1.0.0 workarounds, re-check on upgrade: `paintBehindPages()` gives the SDK's page view a background because `DocumentView` opens Word files without one and the view then never draws (grey screen); `EditAction.NUMBER_FORMAT` always throws (a label with a bare `%` goes through `String.format`), so number formats are left out of the ribbon and of the cell-format sheet.
- `res/layout` files for this page were generated by a script in one pass too; edit the XML directly from now on.

### Premium (UI only)

`ui/main/premium/` holds `PaywallActivity` (`start(context, tool)` names the Premium tool in its header), `SubscriptionActivity` and `QuotaBottomSheet`. They are the design's screens without the logic behind them: prices and the trial length are constants in `model/PremiumPlan.kt`, the purchase and restore buttons only show a toast, the subscription screen always shows the free plan, and no screen shows the quota sheet yet. Wiring Play Billing means replacing those constants with store products, adding an entitlement flag (e.g. in `PreferenceHelper`), and then gating `Tool.isPremium` tools in `ToolLauncher.openTool`. Every such place is marked `TODO`.

### Ads

There is no ads SDK in the app. Where the design has a native ad (Welcome, Processing, Success, `QuitDialog`) the layout holds a dashed `layoutNativeAd` box with the text "[Native ad · loads here]": load the ad into that container when the SDK is added.

### First-run flow

`SplashActivity` → `LanguageActivity` → `OnboardingActivity` → `WelcomeActivity` → `PermissionActivity` → `MainActivity`. `PermissionActivity` sets `preferenceHelper.isFinishFirstFlow = true`; after that, splash goes straight to `MainActivity`. Storage access is `MANAGE_EXTERNAL_STORAGE` on API 30+ and legacy read/write permissions below.

`SplashActivity` is the only exported way in, and also takes the launcher shortcuts and documents from other apps (`ui/entry/`):

- `AppShortcut` is the list of launcher shortcuts (Scan, Search, Images to PDF, New document). They are dynamic, published from `MainActivity`, not a `shortcuts.xml`: the labels follow the in-app language and the target does not depend on the flavor's `applicationId`.
- The `ACTION_VIEW` filter on the splash lists the MIME types the readers show. The splash resolves the uri with `ui/main/files/OpenedFiles` (shared with "Open a file": a copy in `filesDir/opened`, or the file itself for a readable `file://` uri) and opens the reader directly, so back returns to the calling app.
- `PendingLaunch` holds a shortcut or file while the splash or the first-run screens are shown; `MainActivity` consumes it.

Language is applied per activity in `BaseAppActivity.attachBaseContext` through `LocateManager.createLocale`, using codes in Android resource form (`en`, `pt_BR`, `fa_rIR`). `BaseActivity.isFinishFirstFlow()` defaults to `true` and nothing overrides it, so the saved language (default `en`) is always used and the device-language branch is dead for now. Only default `values/` strings exist; no translations yet.

### Data layer

- DI: `di/ViewModelModule.kt` registers ViewModels (`viewModelOf`); `di/AppModule.kt` (Koin) provides `SharedPreference`, `PreferenceHelper`, the Room `AppDatabase` (`documents.db`), both DAOs, an application `CoroutineScope` named `"appScope"`, and `DocumentRepository`.
- `DocumentRepository` / `DocumentRepositoryImpl` (`database/`) is the single source for file lists. `documents` and `images` are `StateFlow<DataResponse<List<RecentDocument>>>` that re-scan when the MediaStore `ContentObserver` fires, when `refresh()` is called, or when storage permission flips (`refresh()` re-checks it; without permission the flow emits `DataError(PERMISSION_DENIED)`). Rename and delete go through the repository so Room rows and MediaStore stay in sync. The scanning, rename, delete, and id-migration logic is in `utils/AppUtils.kt`.
- Room holds only user state: `RecentDocument` and `FavoriteDocument`, both keyed by the MediaStore `mediaId`. `RecentDocument` doubles as the general document model across the app. `exportSchema` is false and the DB is at version 1 with no migrations.
- `DataResponse` (`Idle`/`Loading`/`Success`/`Error`) wraps async results.
- Preferences: add new keys as delegated properties in `PreferenceHelper` (`preference.boolean/int/string(...)`, or `preference.observable(...)` for a value exposing `flow`/`liveData`). `SharedPreference` stores non-primitive values as Gson JSON.
- `data/AppDataRepo.kt` is an older in-memory holder that is not registered in Koin; prefer `DocumentRepository`.

### `com.ui.baselib` (former `:lib` module)

The old `:lib` module now lives as plain source in `:app` under `app/src/main/java/com/ui/baselib` (package names kept, so imports did not change; its resources use the app's `R`). Besides the base classes: `BaseAdapter` / `DiffAdapter` (ViewBinding RecyclerView adapters — implement `createBinding` and `VB.bind(item, position)`), `BaseDialog`, `BaseBottomSheet`, popup builders, click helpers (`click`, `clickSingle` for debounced taps), safe Bundle/Intent helpers (`safeBundleOf`, `getData<T>(key)`), lifecycle/coroutine extensions, network observer, bitmap/file/URI utilities, and the widgets with real behaviour (a vendored Flexbox, `UiNavigationBar` + `NavItem` on top of `UiLinearLayout`/`UiLayoutHelper`, `IndicatorView`, `UiLinearProgressIndicator`). Check here before adding a helper.

Layouts use platform views (`TextView`, `LinearLayout`, `ImageView`…) with XML drawables in `res/drawable` instead of styling views: `bg_*` shapes for fill/corners, `stroke_*` shapes as `android:foreground` for the border of layouts and pictures (drawn over the content), `line_*` layer-lists for one-sided borders, `<icon>_<size>` layer-lists for sized compound drawables, and `android:clipToOutline="true"` to round a layout's content. A button whose icon must sit next to centred text is a `MaterialButton` (`app:icon`, `app:iconGravity="textStart"`, `app:backgroundTint="@null"`). Colours that change at runtime go through `utils/extensions/Shape.kt` (`setFillColor`, `setStroke`, `setCircle`, which mutate the view's own copy of the shape); icons set from code at a fixed size use `TextView.setCompoundDrawablesSized`.

## Repo quirks

- Two files declare packages left over from the project this was forked from, which do not match their directories: `base/Aso.kt` is `com.d010.pdf.reader.base` and `data/DataResponse.kt` is `com.d010.pdf.reader.viewmodel`. Import them under those names (as `DocumentRepositoryImpl` does). The APK base name (`D010-PDF-Reader-…`) comes from the same origin.
- `com.ui.baselib` helpers to avoid: `getData<String>(key)` throws when the extra was put as null (read optional extras with `intent.getStringExtra`), and `shareFile`/`shareFiles` in `com.ui.baselib.file` use the authority `<package>.provider` while the app's provider is `<package>.fileprovider` (use the ones in `Aso.kt` or `DocumentRepository.shareUri`).
- A `StateFlow` must be collected with `mainCollect { }` from `BaseHost`; a bare `collect { }` resolves to the suspending member instead.
- Any `ViewGroup` with padding clips its children to the padding box; decorations placed with negative margins need `android:clipToPadding="false"`.
- Several helpers exist both in `com.ui.baselib` and in the app packages (`setFullScreen`, `hideSystemBar`, `parcelable`, `isInternetAvailable`, `statusBarHeight`). Prefer the `com.ui.baselib` versions; watch for ambiguous imports.
- Layout dimensions use the `sdp`/`ssp` scalable-size libraries (`@dimen/_16sdp`, `@dimen/_14ssp`).
- Comments and some error messages in `com.ui.baselib` are in Vietnamese.
- The directory is not a git repository.
