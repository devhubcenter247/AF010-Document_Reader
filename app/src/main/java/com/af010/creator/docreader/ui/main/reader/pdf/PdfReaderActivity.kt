package com.af010.creator.docreader.ui.main.reader.pdf

import android.content.res.ColorStateList
import com.af010.creator.docreader.ui.dialog.fillHostAccent
import com.af010.creator.docreader.ui.dialog.DocumentAccent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.Choreographer
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.SeekBar
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.databinding.ActivityPdfReaderBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.dialog.MessageDialog
import com.af010.creator.docreader.ui.dialog.OpenPasswordDialog
import com.af010.creator.docreader.ui.dialog.TextInputDialog
import com.af010.creator.docreader.ui.main.reader.pdf.edit.AnnotateMode
import com.af010.creator.docreader.ui.main.reader.pdf.edit.FormMode
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfOverlayView
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfBookmark
import com.af010.creator.docreader.ui.main.reader.pdf.edit.PdfReadingPrefs
import com.af010.creator.docreader.ui.main.reader.pdf.organize.PdfOrganizeActivity
import com.af010.creator.docreader.ui.main.reader.pdf.reflow.PdfReflowActivity
import com.af010.creator.docreader.ui.main.reader.pdf.sheet.ReaderAction
import com.af010.creator.docreader.ui.main.reader.pdf.sheet.PdfToolsBottomSheet
import com.af010.creator.docreader.ui.dialog.RenameDialog
import com.af010.creator.docreader.ui.main.files.sheet.FileInfoBottomSheet
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.model.DocumentType
import com.d010.pdf.reader.viewmodel.DataResponse
import com.d010.pdf.reader.base.shareFile
import com.af010.creator.docreader.ui.main.reader.pdf.sheet.SignatureBottomSheet
import com.af010.creator.docreader.ui.main.tools.openTool
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.reader.pdfviewer.TextMarkupType
import com.reader.pdfviewer.listener.OnLongPressListener
import com.reader.pdfviewer.listener.OnTapListener
import com.reader.pdfviewer.model.PdfAnnotationInfo
import com.reader.pdfviewer.model.SearchResult
import com.reader.pdfviewer.pdfium.PdfFormField
import com.reader.pdfviewer.pdfium.PdfPasswordException
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Reads a PDF and edits it in place: add text, annotate, sign and fill forms, each a mode with
 * its own top and bottom bar. Search, the contents panel, the reading themes, read aloud and
 * auto-scroll sit on top of plain reading. Edits stay in memory until Save writes the file.
 */
class PdfReaderActivity : BaseAppActivity<ActivityPdfReaderBinding>(ActivityPdfReaderBinding::inflate), DocumentAccent {

    override val accentColor: Int
        get() = R.color.color_file_pdf
    override val fullStatus: Boolean
        get() = true

    // Tapping the page moves the caret or picks an object; the keyboard must stay up.
    override val hideKeyboardWhenTouch: Boolean
        get() = false

    private enum class Mode { Read, Text, Annotate, Sign, Form, Search, Contents }
    private enum class Playback { None, ReadAloud, AutoScroll }

    private val toolbox: PdfToolbox by inject()
    private val repository: DocumentRepository by inject()
    private val prefs by lazy { PdfReadingPrefs(this) }
    private val file: File by lazy { File(intent.getStringExtra(EXTRA_PATH).orEmpty()) }

    private var password: String? = null
    private var loaded = false
    private var mode = Mode.Read
    private var pendingTool: Tool? = null

    // add text and annotate
    private var textSize = DEFAULT_TEXT_SIZE
    private var textColor = TEXT_COLORS.first()
    private var inkColor = INK_COLORS.first()
    private var inkWidth = DEFAULT_INK_WIDTH
    private var annotateTool = AnnotateTool.Highlight
    private var shape = AnnotateMode.Tool.RECT
    private var lastSignature: Bitmap? = null
    // the user agreed to OCR this file: other scanned pages are recognized without asking again
    private var ocrAgreed = false
    private var recognizing = false

    // fill form
    private var fields: List<PdfFormField> = emptyList()
    private var fieldIndex = -1

    // search
    private var results: List<SearchResult> = emptyList()
    private var resultIndex = 0
    private var query = ""

    // read aloud and auto-scroll
    private var playback = Playback.None
    private var readAloud: ReadAloud? = null
    private var lastFrame = 0L
    private var sinceRender = 0L

    private val thumbnails = HashMap<Int, Bitmap>()
    private val toolAdapter by lazy { AnnotateToolAdapter() }
    private val matchAdapter by lazy { SearchMatchAdapter() }
    private val overlay by lazy { PdfOverlayView(this, binding.pdfView) }
    private val contents by lazy {
        PdfContentsPanel(binding.contents, binding.pdfView, prefs, file.path, ::loadThumbnail) { page ->
            setMode(Mode.Read)
            binding.pdfView.jumpTo(page, false)
        }
    }

    private val annotate by lazy {
        AnnotateMode(overlay, object : AnnotateMode.Host {
            override val color: Int get() = inkColor
            override val widthPt: Float get() = inkWidth
            override fun askText(done: (String, Float) -> Unit) = askText { done(it, textSize) }
            override fun askNote(done: (String) -> Unit) = askNote("", done)
            override fun annotationTapped(info: PdfAnnotationInfo, movable: Boolean) = editNote(info)
            override fun changed() = edited()
            override fun message(text: CharSequence) = toast(text.toString())
        })
    }

    private val form by lazy {
        FormMode(overlay, object : FormMode.Host {
            override fun edit(field: PdfFormField) = editField(field)
            override fun changed() = formEdited()
        })
    }

