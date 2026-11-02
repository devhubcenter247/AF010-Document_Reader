package com.af010.creator.docreader.ui.main.reader.office

import com.af010.creator.docreader.ui.dialog.fillAccent
import com.af010.creator.docreader.ui.dialog.DocumentAccent
import com.af010.creator.docreader.utils.extensions.softColorRes
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.AppUtils
import java.util.Locale
import android.util.TypedValue
import android.graphics.Color
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.databinding.ActivityOfficeBinding
import androidx.annotation.StringRes
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.ui.dialog.DeleteDialog
import com.af010.creator.docreader.ui.dialog.TextInputDialog
import com.af010.creator.docreader.ui.main.files.sheet.FileInfoBottomSheet
import com.af010.creator.docreader.ui.main.reader.office.sheet.OfficeOption
import com.af010.creator.docreader.ui.main.reader.office.sheet.OfficeOptionsBottomSheet
import com.d010.pdf.reader.base.openFileWith
import com.d010.pdf.reader.viewmodel.DataResponse
import kotlinx.coroutines.flow.first
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.OfficeWriter
import com.af010.creator.docreader.pdf.ToolRunner
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.dialog.ChoiceDialog
import com.af010.creator.docreader.ui.dialog.DraftTimes
import com.af010.creator.docreader.ui.dialog.MessageDialog
import com.af010.creator.docreader.ui.dialog.OpenPasswordDialog
import com.af010.creator.docreader.ui.main.reader.office.sheet.CellFormatBottomSheet
import com.af010.creator.docreader.ui.main.reader.office.sheet.SaveAction
import com.af010.creator.docreader.ui.main.reader.office.sheet.SaveDocumentBottomSheet
import com.af010.creator.docreader.ui.main.reader.office.sheet.TablePickerBottomSheet
import com.af010.creator.docreader.ui.main.reader.office.sheet.WordFormatBottomSheet
import com.af010.creator.docreader.ui.main.reader.pdf.setEnabledLook
import com.d010.pdf.reader.base.shareFile
import com.editor.docsdk.DocumentEditor
import com.editor.docsdk.DocumentException
import com.editor.docsdk.DocumentType
import com.editor.docsdk.DocumentView
import com.editor.docsdk.EditAction
import com.editor.docsdk.EditContext
import com.editor.docsdk.EditDialogs
import com.editor.docsdk.EditFeature
import com.editor.docsdk.EditRequest
import com.editor.docsdk.TableSize
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ui.baselib.base.BaseActivity.KeyboardState
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visible
import com.ui.baselib.extensions.visibleOrGone
import com.wxiwei.office.editor.ui.EditDrafts
import com.wxiwei.office.editor.ui.ExcelEditPanel
import com.wxiwei.office.reader.OfficeDocumentView
import com.wxiwei.office.reader.ReaderState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Views a Word, Excel or PowerPoint file with the document SDK's [DocumentView] and, for the
 * formats it can change, edits it: the SDK does the editing, this screen is the bar around it
 * (ribbon, formula box, slide strip, find and replace, slide sorter, slideshow). Edits stay in
 * the SDK's working copy until Save.
 */
class OfficeActivity : BaseAppActivity<ActivityOfficeBinding>(ActivityOfficeBinding::inflate), DocumentAccent {
    override val fullStatus: Boolean
        get() = true

    // Tapping the page moves the caret or picks an object; the keyboard must stay up.
    override val hideKeyboardWhenTouch: Boolean
        get() = false

    private enum class Mode { View, Edit, Find, Sorter }

    override val accentColor: Int?
        get() = AppUtils.documentTypeOf(file)?.colorRes

    private val repository: DocumentRepository by inject()
    private val runner: ToolRunner by inject()
    private val file: File by lazy { File(intent.getStringExtra(EXTRA_PATH).orEmpty()) }

    private var password: String? = null
    private var loaded = false
    private var mode = Mode.View

    /** Start editing as soon as the document shows: a new file, or a restored draft. */
    private var editWhenLoaded = false

    /** Handed to [runner] by Convert to PDF; dropped here if the job never took it. */
    private var pdfWriter: OfficeWriter? = null
    private var draftChecked = false

    // editing
    private var tabs: List<RibbonTab> = emptyList()
    private var tabIndex = 0
    private val chipTexts = HashMap<EditAction, String>()
    private var formatSheet: WordFormatBottomSheet? = null
    private var photoFile: File? = null

    // find and replace
    private var findRequest: EditRequest.FindReplace? = null
    private var findFromView = false