    private val organizeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // The file changed on disk: open it again near the same page.
            if (result.resultCode == RESULT_OK) {
                thumbnails.clear()
                contents.refreshPages()
                loadPdf(binding.pdfView.currentPage)
            }
        }

    private val pictureLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            launchMain {
                val bitmap = loadPicture(uri, MAX_PICTURE_PX)
                if (bitmap == null) toast(R.string.error_file_open_generic) else placeAtCentre(bitmap, PICTURE_WIDTH)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        password = savedInstanceState?.getString(STATE_PASSWORD)
        // The Night reading theme darkens the bars too, whatever the app theme is.
        if (PdfReadingPrefs(this).theme == PdfReadingPrefs.THEME_NIGHT) {
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        }
        if (savedInstanceState == null) {
            pendingTool = intent.getStringExtra(EXTRA_TOOL)?.let { name -> Tool.entries.firstOrNull { it.name == name } }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PASSWORD, password)
    }

    override fun backPressed() {
        when {
            mode != Mode.Read -> closeMode()
            playback != Playback.None -> stopPlayback()
            else -> finish()
        }
    }

    override fun initialize() {
        repository.markOpened(file.path)
    }

    override fun ActivityPdfReaderBinding.setData() {
        // Buttons in the colour of PDF files, as in the dialogs (DocumentAccent).
        btnModeAction.fillHostAccent()
        btnFieldNext.fillHostAccent()
        val pdf = ColorStateList.valueOf(getColor(R.color.color_file_pdf))
        btnPlayToggle.imageTintList = pdf
        progress.indeterminateTintList = pdf
        layoutTop.updatePadding(top = statusBarHeight)
        tvName.text = file.name
        tvName.isSelected = true
        layoutOverlay.addView(overlay)
        rcvAnnotateTools.adapter = toolAdapter
        rcvMatches.adapter = matchAdapter
        // The signature to place is the picked one; the tile shows it ringed in blue.
        btnAddSignature.isSelected = true
        toolAdapter.submitList(AnnotateTool.entries)
        layoutTextColors.addColorDots(TEXT_COLORS) { color ->
            textColor = color
            render()
        }
        layoutInkColors.addColorDots(INK_COLORS) { color ->
            inkColor = color
            pdfView.setInkColor(color)
            render()
        }
        seekInkWidth.progress = (inkWidth - MIN_INK_WIDTH).toInt()
        annotate.onSelection = { renderMarkup() }
        render()
        loadPdf()
    }

    override fun ActivityPdfReaderBinding.onClick() {
        btnBack.clickSingle { finish() }
        btnSearch.click { setMode(Mode.Search) }
        btnContents.click { setMode(Mode.Contents) }
        btnBookmark.click { toggleBookmark() }
        seekPage.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) tvPagePill.text = getString(R.string.page_count_short, progress + 1, pdfView.pageCount)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (loaded) pdfView.jumpTo(seekBar.progress, false)
            }
        })
        matchAdapter.onMatchClick { index ->
            resultIndex = index
            pdfView.jumpToSearchResult(results[index].page, query)
            render()
        }
        btnNormal.click { setReadingTheme(PdfReadingPrefs.THEME_NORMAL) }
        btnMore.clickSingle { showMoreSheet() }

        btnEdit.click { setMode(Mode.Text) }
        btnAnnotate.click { setMode(Mode.Annotate) }
        btnSign.click { startSigning() }
        btnFill.click { startForm() }

        btnModeClose.click { closeMode() }
        btnModeAction.clickSingle { saveEdits { setMode(Mode.Read) } }
        btnFormDone.clickSingle { saveEdits { setMode(Mode.Read) } }
        btnUndo.click {
            pdfView.undoEdit()
            edited()
        }
        btnRedo.click {
            pdfView.redoEdit()
            edited()
        }

        // add text
        btnTextSmaller.click {
            textSize = (textSize - 1).coerceAtLeast(MIN_TEXT_SIZE)
            render()
        }
        btnTextLarger.click {
            textSize = (textSize + 1).coerceAtMost(MAX_TEXT_SIZE)
            render()
        }

        // annotate
        toolAdapter.onToolClick(::pickAnnotateTool)
        seekInkWidth.onProgress { progress ->
            inkWidth = MIN_INK_WIDTH + progress
            pdfView.setInkWidth(inkWidth)
        }
        btnMarkHighlight.click { markSelection(TextMarkupType.HIGHLIGHT) }
        btnMarkUnderline.click { markSelection(TextMarkupType.UNDERLINE) }
        btnMarkStrike.click { markSelection(TextMarkupType.STRIKETHROUGH) }
        btnMarkDuplicate.click { lastSignature?.let { placeAtCentre(it, SIGNATURE_WIDTH) } }
        btnMarkDelete.click { annotate.deleteSelected() }

        // sign
        btnAddSignature.clickSingle { pickSignature() }
        btnSignDate.click {
            addTextAtCentre(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()), SIGN_TEXT_SIZE)
        }
        btnSignInitials.clickSingle {
            TextInputDialog(this@PdfReaderActivity, R.string.sign_initials, R.string.sign_initials, R.string.done) {
                addTextAtCentre(it, INITIALS_TEXT_SIZE)
            }.show()
        }

        // fill form
        btnFieldPrevious.click { moveField(-1) }
        btnFieldNext.click { moveField(1) }

        // search
        btnSearchClose.click { closeMode() }
        btnSearchPrevious.click { moveResult(-1) }
        btnSearchNext.click { moveResult(1) }
        edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            search(edtSearch.text.toString().trim())
            true
        }

        // read aloud and auto-scroll
        btnPlayClose.click { stopPlayback() }
        btnPlayPrevious.click {
            if (playback == Playback.AutoScroll) changeScrollSpeed(-1) else readAloud?.previous()
        }
        btnPlayNext.click {
            if (playback == Playback.AutoScroll) changeScrollSpeed(1) else readAloud?.next()
        }
        btnPlayToggle.click {
            readAloud?.let { if (it.playing) it.pause() else it.resume() }
        }
    }

    // ---- document ----

    private fun loadPdf(page: Int = prefs.lastPage(file.path).coerceAtLeast(0)) {
        if (!file.isFile || !file.canRead()) return showOpenError(R.string.error_file_not_found)
        loaded = false
        binding.progress.visible()
        val accent = ContextCompat.getColor(this, R.color.primary)
        val horizontal = prefs.horizontal
        binding.pdfView.fromFile(file)
            .password(password)
            .enableAnnotationRendering(true)
            .defaultPage(page)
            .pageSeparatorSpacing(PAGE_SPACING)
            .pageFling(true)
            .pageFlingByVelocity(true)
            .enableSwipe(true)
            .swipeHorizontal(horizontal)
            .pageSnap(horizontal)
            .autoSpacing(horizontal)
            .enableDoubletap(false)
            .enableTextSelection(true)
            .enableMagnifier(true)
            // Copy only in the viewer's own popup: markup is offered by the Highlight tool.
            .enableTextMarkup(false)
            .setInkColor(inkColor)
            .setInkWidth(inkWidth)
            .onInkChange { _, _ -> edited() }
            .onEditChange { _, _ -> edited() }
            .onSelectionChange { renderMarkup() }
            .selectionHandleColor(accent)
            .selectionHighlightColor(ColorUtils.setAlphaComponent(accent, 0x40))
            .searchHighlightColor(SEARCH_HIGHLIGHT_COLOR)
            .searchHighlightCornerRadius(resources.displayMetrics.density * 2)
            .onTap(object : OnTapListener {
                override fun onTap(e: MotionEvent?): Boolean = e != null && onPageTap(e.x, e.y)
            })
            .onLongPress(object : OnLongPressListener {
                override fun onLongPress(e: MotionEvent?) {
                    e?.let { onPageLongPress(it.x, it.y) }
                }
            })
            .onLoad {
                if (isFinishing || isDestroyed) return@onLoad
                loaded = true
                binding.progress.gone()
                // loading resets the page colours
                applyTheme()
                render()
                pendingTool?.let(::openPendingTool)
                pendingTool = null
            }
            .onPageChange { current, _ ->
                if (isFinishing || isDestroyed) return@onPageChange
                prefs.setLastPage(file.path, current)
                renderPage()
            }
            .onError { error ->
                if (isFinishing || isDestroyed) return@onError
                binding.progress.gone()
                if (isPasswordError(error)) askPassword() else showOpenError(openErrorMessage(error))
            }
            .load()
    }

    private fun isPasswordError(error: Throwable?): Boolean =
        generateSequence(error) { it.cause }.take(8).any { it is PdfPasswordException }

    private fun askPassword() {
        var submitted = false
        OpenPasswordDialog(this, file, wrong = password != null) { entered ->
            submitted = true
            password = entered
            loadPdf()
        }.apply {
            setOnDismissListener { if (!submitted && !isFinishing) finish() }
            show()
        }
    }

    /** What to tell the user about [error]: the viewer reports by exception type, not by a reason. */
    @StringRes
    private fun openErrorMessage(error: Throwable?): Int {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        return when {
            causes.any { it is OutOfMemoryError } -> R.string.error_file_too_large_memory
            causes.any { it is FileNotFoundException || it is SecurityException } -> R.string.error_file_not_found
            // pdfium could not read the file as a PDF (empty, cut short, not a PDF)
            causes.any { it is IOException } -> R.string.error_file_damaged
            else -> R.string.error_file_open_generic
        }
    }

    private fun showOpenError(@StringRes message: Int) {
        MessageDialog(this, R.string.cant_open_file, message, R.string.fo_okay, alert = true).apply {
            setOnDismissListener { if (!isFinishing) finish() }
            show()
        }
    }

    private fun openPendingTool(tool: Tool) {
        when (tool) {
            Tool.Sign -> startSigning()
            Tool.Annotate -> setMode(Mode.Annotate)
            Tool.FillForm -> startForm()
            Tool.AddText -> setMode(Mode.Text)
            else -> Unit
        }
    }

    /** Writes the edits over the file, then runs [then]; nothing to write counts as saved. */
    private fun saveEdits(then: () -> Unit) {
        val pdfView = binding.pdfView
        if (!loaded || !pdfView.hasUnsavedChanges) return then()
        binding.progress.visible()
        launchMain {
            val saved = withContext(Dispatchers.IO) { runCatching { pdfView.saveDocument(file) }.getOrDefault(false) }
            if (isFinishing || isDestroyed) return@launchMain
            binding.progress.gone()
            if (!saved) {
                toast(R.string.save_failed)
                return@launchMain
            }
            MediaScannerConnection.scanFile(applicationContext, arrayOf(file.absolutePath), null, null)
            repository.refresh()
            thumbnails.clear()
            contents.refreshPages()
            toast(R.string.saved_changes)
            then()
        }
    }

    // ---- OCR of a scanned page ----

    /** A long press that selected no text: on a scanned page, offer to recognize its text. */
    private fun onPageLongPress(x: Float, y: Float) {
        val pdfView = binding.pdfView
        if (!loaded || recognizing || mode != Mode.Read || pdfView.hasTextSelection()) return
        val page = pdfView.pageAt(x, y) ?: return
        if (!pdfView.isScannedPage(page)) return
        if (ocrAgreed) return recognizePage(page)
        MessageDialog(
            this, R.string.ocr_page_title, R.string.ocr_page_message,
            R.string.ocr_page_recognize, R.string.file_action_cancel
        ) {
            ocrAgreed = true
            recognizePage(page)
        }.show()
    }

    /**
     * Writes the text of [page] into the opened file itself, then opens it again at that page.
     * A protected file (the new one would lose its password) or one this app cannot write goes to
     * the OCR tool instead, which makes a copy of the whole document.
     */
    private fun recognizePage(page: Int) {
        if (password != null || !file.canWrite()) return openTool(Tool.Ocr, listOf(file.path))
        saveEdits {
            recognizing = true
            binding.progress.visible()
            launchMain {
                val temp = File(cacheDir, "ocr_page.pdf")
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val pages = toolbox.recognizePage(file, page, temp)
                        if (pages > 0) temp.copyTo(file, overwrite = true)
                        pages
                    }.also { temp.delete() }
                }
                recognizing = false
                if (isFinishing || isDestroyed) return@launchMain
                binding.progress.gone()
                val pages = result.getOrNull() ?: return@launchMain toast(R.string.ocr_page_failed)
                if (pages == 0) return@launchMain toast(R.string.ocr_page_none)
                MediaScannerConnection.scanFile(applicationContext, arrayOf(file.absolutePath), null, null)
                repository.refresh()
                thumbnails.clear()
                contents.refreshPages()
                toast(R.string.ocr_page_done)
                loadPdf(page)
            }
        }
    }

    private fun loadThumbnail(page: Int, onReady: (Bitmap) -> Unit) {
        thumbnails[page]?.let { return onReady(it) }
        launchMain {
            val bitmap = runCatching { toolbox.renderPage(file, page, THUMBNAIL_WIDTH, password) }.getOrNull()
                ?: return@launchMain
            thumbnails[page] = bitmap
            onReady(bitmap)
        }
    }

    // ---- modes ----

    private fun setMode(next: Mode) {
        if (!loaded && next != Mode.Read) return
        val pdfView = binding.pdfView
        stopPlayback()
        if (mode == Mode.Search && next != Mode.Search) clearSearch()
        mode = next
        pdfView.setDrawingMode(false)
        pdfView.clearTextSelection()
        annotate.clearSelection()
        overlay.mode = null
        when (next) {
            Mode.Text, Mode.Sign -> {
                annotate.tool = AnnotateMode.Tool.SELECT
                overlay.mode = annotate
            }
            Mode.Annotate -> applyAnnotateTool()
            Mode.Form -> {
                overlay.mode = form
                form.refresh()
                readFields()
            }
            Mode.Search -> {
                binding.edtSearch.requestFocus()
                showKeyboard(binding.edtSearch)
            }
            Mode.Contents, Mode.Read -> Unit
        }
        render()
    }

    /** Leaving a mode without saving: edits are dropped by opening the file again. */
    private fun closeMode() {
        if (mode == Mode.Search || mode == Mode.Contents || !binding.pdfView.hasUnsavedChanges) {
            return setMode(Mode.Read)
        }
        MessageDialog(
            this, R.string.discard_changes_title, R.string.discard_changes_message,
            R.string.discard, R.string.file_action_cancel
        ) {
            setMode(Mode.Read)
            loadPdf(binding.pdfView.currentPage)
        }.show()
    }

    /** A tap on the page that no edit gesture took: what the current tool does with it. */
    private fun onPageTap(x: Float, y: Float): Boolean {
        val pdfView = binding.pdfView
        val tool = annotateTool.takeIf { mode == Mode.Annotate }
        when {
            mode == Mode.Text -> askText { text -> annotate.addTextAt(x, y, text, textSize, textColor) }
            tool == AnnotateTool.Text -> askText { text -> annotate.addTextAt(x, y, text, textSize, inkColor) }
            tool == AnnotateTool.Note -> {
                val (page, point) = pdfView.viewToPagePoint(x, y) ?: return false
                askNote("") { text -> if (pdfView.addNote(page, point.x, point.y, text) != null) edited() }
            }
            else -> return false
        }
        return true
    }

    private fun askText(done: (String) -> Unit) {
        TextInputDialog(this, R.string.add_text, R.string.enter_text, R.string.done, onConfirm = done).show()
    }

    private fun askNote(value: String, done: (String) -> Unit) {
        TextInputDialog(this, R.string.annot_note, R.string.note_hint, R.string.done, value, onConfirm = done).show()
    }

    /** A tap on a note that is already in the file: change its text. */
    private fun editNote(info: PdfAnnotationInfo) {
        if (info.subtype != SUBTYPE_NOTE) return
        val pdfView = binding.pdfView
        askNote(pdfView.getAnnotationText(info).orEmpty()) { text ->
            if (pdfView.setAnnotationText(info, text)) edited()
        }
    }

    private fun edited() {
        overlay.invalidate()
        renderEditButtons()
        renderMarkup()
    }

    // ---- annotate ----

    private fun pickAnnotateTool(tool: AnnotateTool) {
        when (tool) {
            AnnotateTool.Shapes -> {
                val shapes = listOf(
                    AnnotateMode.Tool.RECT to R.string.shape_rectangle,
                    AnnotateMode.Tool.ELLIPSE to R.string.shape_ellipse,
                    AnnotateMode.Tool.LINE to R.string.shape_line,
                    AnnotateMode.Tool.ARROW to R.string.shape_arrow
                )
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.annot_shapes)
                    .setItems(shapes.map { getString(it.second) }.toTypedArray()) { _, index ->
                        shape = shapes[index].first
                        annotateTool = tool
                        applyAnnotateTool()
                    }
                    .show()
            }
            AnnotateTool.Image -> pictureLauncher.launch("image/*")
            else -> {
                annotateTool = tool
                applyAnnotateTool()
            }
        }
    }

    /** The pen draws in the viewer itself and Highlight uses its text selection; the rest go through the overlay. */
    private fun applyAnnotateTool() {
        val pdfView = binding.pdfView
        pdfView.setDrawingMode(annotateTool == AnnotateTool.Pen)
        pdfView.clearTextSelection()
        when (annotateTool) {
            AnnotateTool.Pen, AnnotateTool.Highlight -> {
                annotate.clearSelection()
                overlay.mode = null
            }
            // The eraser follows the finger over the page, so the overlay takes its touches.
            AnnotateTool.Eraser -> {
                annotate.clearSelection()
                annotate.tool = AnnotateMode.Tool.ERASER
                overlay.mode = annotate
            }
            AnnotateTool.Shapes -> {
                annotate.tool = shape
                overlay.mode = annotate
            }
            else -> {
                annotate.tool = AnnotateMode.Tool.SELECT
                overlay.mode = annotate
            }
        }
        toolAdapter.selected = annotateTool
        renderMarkup()
    }

    private fun markSelection(type: TextMarkupType) {
        val color = if (type == TextMarkupType.HIGHLIGHT) ColorUtils.setAlphaComponent(inkColor, HIGHLIGHT_ALPHA) else inkColor
        if (binding.pdfView.addTextMarkupToSelection(type, color)) {
            binding.pdfView.clearTextSelection()
            edited()
        }
    }

    /** [bitmap] goes in the middle of the screen, [widthPt] wide, selected so it can be moved. */
    private fun placeAtCentre(bitmap: Bitmap, widthPt: Float) {
        if (mode == Mode.Annotate) {
            annotateTool = AnnotateTool.Select
            applyAnnotateTool()
        }
        overlay.mode = annotate
        annotate.placePicture(bitmap, widthPt)
        annotate.placePictureAt(overlay.width / 2f, overlay.height / 2f)
    }

    private fun addTextAtCentre(text: String, sizePt: Float) {
        annotate.addTextAt(overlay.width / 2f, overlay.height / 2f, text, sizePt, TEXT_COLORS.first())
    }

    // ---- sign ----

    private fun startSigning() {
        if (!loaded) return
        setMode(Mode.Sign)
        pickSignature()
    }

    private fun pickSignature() {
        SignatureBottomSheet(this) { bitmap ->
            lastSignature = bitmap
            placeAtCentre(bitmap, SIGNATURE_WIDTH)
        }.show()
    }

    // ---- fill form ----

    private fun startForm() {
        if (!loaded) return
        if (!binding.pdfView.hasForm) return toast(R.string.form_none)
        setMode(Mode.Form)
    }

    private fun readFields() {
        val pdfView = binding.pdfView
        fields = (0 until pdfView.pageCount)
            .flatMap { page -> runCatching { pdfView.getFormFields(page) }.getOrDefault(emptyList()) }
            .filter { !it.readOnly && it.type != PdfFormField.TYPE_PUSHBUTTON }
        fieldIndex = fieldIndex.coerceIn(-1, fields.lastIndex)
    }

    private fun formEdited() {
        form.refresh()
        readFields()
        edited()
        render()
    }

    private fun moveField(by: Int) {
        if (fields.isEmpty()) return
        fieldIndex = (fieldIndex + by).mod(fields.size)
        val field = fields[fieldIndex]
        if (field.page != binding.pdfView.currentPage) binding.pdfView.jumpTo(field.page, true)
        render()
        if (field.type != PdfFormField.TYPE_CHECKBOX && field.type != PdfFormField.TYPE_RADIOBUTTON) editField(field)
    }

    private fun editField(field: PdfFormField) {
        val pdfView = binding.pdfView
        fields.indexOfFirst { it.page == field.page && it.index == field.index }
            .takeIf { it >= 0 }?.let { fieldIndex = it }
        render()
        when (field.type) {
            PdfFormField.TYPE_COMBOBOX, PdfFormField.TYPE_LISTBOX -> {
                if (field.options.isEmpty()) return
                MaterialAlertDialogBuilder(this)
                    .setTitle(field.name.ifBlank { getString(R.string.tool_fill_form) })
                    .setItems(field.options.toTypedArray()) { _, index ->
                        if (pdfView.setFormChoice(field, index)) formEdited()
                    }
                    .show()
            }
            PdfFormField.TYPE_SIGNATURE -> SignatureBottomSheet(this) { bitmap ->
                // the signature fills the field, keeping its proportions
                val rect = field.rect
                val scale = minOf((rect.right - rect.left) / bitmap.width, (rect.top - rect.bottom) / bitmap.height)
                val width = bitmap.width * scale
                val height = bitmap.height * scale
                val x = (rect.left + rect.right) / 2
                val y = (rect.top + rect.bottom) / 2
                val target = RectF(x - width / 2, y + height / 2, x + width / 2, y - height / 2)
                if (pdfView.addImage(field.page, target, bitmap) != null) formEdited()
            }.show()
            else -> TextInputDialog(
                this, R.string.tool_fill_form, R.string.form_field_default, R.string.done,
                value = field.value, allowEmpty = true
            ) { text ->
                if (pdfView.setFormText(field, text)) formEdited() else toast(R.string.save_failed)
            }.show()
        }
    }

    // ---- search ----

    private fun search(text: String) {
        if (!loaded || text.isEmpty()) return
        hideKeyboard()
        query = text
        binding.progress.visible()
        launchMain {
            val found = runCatching { binding.pdfView.searchDocument(text) }.getOrDefault(emptyList())
            binding.progress.gone()
            if (mode != Mode.Search) return@launchMain
            results = found
            if (found.isEmpty()) {
                binding.pdfView.clearSearch()
                toast(R.string.search_no_results)
            } else {
                binding.pdfView.setSearchQuery(text)
                // start from the page being read
                val page = binding.pdfView.currentPage
                resultIndex = found.indexOfFirst { it.page >= page }.coerceAtLeast(0)
                binding.pdfView.jumpToSearchResult(found[resultIndex].page, text)
            }
            render()
        }
    }

    /** The star in the top bar: the page goes into the bookmarks, or out again. */
    private fun toggleBookmark() {
        if (!loaded) return
        val page = binding.pdfView.currentPage
        val marks = prefs.bookmarks(file.path)
        prefs.setBookmarks(
            file.path,
            if (marks.any { it.page == page }) marks.filter { it.page != page }
            else (marks + PdfBookmark(page, getString(R.string.page_number, page + 1))).sortedBy { it.page }
        )
        renderPage()
    }

    private fun moveResult(by: Int) {
        if (results.isEmpty()) return
        resultIndex = (resultIndex + by).mod(results.size)
        binding.pdfView.jumpToSearchResult(results[resultIndex].page, query)
        render()
    }

    private fun clearSearch() {
        hideKeyboard()
        results = emptyList()
        query = ""
        binding.edtSearch.setText("")
        binding.pdfView.clearSearch()
    }

    // ---- tools and reading sheet ----

    private fun showMoreSheet() {
        if (!loaded) return
        PdfToolsBottomSheet(
            this, prefs.theme, prefs.horizontal,
            onTheme = ::setReadingTheme,
            onLayout = { horizontal ->
                prefs.horizontal = horizontal
                loadPdf(binding.pdfView.currentPage)
            },
            onAction = { action ->
                when (action) {
                    ReaderAction.TextView -> startActivity(
                        PdfReflowActivity.intent(this, file.path, password, binding.pdfView.currentPage)
                    )
                    ReaderAction.ReadAloud -> startReadAloud()
                    ReaderAction.AutoScroll -> startAutoScroll()
                    ReaderAction.Share -> if (!shareFile(file.path)) toast(R.string.file_share_failed)
                    ReaderAction.Rename -> renameFile()
                    ReaderAction.Info -> FileInfoBottomSheet(this, document()).show()
                    ReaderAction.Organize ->
                        organizeLauncher.launch(PdfOrganizeActivity.intent(this, file.path, password))
                }
            },
            onTool = { tool -> openTool(tool, listOf(file.path)) }
        ).show()
    }

    /** The open file as the lists know it, or a stand-in when it is not in MediaStore (opened from another app). */
    private fun document(): RecentDocument =
        (repository.documents.value as? DataResponse.DataSuccess)?.data?.firstOrNull { it.path == file.path }
            ?: RecentDocument(path = file.path, lastModified = file.lastModified(), size = file.length(), type = DocumentType.Pdf)

    /** Renames the file through the repository, then reopens it under its new name. */
    private fun renameFile() {
        val doc = document()
        RenameDialog(this, doc) { newName ->
            launchMain {
                repository.rename(doc, newName)
                    .onSuccess { renamed ->
                        prefs.moved(file.path, renamed.path)
                        start(this@PdfReaderActivity, renamed.path)
                        finish()
                    }
                    .onFailure { toast(R.string.file_rename_failed) }
            }
        }.show()
    }

    private fun setReadingTheme(theme: Int) {
        prefs.theme = theme
        // Recreates the screen when the bars have to change colour.
        delegate.localNightMode = if (theme == PdfReadingPrefs.THEME_NIGHT) AppCompatDelegate.MODE_NIGHT_YES
        else AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
        applyTheme()
    }

    private fun applyTheme() {
        val theme = prefs.theme
        val pdfView = binding.pdfView
        pdfView.setNightMode(theme == PdfReadingPrefs.THEME_NIGHT)
        pdfView.setSepiaMode(theme == PdfReadingPrefs.THEME_SEPIA)
        val background = ContextCompat.getColor(
            this, when (theme) {
                PdfReadingPrefs.THEME_NIGHT -> R.color.color_reader_night
                PdfReadingPrefs.THEME_SEPIA -> R.color.color_reader_sepia
                else -> R.color.color_surface_track
            }
        )
        binding.main.setBackgroundColor(background)
        pdfView.setBackgroundColor(background)
        pdfView.invalidate()
        binding.btnNormal.visibleOrGone(theme != PdfReadingPrefs.THEME_NORMAL)
        renderPage()
    }

    // ---- read aloud and auto-scroll ----

    private fun startReadAloud() {
        if (!loaded) return
        stopPlayback()
        val pdfView = binding.pdfView
        playback = Playback.ReadAloud
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        readAloud = ReadAloud(
            this, lifecycleScope, toolbox, file, password, pdfView.pageCount,
            object : ReadAloud.Listener {
                override fun onSentence(page: Int, rects: List<RectF>) {
                    pdfView.setReadingHighlight(page, rects)
                    if (rects.isNotEmpty() && page != pdfView.currentPage) pdfView.jumpTo(page, true)
                    binding.tvPlayTitle.text = getString(R.string.read_aloud_page, page + 1)
                }

                override fun onStateChanged(playing: Boolean) {
                    binding.btnPlayToggle.setImageResource(
                        if (playing) R.drawable.ic_fo_pause else R.drawable.ic_fo_play
                    )
                }

                override fun onFinished(error: Boolean) {
                    if (error) toast(R.string.read_aloud_unavailable)
                    stopPlayback()
                }
            }
        ).also { it.start(pdfView.currentPage) }
        binding.tvPlayTitle.setText(R.string.read_aloud)
        render()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (playback != Playback.AutoScroll) return
            val pdfView = binding.pdfView
            val elapsed = if (lastFrame == 0L) 0L else (now - lastFrame) / 1_000_000
            lastFrame = now
            // Pages run along X when the layout is horizontal.
            val horizontal = prefs.horizontal
            val before = if (horizontal) pdfView.currentXOffset else pdfView.currentYOffset
            val step = -prefs.scrollSpeed * SCROLL_DP_PER_SECOND * resources.displayMetrics.density * elapsed / 1000f
            if (horizontal) pdfView.moveRelativeTo(step, 0f) else pdfView.moveRelativeTo(0f, step)
            sinceRender += elapsed
            if (sinceRender > RENDER_INTERVAL_MS) {
                sinceRender = 0
                pdfView.loadPageByOffset()
                pdfView.loadPages()
            }
            // the end of the document
            val after = if (horizontal) pdfView.currentXOffset else pdfView.currentYOffset
            if (elapsed > 0 && after == before) return stopPlayback()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startAutoScroll() {
        if (!loaded) return
        stopPlayback()
        playback = Playback.AutoScroll
        lastFrame = 0
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun changeScrollSpeed(by: Int) {
        prefs.scrollSpeed = (prefs.scrollSpeed + by).coerceIn(MIN_SCROLL_SPEED, MAX_SCROLL_SPEED)
        binding.showScrollSpeed()
    }

    /** The speed between the two buttons; a button fades once its end of the range is reached. */
    private fun ActivityPdfReaderBinding.showScrollSpeed() {
        val speed = prefs.scrollSpeed
        tvPlayTitle.text = getString(R.string.auto_scroll_speed, speed)
        tvPlaySpeed.text = speed.toString()
        btnPlayPrevious.isEnabled = speed > MIN_SCROLL_SPEED
        btnPlayNext.isEnabled = speed < MAX_SCROLL_SPEED
        btnPlayPrevious.alpha = if (btnPlayPrevious.isEnabled) 1f else DISABLED_ALPHA
        btnPlayNext.alpha = if (btnPlayNext.isEnabled) 1f else DISABLED_ALPHA
    }

    private fun stopPlayback() {
        if (playback == Playback.None) return
        val pdfView = binding.pdfView
        if (playback == Playback.AutoScroll) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            pdfView.loadPageByOffset()
            pdfView.loadPages()
        }
        readAloud?.release()
        readAloud = null
        pdfView.setReadingHighlight(-1, emptyList())
        playback = Playback.None
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    // ---- what is on screen ----

    private fun render() = with(binding) {
        val editing = mode == Mode.Text || mode == Mode.Annotate || mode == Mode.Sign || mode == Mode.Form
        barReader.visibleOrGone(mode == Mode.Read)
        barSearch.visibleOrGone(mode == Mode.Search)
        barMode.visibleOrGone(editing || mode == Mode.Contents)
        // Placing a signature has a long title and little to undo: Delete on the object does it.
        btnUndo.visibleOrGone(editing && mode != Mode.Sign)
        btnRedo.visibleOrGone(editing && mode != Mode.Sign)
        btnModeAction.visibleOrGone(editing)
        btnModeAction.setText(if (mode == Mode.Sign) R.string.done else R.string.file_action_save)
        tvBanner.visibleOrGone(mode == Mode.Form)
        contents.root.visibleOrGone(mode == Mode.Contents)
        if (mode == Mode.Contents) this@PdfReaderActivity.contents.render()

        val pageCount = pdfView.pageCount
        val (title, subtitle) = when (mode) {
            Mode.Text -> getString(R.string.add_text) to file.name
            Mode.Annotate -> getString(R.string.tool_annotate) to getString(R.string.fo_mark_it_up)
            Mode.Sign -> getString(R.string.tool_sign) to
                getString(R.string.fo_page_x_of_y, pdfView.currentPage + 1, pageCount)
            Mode.Form -> getString(R.string.fo_fill_form_title) to
                getString(R.string.fo_fields_filled, fields.count { it.isFilled }, fields.size)
            Mode.Contents -> getString(R.string.contents) to
                getString(R.string.document_meta, file.nameWithoutExtension, getString(R.string.pages_count, pageCount))
            else -> file.name to getString(R.string.pages_count, pageCount)
        }
        tvModeTitle.text = title
        tvModeSubtitle.text = subtitle
        tvModeSubtitle.visibleOrGone(subtitle.isNotEmpty())

        bottomReader.visibleOrGone(mode == Mode.Read && playback == Playback.None)
        bottomPlay.visibleOrGone(mode == Mode.Read && playback != Playback.None)
        bottomText.visibleOrGone(mode == Mode.Text)
        bottomAnnotate.visibleOrGone(mode == Mode.Annotate)
        bottomSign.visibleOrGone(mode == Mode.Sign)
        bottomForm.visibleOrGone(mode == Mode.Form)
        btnPlayToggle.visibleOrGone(playback == Playback.ReadAloud)
        // Auto-scroll turns previous/next into slower/faster around the speed.
        val scrolling = playback == Playback.AutoScroll
        tvPlaySpeed.visibleOrGone(scrolling)
        btnPlayPrevious.setImageResource(if (scrolling) R.drawable.ic_fo_minus else R.drawable.ic_fo_back)
        btnPlayNext.setImageResource(if (scrolling) R.drawable.ic_fo_plus else R.drawable.ic_fo_back)
        btnPlayNext.rotation = if (scrolling) 0f else 180f
        if (scrolling) showScrollSpeed() else {
            btnPlayPrevious.isEnabled = true
            btnPlayNext.isEnabled = true
            btnPlayPrevious.alpha = 1f
            btnPlayNext.alpha = 1f
        }

        tvTextSize.text = getString(R.string.fo_text_size_pt, textSize.toInt())
        tvAnnotateHint.text = getString(R.string.fo_annotate_hint, getString(annotateTool.title), getString(annotateTool.hint))
        ivSignature.setImageBitmap(lastSignature)
        ivSignature.visibleOrGone(lastSignature != null)
        tvAddSignature.visibleOrGone(lastSignature == null)
        layoutTextColors.selectColorDot(textColor)
        layoutInkColors.selectColorDot(inkColor)

        val field = fields.getOrNull(fieldIndex)
        tvFieldCount.visibleOrGone(field != null)
        tvFieldCount.text = getString(R.string.fo_field_x_of_y, fieldIndex + 1, fields.size)
        tvField.text = field?.name?.ifBlank { getString(R.string.form_field_default) } ?: getString(R.string.form_hint)
        tvSearchCount.text = if (results.isEmpty()) "" else getString(R.string.search_count, resultIndex + 1, results.size)
        bottomSearch.visibleOrGone(mode == Mode.Search && results.isNotEmpty())
        if (mode == Mode.Search && results.isNotEmpty()) {
            tvMatches.text = resources.getQuantityString(R.plurals.fo_matches, results.size, results.size)
            val pages = results.distinctBy { it.page }.size
            tvMatchPages.text = resources.getQuantityString(R.plurals.fo_on_pages, pages, pages)
            matchAdapter.show(results, query, resultIndex)
            // A few rows, then the list scrolls: the page stays in sight.
            val row = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._37sdp)
            rcvMatches.updateLayoutParams {
                height = if (results.size > MATCH_ROWS) row * MATCH_ROWS + row / 2 else ViewGroup.LayoutParams.WRAP_CONTENT
            }
            rcvMatches.scrollToPosition(resultIndex)
        }

        renderEditButtons()
        renderMarkup()
        renderPage()
    }

    private val PdfFormField.isFilled: Boolean
        get() = when (type) {
            PdfFormField.TYPE_CHECKBOX, PdfFormField.TYPE_RADIOBUTTON -> checked
            else -> value.isNotBlank()
        }

    private fun renderEditButtons() = with(binding) {
        btnUndo.setEnabledLook(loaded && pdfView.canUndoEdit())
        btnRedo.setEnabledLook(loaded && pdfView.canRedoEdit())
    }

    /** The floating bar: markup for selected text with the Highlight tool, actions for a selected object. */
    private fun renderMarkup() = with(binding) {
        val marking = mode == Mode.Annotate && annotateTool == AnnotateTool.Highlight && pdfView.hasTextSelection()
        val objectPicked = (mode == Mode.Text || mode == Mode.Annotate || mode == Mode.Sign) && annotate.hasSelection
        btnMarkHighlight.visibleOrGone(marking)
        btnMarkUnderline.visibleOrGone(marking)
        btnMarkStrike.visibleOrGone(marking)
        btnMarkDuplicate.visibleOrGone(!marking && objectPicked && mode == Mode.Sign && lastSignature != null)
        btnMarkDelete.visibleOrGone(!marking && objectPicked)
        layoutMarkup.visibleOrGone(marking || objectPicked)
    }

    private fun renderPage() = with(binding) {
        val count = pdfView.pageCount
        val page = (pdfView.currentPage + 1).coerceAtMost(count.coerceAtLeast(1))
        val text = getString(R.string.fo_page_x_of_y, page, count)
        tvPage.text = when (prefs.theme) {
            PdfReadingPrefs.THEME_NIGHT -> getString(R.string.page_theme_suffix, text, getString(R.string.theme_night))
            PdfReadingPrefs.THEME_SEPIA -> getString(R.string.page_theme_suffix, text, getString(R.string.theme_sepia))
            else -> text
        }
        tvPagePill.text = getString(R.string.page_count_short, page, count)
        tvPagePill.visibleOrGone(loaded && count > 0 && mode != Mode.Contents)
        // Sign names the page the signature will land on.
        if (mode == Mode.Sign) tvModeSubtitle.text = getString(R.string.fo_page_x_of_y, page, count)
        seekPage.max = (count - 1).coerceAtLeast(0)
        if (!seekPage.isPressed) seekPage.progress = page - 1
        val marked = prefs.bookmarks(file.path).any { it.page == page - 1 }
        btnBookmark.setImageResource(if (marked) R.drawable.ic_fo_star_filled else R.drawable.ic_fo_star)
        btnBookmark.contentDescription = getString(if (marked) R.string.fo_remove_bookmark else R.string.fo_bookmark_page)
    }

    override fun onStop() {
        super.onStop()
        // Speech does not go on once the reader is off screen; the play button picks it up again.
        readAloud?.takeIf { it.playing }?.pause()
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        readAloud?.release()
        readAloud = null
        binding.pdfView.recycle()
        super.onDestroy()
    }

    companion object {
        /** Matches listed before the search list scrolls. */
        private const val MATCH_ROWS = 3
        private const val EXTRA_PATH = "extra_path"
        private const val EXTRA_TOOL = "extra_tool"
        private const val STATE_PASSWORD = "state_password"

        private val TEXT_COLORS = listOf(
            0xFF20243A.toInt(), 0xFF1D3FA8.toInt(), 0xFFD93A2F.toInt(), 0xFF16A34A.toInt(), 0xFFEA580C.toInt()
        )
        private val INK_COLORS = listOf(
            0xFFFFD84D.toInt(), 0xFFF0443A.toInt(), 0xFF22A565.toInt(), 0xFF3984FF.toInt(), 0xFFDB2777.toInt(),
            0xFF20243A.toInt()
        )
        private const val SEARCH_HIGHLIGHT_COLOR = 0x80FFD84D.toInt()
        private const val HIGHLIGHT_ALPHA = 0x66
        private const val SUBTYPE_NOTE = 1

        // sizes in PDF points
        private const val DEFAULT_TEXT_SIZE = 13f
        private const val MIN_TEXT_SIZE = 8f
        private const val MAX_TEXT_SIZE = 48f
        private const val SIGN_TEXT_SIZE = 12f
        private const val INITIALS_TEXT_SIZE = 18f
        private const val DEFAULT_INK_WIDTH = 3f
        private const val MIN_INK_WIDTH = 1f
        private const val SIGNATURE_WIDTH = 150f
        private const val PICTURE_WIDTH = 220f

        private const val MAX_PICTURE_PX = 2000
        private const val THUMBNAIL_WIDTH = 240
        private const val PAGE_SPACING = 8
        private const val SCROLL_DP_PER_SECOND = 12f
        private const val RENDER_INTERVAL_MS = 250
        private const val MIN_SCROLL_SPEED = 1
        private const val DISABLED_ALPHA = 0.3f
        private const val MAX_SCROLL_SPEED = 10

        /** [tool] (sign, annotate or fill form) starts right after the file is open. */
        fun start(context: Context, path: String, tool: Tool? = null) {
            context.startActivity(
                Intent(context, PdfReaderActivity::class.java)
                    .putExtra(EXTRA_PATH, path)
                    .putExtra(EXTRA_TOOL, tool?.name)
            )
        }
    }
}