    // slides
    private var slideCount = 0
    /** A slide command the SDK took: it reopens the deck in the background until [DocumentView.Listener.onPageChanged]. */
    private var slidesChanging = false
    private var readerJob: Job? = null
    private var startingShow = false
    /** Last answer of [DocumentEditor.hasUnsavedChanges], kept while the user types in the document. */
    private var unsaved = false
    /**
     * A ribbon command ran with the keyboard down: the SDK puts the caret after most edits and
     * opens the keyboard, which would push the ribbon up under the finger. Kept down until the
     * document is touched.
     */
    private var keepKeyboardDown = false

    private val ribbonAdapter by lazy { RibbonAdapter() }
    private val stripAdapter by lazy { SlideAdapter(grid = false, ::loadSlide) }
    private val sorterAdapter by lazy { SlideAdapter(grid = true, ::loadSlide) }

    private val documentView: DocumentView get() = binding.documentView
    private val editor: DocumentEditor? get() = documentView.editor
    private val type: DocumentType? get() = documentView.documentType

    /** The SDK's own view inside [documentView]: it has the slide pictures. */
    private val office: OfficeDocumentView?
        get() = documentView.office

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val photo = photoFile
            if (taken && photo != null) editor?.runSafely(EditAction.INSERT_PICTURE, photo)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        password = savedInstanceState?.getString(STATE_PASSWORD)
        editWhenLoaded = savedInstanceState == null && intent.getBooleanExtra(EXTRA_EDIT, false)
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PASSWORD, password)
    }

    override fun backPressed() {
        when (mode) {
            Mode.Sorter -> setMode(Mode.Edit)
            Mode.Find -> closeFind()
            Mode.Edit -> leaveEditing()
            Mode.View -> finish()
        }
    }

    override fun initialize() {
        repository.markOpened(file.path)
    }

    override fun ActivityOfficeBinding.setData() {
        layoutTop.updatePadding(top = statusBarHeight)
        tvName.text = file.name
        tvName.isSelected = true
        // The badge under it already says the type.
        tvEditName.text = file.nameWithoutExtension
        tvEditName.isSelected = true
        listOf(tvType, tvTypeEdit).forEach { badge ->
            badge.text = file.extension.uppercase(Locale.ROOT)
            val fileType = AppUtils.documentTypeOf(file)
            badge.backgroundTintList = ColorStateList.valueOf(getColor(fileType?.softColorRes ?: R.color.color_surface_variant))
            badge.setTextColor(getColor(fileType?.colorRes ?: R.color.text_secondary))
        }
        rcvRibbon.adapter = ribbonAdapter
        AppUtils.documentTypeOf(file).let { type ->
            val accent = getColor(type?.colorRes ?: R.color.primary)
            ribbonAdapter.accent = accent
            ribbonAdapter.accentSoft = getColor(type?.softColorRes ?: R.color.color_bg_selected)
            listOf(stripAdapter, sorterAdapter).forEach {
                it.accent = accent
                it.accentSoft = ribbonAdapter.accentSoft
            }
            // Every main button in the file type's colour, as in the dialogs (DocumentAccent).
            listOf(btnSave, btnEdit, btnSorterDone, btnReplaceAll).forEach { it.fillAccent(accent) }
            progress.indeterminateTintList = ColorStateList.valueOf(accent)
        }
        rcvSlides.adapter = stripAdapter
        rcvSorter.adapter = sorterAdapter

        val legacy = file.extension.lowercase() in LEGACY_EXTENSIONS
        tvBanner.visibleOrGone(legacy)
        tvBanner.text = getString(R.string.legacy_view_only, "." + file.extension.lowercase())

        documentView.listener = documentListener
        root.observeKeyboardState(this@OfficeActivity, ::fitKeyboard)
        render()
        open()
    }

    override fun ActivityOfficeBinding.onClick() {
        btnBack.clickSingle { finish() }
        btnEdit.clickSingle { startEditing() }
        btnSearch.click { startFind() }
        btnShare.clickSingle { share() }
        btnMore.clickSingle { showOptions() }

        // edit bar
        btnEditBack.click { leaveEditing() }
        btnUndo.click { editor?.runSafely(EditAction.UNDO) }
        btnRedo.click { editor?.runSafely(EditAction.REDO) }
        btnPlay.clickSingle { startShow() }
        btnSave.clickSingle { showSaveSheet() }
        ribbonAdapter.onItemClick(::onRibbonItem)
        btnTabPick.clickSingle { pickTab() }

        // formula box
        btnFormulaApply.click { applyFormula() }
        edtFormula.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            applyFormula()
            true
        }

        // find and replace
        btnFindClose.click { closeFind() }
        btnFindNext.click { findNext() }
        edtFind.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            findNext()
            true
        }
        btnReplace.click {
            val find = edtFind.text.toString()
            if (find.isEmpty()) return@click
            showFindResult(findRequest?.replace(find, edtReplace.text.toString()))
        }
        btnReplaceAll.click {
            val find = edtFind.text.toString()
            if (find.isEmpty()) return@click
            val count = findRequest?.replaceAll(find, edtReplace.text.toString()) ?: 0
            tvFindCount.text = ""
            toast(getString(R.string.replaced_count, count))
        }

        // slides
        stripAdapter.onSlideClick { slide -> documentView.goToPage(slide) }
        stripAdapter.onAddClick { runSlideAction(EditAction.ADD_SLIDE) }
        sorterAdapter.onSlideClick { slide -> documentView.goToPage(slide) }
        btnArrange.click { setMode(Mode.Sorter) }
        btnSorterClose.click { setMode(Mode.Edit) }
        btnSorterDone.click { setMode(Mode.Edit) }
        mapOf(
            btnSlideDuplicate to EditAction.DUPLICATE_SLIDE,
            btnSlideEarlier to EditAction.SLIDE_UP,
            btnSlideLater to EditAction.SLIDE_DOWN,
            btnSlideAdd to EditAction.ADD_SLIDE,
            btnSlideDelete to EditAction.DELETE_SLIDE
        ).forEach { (button, action) ->
            button.click { runSlideAction(action) }
        }
    }

    // ---- document ----

    private fun open() {
        if (!file.isFile || !file.canRead()) return showOpenError(DocumentException.Reason.NOT_FOUND)
        loaded = false
        binding.progress.visible()
        documentView.open(file, password)
    }

    private val documentListener = object : DocumentView.Listener {
        override fun onLoaded(pageCount: Int) {
            if (isFinishing || isDestroyed) return
            loaded = true
            binding.progress.gone()
            slidesChanging = false
            watchReader()
            refreshSlides(redraw = true)
            render()
            if (!draftChecked) {
                draftChecked = true
                if (offerDraft()) return
            }
            if (editWhenLoaded) {
                editWhenLoaded = false
                startEditing()
            }
        }

        override fun onPageChanged(page: Int, pageCount: Int) {
            if (isFinishing || isDestroyed) return
            // Also the end of a slide command (add, duplicate, move, delete), even when the count stays.
            refreshSlides(redraw = slidesChanging)
            slidesChanging = false
            renderPage()
        }

        override fun onError(error: DocumentException) {
            if (isFinishing || isDestroyed) return
            binding.progress.gone()
            when (error.reason) {
                DocumentException.Reason.PASSWORD_REQUIRED,
                DocumentException.Reason.PASSWORD_INCORRECT -> askPassword()
                // the document is open: only writing it failed
                DocumentException.Reason.STORAGE ->
                    if (loaded) toast(R.string.save_failed) else showOpenError(error.reason)
                else -> showOpenError(error.reason)
            }
        }

        // The SDK shows the one message of a save and has the file scanned already.
        override fun onSaved(file: File) {
            repository.refresh()
            renderState()
        }
    }

    private fun askPassword() {
        var submitted = false
        OpenPasswordDialog(this, file, wrong = password != null) { entered ->
            submitted = true
            password = entered
            open()
        }.apply {
            setOnDismissListener { if (!submitted && !isFinishing) finish() }
            show()
        }
    }

    /** The app's own dialog for a document that cannot be read, worded by what went wrong. */
    private fun showOpenError(reason: DocumentException.Reason) {
        MessageDialog(this, R.string.cant_open_file, reason.openMessage, R.string.fo_okay, alert = true).apply {
            setOnDismissListener { if (!isFinishing) finish() }
            show()
        }
    }

    /** Offers the edits kept when the app was closed before saving them; true when it asks. */
    private fun offerDraft(): Boolean {
        val draft = EditDrafts.pending(this, file) ?: return false
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        ChoiceDialog(
            this, R.string.draft_title,
            getString(R.string.draft_message, file.name),
            R.string.draft_restore, R.string.fo_open_saved_file,
            times = DraftTimes(format.format(Date(draft.lastModified())), format.format(Date(file.lastModified()))),
            onPrimary = {
                if (EditDrafts.restore(this, file)) {
                    editWhenLoaded = true
                    open()
                } else {
                    toast(R.string.draft_restore_failed)
                }
            },
            onSecondary = { EditDrafts.delete(this, file) }
        ).show()
        return true
    }

    private fun share() {
        val editor = editor
        if (editor != null && editor.hasUnsavedChanges() && !editor.save()) return
        if (!shareFile(file.path)) toast(R.string.file_share_failed)
    }

    // ---- options ----

    /** This file in the app's library, when it is one of the scanned documents. */
    private fun libraryDocument(): RecentDocument? =
        (repository.documents.value as? DataResponse.DataSuccess)?.data?.firstOrNull { it.path == file.path }

    private fun showOptions() {
        if (!loaded) return
        val doc = libraryDocument()
        launchMain {
            val favorite = doc != null && doc.mediaId in repository.favoriteIds.first()
            val available = buildSet {
                if (type == DocumentType.POWERPOINT) add(OfficeOption.Slideshow)
                if (documentView.pageCount > 1) add(OfficeOption.GoTo)
                if (canConvertToPdf) add(OfficeOption.ToPdf)
                add(OfficeOption.OpenWith)
                // Favourite, info and delete work on the library's entry of the file.
                if (doc != null) addAll(listOf(OfficeOption.Favorite, OfficeOption.Info, OfficeOption.Delete))
            }
            OfficeOptionsBottomSheet(this@OfficeActivity, file.name, available, favorite, goToLabel()) { option ->
                when (option) {
                    OfficeOption.Slideshow -> startShow()
                    OfficeOption.GoTo -> askPage()
                    OfficeOption.ToPdf -> convertToPdf()
                    OfficeOption.OpenWith -> if (!openFileWith(file.path)) toast(R.string.file_open_with_failed)
                    OfficeOption.Favorite -> doc?.let { launchMain { repository.setFavorite(it, !favorite) } }
                    OfficeOption.Info -> doc?.let { FileInfoBottomSheet(this@OfficeActivity, it).show() }
                    OfficeOption.Delete -> doc?.let(::confirmDelete)
                }
            }.show()
        }
    }

    @StringRes
    private fun goToLabel(): Int = when (type) {
        DocumentType.EXCEL -> R.string.go_to_sheet
        DocumentType.POWERPOINT -> R.string.go_to_slide
        else -> R.string.go_to_page
    }

    private fun askPage() {
        val count = documentView.pageCount
        TextInputDialog(
            this, goToLabel(), goToLabel(), R.string.done, (documentView.currentPage + 1).toString()
        ) { text ->
            val page = text.toIntOrNull()
            if (page == null || page !in 1..count) toast(getString(R.string.go_to_invalid, count))
            else documentView.goToPage(page - 1)
        }.show()
    }

    private fun confirmDelete(doc: RecentDocument) {
        DeleteDialog(this, 1) {
            launchMain {
                if (repository.delete(doc).isSuccess) finish() else toast(R.string.file_delete_failed)
            }
        }.show()
    }

    // ---- convert to PDF ----

    private val canConvertToPdf: Boolean
        get() = OfficePdf.canWrite(office)

    /**
     * The document as shown (edits not saved yet included) into a PDF drawn as vectors, by the same
     * job as the Office to PDF tool. This screen stays under Processing: the pages come from it.
     */
    private fun convertToPdf() {
        val office = office
        if (!loaded || office == null || !canConvertToPdf) return toast(R.string.convert_pdf_unavailable)
        pdfWriter = OfficeWriter(file.path) { output, progress -> OfficePdf.write(office, output, progress) }
        runner.officeWriter = pdfWriter
        ProcessingActivity.start(this, ToolRequest.OfficeToPdf(file.path, file.nameWithoutExtension))
    }

    // ---- editing ----

    private fun startEditing(): Boolean {
        if (editor != null) return true
        if (!loaded) return false
        val editor = documentView.startEditing(EditFeature.all(), false) ?: run {
            toast(R.string.error_file_open_generic)
            return false
        }
        // The SDK keeps the caret above this bar and lifts its parent over the keyboard.
        editor.panel.toolbar = binding.layoutRibbon
        // The ribbon has the SDK's context bar commands: keep that bar only for text typed in a
        // slide, where the SDK hides the ribbon.
        editor.contextCommands = DocumentEditor.ContextCommands { context, defaults ->
            if (context == EditContext.SHAPE_TEXT) defaults else emptyList()
        }
        editor.dialogs = EditDialogs(::onEditRequest)
        editor.listener = object : DocumentEditor.Listener {
            override fun onStateChanged() {
                // The SDK says it right after opening the keyboard: closed in the same frame, it never shows.
                if (keepKeyboardDown) hideKeyboard()
                renderState()
            }
        }
        tabs = Ribbons.of(editor)
        tabIndex = 0
        chipTexts.clear()
        buildTabs()
        setMode(Mode.Edit)
        return true
    }

    /** Back from editing: to viewing, after asking what to do with unsaved edits. */
    private fun leaveEditing() {
        val editor = editor ?: return setMode(Mode.View)
        if (!editor.hasUnsavedChanges()) return stopEditing()
        ChoiceDialog(
            this, R.string.unsaved_title,
            getString(R.string.unsaved_message, file.name),
            R.string.fo_save_and_leave, R.string.fo_leave_without_saving, R.string.keep_editing,
            secondaryDanger = true,
            onPrimary = { if (editor.save()) stopEditing() },
            onSecondary = {
                EditDrafts.delete(this, file)
                stopEditing()
            }
        ).show()
    }

    private fun stopEditing() {
        hideKeyboard()
        formatSheet = null
        findRequest = null
        // Dropped edits make the SDK read the file again: the document loads once more.
        documentView.stopEditing()
        setMode(Mode.View)
    }

    private fun onRibbonItem(item: RibbonItem) {
        val editor = editor ?: return
        // A sheet opens at the bottom of the screen, where the keyboard is.
        if (item.extra == RibbonExtra.Format || item.extra == RibbonExtra.Table) hideKeyboard()
        when (item.extra) {
            RibbonExtra.Format -> if (type == DocumentType.EXCEL) {
                CellFormatBottomSheet(this, editor, excelPanel?.selectedRange.orEmpty()).show()
            } else {
                WordFormatBottomSheet(this, editor) { chipTexts[it] }.also { formatSheet = it }.show()
            }
            RibbonExtra.Table -> TablePickerBottomSheet(
                this,
                onInsert = { rows, columns -> editor.runSafely(EditAction.INSERT_TABLE, TableSize(rows, columns)) },
                onCancel = {}
            ).show()
            RibbonExtra.Camera -> takePhoto()
            RibbonExtra.Slideshow -> startShow()
            null -> item.action?.let { action ->
                keepKeyboardDown = action !in TYPING_ACTIONS && !binding.root.currentKeyboardState().visible
                editor.runSafely(action)
                if (keepKeyboardDown) hideKeyboard()
            }
        }
    }

    /** The questions of the SDK answered with the app's own UI; false leaves one to the SDK's dialog. */
    private fun onEditRequest(request: EditRequest): Boolean = when (request) {
        is EditRequest.FindReplace -> {
            findRequest = request
            setMode(Mode.Find)
            true
        }
        is EditRequest.Font -> {
            pickOne(request.title, request.fonts, request.fonts.indexOf(request.current), request::cancel) { font ->
                chipTexts[EditAction.FONT] = font
                request.answer(font)
            }
            true
        }
        is EditRequest.FontSize -> {
            pickOne(request.title, request.sizes.map(::sizeText), -1, request::cancel) { size ->
                chipTexts[EditAction.FONT_SIZE] = size
                request.answer(size.toFloat())
            }
            true
        }
        else -> false
    }

    private fun sizeText(points: Float) = if (points % 1f == 0f) points.toInt().toString() else points.toString()

    private fun pickOne(title: String, options: List<String>, selected: Int, onCancel: () -> Unit, onPick: (String) -> Unit) {
        var picked = false
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), selected) { dialog, index ->
                picked = true
                dialog.dismiss()
                onPick(options[index])
                renderState()
            }
            .setOnDismissListener { if (!picked) onCancel() }
            .show()
    }

    private fun takePhoto() {
        val photo = File(cacheDir, "office-photo-${System.currentTimeMillis()}.jpg")
        photoFile = photo
        runCatching {
            cameraLauncher.launch(FileProvider.getUriForFile(this, "$packageName.fileprovider", photo))
        }.onFailure { toast(R.string.error_file_open_generic) }
    }

    private fun showSaveSheet() {
        val editor = editor ?: return
        val available = buildSet {
            add(SaveAction.Save)
            if (editor.isAvailable(EditAction.SAVE_COPY)) add(SaveAction.SaveCopy)
            if (editor.isAvailable(EditAction.EXPORT_PDF)) add(SaveAction.ExportPdf)
            add(SaveAction.Share)
        }
        SaveDocumentBottomSheet(this, file.name, available) { action ->
            when (action) {
                SaveAction.Save -> if (editor.hasUnsavedChanges()) editor.save() else toast(R.string.saved)
                SaveAction.SaveCopy -> editor.runSafely(EditAction.SAVE_COPY)
                SaveAction.ExportPdf -> editor.runSafely(EditAction.EXPORT_PDF)
                SaveAction.Share -> share()
            }
        }.show()
    }

    // ---- Excel formula box ----

    private val excelPanel: ExcelEditPanel? get() = editor?.panel as? ExcelEditPanel

    private fun applyFormula() {
        val editor = editor ?: return
        editor.runSafely(EditAction.CELL_VALUE, binding.edtFormula.text.toString())
        binding.edtFormula.clearFocus()
        hideKeyboard()
    }

    // ---- find and replace ----

    /** Find works on the editor: from viewing, editing starts for as long as the bar is open. */
    private fun startFind() {
        findFromView = editor == null
        if (!startEditing()) return
        editor?.runSafely(EditAction.FIND_REPLACE)
    }

    private fun findNext() {
        val text = binding.edtFind.text.toString()
        if (text.isEmpty()) return
        hideKeyboard()
        showFindResult(findRequest?.findNext(text))
    }

    private fun showFindResult(result: com.editor.docsdk.FindResult?) {
        binding.tvFindCount.text = result?.let { getString(R.string.search_count, it.number, it.count) }.orEmpty()
        if (result == null) toast(R.string.find_no_match)
    }

    private fun closeFind() {
        hideKeyboard()
        findRequest?.cancel()
        findRequest = null
        binding.edtFind.setText("")
        binding.edtReplace.setText("")
        binding.tvFindCount.text = ""
        if (findFromView && editor?.hasUnsavedChanges() != true) stopEditing() else setMode(Mode.Edit)
    }

    // ---- slides ----

    private fun loadSlide(slide: Int, onReady: (Bitmap) -> Unit) {
        val thumbnails = office?.thumbnails ?: return
        launchMain {
            // the SDK counts pages from 1 and keeps its own cache
            runCatching { thumbnails.get(slide + 1) }.getOrNull()?.let(onReady)
        }
    }

    /**
     * Follows the SDK's own view: slides it drew again (an edit, a new picture) get a new
     * thumbnail, and every document it shows gets a colour behind its pages.
     */
    private fun watchReader() {
        readerJob?.cancel()
        val office = office ?: return
        readerJob = lifecycleScope.launch {
            launch {
                office.thumbnailInvalidated.collect { page ->
                    stripAdapter.notifyItemChanged(page - 1)
                    sorterAdapter.notifyItemChanged(page - 1)
                }
            }
            office.state.collect { state ->
                if (state.status == ReaderState.Status.Ready) {
                    office.paintBehindPages(ContextCompat.getColor(this@OfficeActivity, R.color.color_surface_track))
                }
            }
        }
    }

    /**
     * The strip and the sorter follow in [DocumentView.Listener.onPageChanged], once the SDK has
     * reopened the deck. It refuses commands (false) while it does, so taps then only get a toast.
     */
    private fun runSlideAction(action: EditAction) {
        val editor = editor ?: return
        if (slidesChanging) return toast(R.string.slides_updating)
        // With one slide left the SDK says itself that it cannot delete it.
        if (action == EditAction.DELETE_SLIDE && documentView.pageCount > 1) {
            MessageDialog(
                this, R.string.delete_slide_title, R.string.delete_slide_message,
                R.string.file_action_delete, R.string.file_action_cancel
            ) { if (editor.runSafely(action)) slidesChanging = true }.show()
            return
        }
        if (editor.runSafely(action)) slidesChanging = true
    }

    private fun refreshSlides(redraw: Boolean) {
        if (type != DocumentType.POWERPOINT) return
        val count = documentView.pageCount
        if (count != slideCount || redraw) {
            slideCount = count
            val slides = (0 until count).toList()
            stripAdapter.submitList(slides + SlideAdapter.ADD)
            sorterAdapter.submitList(slides)
        }
        val current = documentView.currentPage
        stripAdapter.selected = current
        sorterAdapter.selected = current
        if (mode == Mode.Edit) binding.rcvSlides.scrollToPosition(current.coerceAtLeast(0))
    }

    // ---- slideshow ----

    /**
     * The SDK plays the show in [SlideShowActivity], from the slide on screen and with the edits
     * not saved yet. It reads the slides from [documentView], so this screen stays open under it.
     */
    private fun startShow() {
        if (startingShow) return
        startingShow = true
        hideKeyboard()
        documentView.prepareSlideShow { source ->
            startingShow = false
            if (isFinishing || isDestroyed) return@prepareSlideShow
            if (source == null) return@prepareSlideShow toast(R.string.slideshow_unavailable)
            SlideShowActivity.source = source
            launcherForResult<SlideShowActivity> { result ->
                // the slide the show ended on
                val slide = result.data?.getIntExtra(SlideShowActivity.EXTRA_SLIDE, -1) ?: -1
                if (slide >= 0) documentView.goToPage(slide)
            }
        }
    }

    // ---- what is on screen ----

    private fun setMode(next: Mode) {
        mode = next
        if (next == Mode.Sorter) refreshSlides(redraw = true)
        if (next == Mode.Find) {
            binding.edtFind.requestFocus()
            showKeyboard(binding.edtFind)
        }
        render()
    }

    private fun render() = with(binding) {
        val editing = mode == Mode.Edit
        val slides = type == DocumentType.POWERPOINT
        barView.visibleOrGone(mode == Mode.View)
        barEdit.visibleOrGone(editing)
        barFind.visibleOrGone(mode == Mode.Find)
        barSorter.visibleOrGone(mode == Mode.Sorter)
        dockTop(editing)
        tvSorterTitle.setText(R.string.arrange_slides)
        tvSorterSubtitle.setText(R.string.arrange_slides_hint)

        btnMore.visibleOrGone(loaded)
        btnSearch.visibleOrGone(loaded && documentView.canEdit && type == DocumentType.WORD)
        btnEdit.visibleOrGone(mode == Mode.View && loaded && documentView.canEdit)
        btnPlay.visibleOrGone(slides)

        layoutFormula.visibleOrGone(editing && excelPanel != null)
        layoutRibbon.visibleOrGone(editing && !typingInSlide())
        rcvSorter.visibleOrGone(mode == Mode.Sorter)
        layoutSorterActions.visibleOrGone(mode == Mode.Sorter)

        renderTabs()
        renderState()
        renderPage()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && keepKeyboardDown) {
            val content = Rect().also { binding.documentView.getGlobalVisibleRect(it) }
            if (content.contains(ev.rawX.toInt(), ev.rawY.toInt())) keepKeyboardDown = false
        }
        return super.dispatchTouchEvent(ev)
    }

    /** The SDK's own text box over the page (slide text, cell) has the keyboard. */
    private fun typingInDocument(): Boolean {
        val view = currentFocus as? EditText ?: return false
        var parent = view.parent
        while (parent is View) {
            if (parent === binding.documentView) return true
            parent = parent.parent
        }
        return false
    }

    /**
     * Text typed in a slide: the SDK hides the ribbon and puts its format bar over the keyboard, and
     * lifts [ActivityOfficeBinding.layoutBottom] there too, so the slide strip would cover that bar.
     */
    private fun typingInSlide(): Boolean = type == DocumentType.POWERPOINT && typingInDocument()

    /** What follows the selection: undo and redo, the save state, the ribbon's toggles, the formula box. */
    private fun renderState() = with(binding) {
        layoutSlides.visibleOrGone(mode == Mode.Edit && type == DocumentType.POWERPOINT && !typingInSlide())
        val editor = editor ?: return@with
        btnUndo.setEnabledLook(editor.isEnabled(EditAction.UNDO))
        btnRedo.setEnabledLook(editor.isEnabled(EditAction.REDO))

        // Asking closes the box being typed in (slide text, cell): the keyboard would drop at once.
        // Word only reads its state, and its hidden typing box has the focus all the time.
        if (type == DocumentType.WORD || !typingInDocument()) unsaved = editor.hasUnsavedChanges()
        tvStatus.setText(if (unsaved) R.string.fo_edited else R.string.saved)
        tvStatus.setTextColor(
            ContextCompat.getColor(this@OfficeActivity, if (unsaved) R.color.text_secondary else R.color.color_success)
        )

        ribbonAdapter.isActive = editor::isActive
        ribbonAdapter.isEnabled = editor::isEnabled
        ribbonAdapter.chipText = { chipTexts[it] }
        ribbonAdapter.notifyItemRangeChanged(0, ribbonAdapter.itemCount)
        formatSheet?.refresh()

        excelPanel?.let { panel ->
            tvCell.text = panel.selectedRange
            // Not while the user types in the box.
            if (!edtFormula.hasFocus()) edtFormula.setText(panel.cellInput)
        }
        refreshSlides(redraw = false)
    }

    private fun renderPage() = with(binding) {
        val count = documentView.pageCount
        val page = (documentView.currentPage + 1).coerceIn(1, count.coerceAtLeast(1))
        tvPage.visibleOrGone(loaded && count > 0)
        tvPagePill.visibleOrGone(loaded && count > 1 && mode == Mode.View)
        tvPagePill.text = getString(R.string.page_count_short, page, count)
        tvPage.text = getString(
            when (type) {
                DocumentType.EXCEL -> R.string.sheet_of
                DocumentType.POWERPOINT -> R.string.slide_of
                else -> R.string.page_of
            }, page, count
        )
    }

    // ---- keyboard ----

    /**
     * The SDK lifts [ActivityOfficeBinding.layoutBottom] until its bottom edge meets the keyboard,
     * so the docked ribbon sits right on it. While typing the ribbon keeps one row: the tab row
     * goes and the picked tab becomes a chip at the start of the commands.
     */
    private fun fitKeyboard(keyboard: KeyboardState) = with(binding) {
        val typing = keyboard.visible && keyboard.heightPx > 0
        if (btnTabPick.isVisible == typing) return@with
        scrollTabs.visibleOrGone(!typing)
        btnTabPick.visibleOrGone(typing)
    }

    /** Floating over the page while viewing, flat against the top edge while editing. */
    private fun dockTop(docked: Boolean) = with(binding) {
        fun px(id: Int) = resources.getDimensionPixelSize(id)
        cardTop.setBackgroundResource(if (docked) R.drawable.bg_docked_top else R.drawable.bg_float_card)
        cardTop.elevation = if (docked) 0f else px(com.intuit.sdp.R.dimen._8sdp).toFloat()
        val inner = px(if (docked) com.intuit.sdp.R.dimen._3sdp else com.intuit.sdp.R.dimen._6sdp)
        cardTop.updatePadding(left = inner, right = inner)
        val side = if (docked) 0 else px(com.intuit.sdp.R.dimen._9sdp)
        layoutTop.updatePadding(left = side, right = side, bottom = if (docked) 0 else px(com.intuit.sdp.R.dimen._8sdp))
        // The status bar's room above the docked bar takes its colour.
        if (docked) layoutTop.setBackgroundColor(getColor(R.color.color_surface_card)) else layoutTop.background = null
    }

    // ---- ribbon tabs ----

    private fun buildTabs() = with(binding) {
        layoutTabs.removeAllViews()
        val side = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
        val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)
        tabs.forEachIndexed { index, tab ->
            val label = TextView(this@OfficeActivity).apply {
                setText(tab.title)
                gravity = Gravity.CENTER
                setPadding(side, 0, side, 0)
                setBackgroundResource(R.drawable.bg_badge)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(com.intuit.sdp.R.dimen._10sdp))
                typeface = ResourcesCompat.getFont(this@OfficeActivity, R.font.nunito_700)
                setOnClickListener {
                    tabIndex = index
                    renderTabs()
                }
            }
            layoutTabs.addView(
                label,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply { if (index > 0) marginStart = gap }
            )
        }
    }

    /** The tabs as a menu, from the chip that stands for the tab row while typing. */
    private fun pickTab() {
        PopupMenu(this, binding.btnTabPick).apply {
            tabs.forEachIndexed { index, tab -> menu.add(0, index, index, tab.title) }
            setOnMenuItemClickListener {
                tabIndex = it.itemId
                renderTabs()
                true
            }
        }.show()
    }

    /** The picked tab is a pill in the file type's pale colour. */
    private fun renderTabs() = with(binding) {
        if (tabs.isEmpty()) return@with
        val fileType = AppUtils.documentTypeOf(file)
        val accent = getColor(fileType?.colorRes ?: R.color.primary)
        val soft = getColor(fileType?.softColorRes ?: R.color.color_bg_selected)
        val muted = getColor(R.color.text_secondary)
        layoutTabs.children.forEachIndexed { index, view ->
            val selected = index == tabIndex
            val label = view as TextView
            label.setTextColor(if (selected) accent else muted)
            label.backgroundTintList = ColorStateList.valueOf(if (selected) soft else Color.TRANSPARENT)
        }
        btnTabPick.setText(tabs[tabIndex.coerceIn(0, tabs.lastIndex)].title)
        btnTabPick.setTextColor(accent)
        btnTabPick.compoundDrawableTintList = ColorStateList.valueOf(accent)
        btnTabPick.backgroundTintList = ColorStateList.valueOf(soft)
        ribbonAdapter.submitList(tabs[tabIndex.coerceIn(0, tabs.lastIndex)].items)
    }

    override fun onStop() {
        super.onStop()
        // The app may be killed in the background: keep the unsaved edits as a draft.
        editor?.takeIf { it.hasUnsavedChanges() }?.panel?.saveDraft()
    }

    override fun onDestroy() {
        if (runner.officeWriter === pdfWriter) runner.officeWriter = null
        readerJob?.cancel()
        documentView.listener = null
        documentView.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATH = "extra_path"

        /** Commands whose next step is typing: the keyboard they open is wanted. */
        private val TYPING_ACTIONS = setOf(
            EditAction.INSERT_TEXT, EditAction.NEW_LINE, EditAction.SET_TEXT, EditAction.ADD_TEXT_BOX
        )
        private const val EXTRA_EDIT = "extra_edit"
        private const val STATE_PASSWORD = "state_password"
        private val LEGACY_EXTENSIONS = setOf("doc", "xls", "ppt")

        /** Whether this screen can show the file with [name]. */
        fun canOpen(name: String): Boolean {
            val type = DocumentType.fromFileName(name)
            return type != null && type != DocumentType.PDF
        }

        /** With [edit], editing starts as soon as the document shows. */
        fun start(context: Context, path: String, edit: Boolean = false) {
            context.startActivity(
                Intent(context, OfficeActivity::class.java)
                    .putExtra(EXTRA_PATH, path)
                    .putExtra(EXTRA_EDIT, edit)
            )
        }
    }
}
