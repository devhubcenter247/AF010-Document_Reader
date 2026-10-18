package com.af010.creator.docreader.pdf

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.model.ToolResult
import com.af010.creator.docreader.utils.AppUtils
import com.editor.docsdk.DocumentException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface ToolState {
    data object Idle : ToolState

    /** [total] is 0 while there is no countable progress yet. */
    data class Running(
        val request: ToolRequest,
        val done: Int = 0,
        val total: Int = 0,
        @StringRes val step: Int = 0
    ) : ToolState

    data class Done(val result: ToolResult) : ToolState
    data class Failed(val request: ToolRequest, @StringRes val message: Int) : ToolState
}

/**
 * Runs one [ToolRequest] at a time on the application scope, so the work goes on when the
 * Processing screen is left. The screen follows [state] and calls [reset] once it has shown
 * the outcome.
 */
class ToolRunner(
    private val context: Context,
    private val toolbox: PdfToolbox,
    private val translator: PdfTranslator,
    private val repository: DocumentRepository,
    private val scope: CoroutineScope
) {
    val state: StateFlow<ToolState>
        field = MutableStateFlow<ToolState>(ToolState.Idle)

    private var job: Job? = null

    fun start(request: ToolRequest) {
        if (state.value is ToolState.Running) return
        state.value = ToolState.Running(request)
        job = scope.launch {
            val outputs = ArrayList<File>()
            try {
                val result = run(request, outputs)
                AppUtils.notifyMediaScanner(context, *result.outputs.toTypedArray())
                repository.refresh()
                state.value = ToolState.Done(result)
            } catch (e: CancellationException) {
                outputs.forEach { it.delete() }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "${request.tool} failed", e)
                outputs.forEach { it.delete() }
                state.value = ToolState.Failed(request, messageOf(request, e))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        state.value = ToolState.Idle
    }

    fun reset() {
        if (state.value !is ToolState.Running) state.value = ToolState.Idle
    }

    private fun progress(request: ToolRequest, @StringRes step: Int = 0): ToolProgress = { done, total ->
        state.value = ToolState.Running(request, done, total, step)
    }

    /** Every file created is added to [outputs] first, so a failure or a cancel can remove it. */
    private suspend fun run(request: ToolRequest, outputs: MutableList<File>): ToolResult {
        val directory = File(AppUtils.ensureDocumentDirectory())
        fun output(name: String, extension: String = "pdf"): File =
            uniqueFile(directory, name, extension).also(outputs::add)

        var sourceSize = 0L
        when (request) {
            is ToolRequest.Merge ->
                toolbox.merge(request.paths.map(::File), output(request.name), progress(request))

            is ToolRequest.Split -> {
                val source = File(request.path)
                request.parts.forEachIndexed { index, pages ->
                    val label = if (pages.size == 1) "${pages.first() + 1}" else "${pages.first() + 1}-${pages.last() + 1}"
                    toolbox.split(source, pages.toIntArray(), output("${source.nameWithoutExtension}-$label"))
                    progress(request)(index + 1, request.parts.size)
                }
            }

            is ToolRequest.PdfToImages -> {
                val source = File(request.path)
                val pages = request.pages.ifEmpty { (0 until toolbox.pageCount(source)).toList() }
                pages.forEachIndexed { index, page ->
                    toolbox.pageToImage(source, page, output("${source.nameWithoutExtension}-${page + 1}", "jpg"))
                    progress(request)(index + 1, pages.size)
                }
            }

            is ToolRequest.Compress -> {
                val source = File(request.path)
                sourceSize = source.length()
                toolbox.compress(
                    source, output("${source.nameWithoutExtension}-compressed"), request.level, progress(request)
                )
            }

            is ToolRequest.Protect -> {
                val source = File(request.path)
                toolbox.protect(source, output("${source.nameWithoutExtension}-protected"), request.password)
            }

            is ToolRequest.Watermark -> {
                val source = File(request.path)
                toolbox.watermark(
                    source, output("${source.nameWithoutExtension}-watermark"),
                    request.text, request.color, request.angle, progress(request)
                )
            }

            is ToolRequest.Redact -> {
                val source = File(request.path)
                toolbox.redact(
                    source, output("${source.nameWithoutExtension}-redacted"), request.areas, progress(request)
                )
            }

            is ToolRequest.Ocr -> {
                val source = File(request.path)
                toolbox.recognizeText(source, output("${source.nameWithoutExtension}-ocr"), progress(request))
            }

            is ToolRequest.Zip ->
                toolbox.zip(request.paths.map(::File), output(request.name, "zip"), progress(request))

            is ToolRequest.ImagesToPdf ->
                toolbox.imagesToPdf(request.paths.map(::File), output(request.name), request.setup, progress(request))

            is ToolRequest.Translate -> return translate(request)
        }
        return ToolResult(request.tool, outputs.map { it.path }, sourceSize)
    }

    /** The result is kept in the cache for the result screen, which saves it as a PDF on demand. */
    private suspend fun translate(request: ToolRequest.Translate): ToolResult {
        val source = File(request.path)
        val total = toolbox.pageCount(source)
        val document = translator.translate(
            source, request.pages, total, request.sourceLanguage, request.targetLanguage
        ) { step ->
            state.value = when (step) {
                is TranslateStep.Reading ->
                    ToolState.Running(request, step.done, step.total, R.string.translate_step_read)
                TranslateStep.Downloading ->
                    ToolState.Running(request, step = R.string.translate_step_download)
                is TranslateStep.Translating ->
                    ToolState.Running(request, step.done, step.total, R.string.translate_step_translate)
            }
        }
        val file = File(context.cacheDir, TRANSLATION_FILE)
        document.save(file)
        return ToolResult(request.tool, listOf(file.path))
    }

    private fun messageOf(request: ToolRequest, error: Exception): Int = when {
        error is TranslateException -> when (error.error) {
            TranslateError.NoText -> R.string.translate_no_text
            TranslateError.UnknownLanguage -> R.string.translate_unknown_language
            TranslateError.ModelDownload -> R.string.translate_download_failed
            TranslateError.Failed -> R.string.translate_failed
        }
        error is DocumentException -> when (error.reason) {
            DocumentException.Reason.PASSWORD_REQUIRED,
            DocumentException.Reason.PASSWORD_INCORRECT -> R.string.error_file_password
            DocumentException.Reason.DAMAGED -> R.string.error_file_damaged
            DocumentException.Reason.NOT_FOUND -> R.string.error_file_not_found
            DocumentException.Reason.OUT_OF_MEMORY -> R.string.error_file_too_large_memory
            DocumentException.Reason.STORAGE -> R.string.error_file_storage
            else -> R.string.error_file_open_generic
        }
        request is ToolRequest.Merge -> R.string.merge_failed
        request is ToolRequest.Split -> R.string.split_failed
        request is ToolRequest.Zip -> R.string.zip_failed
        request is ToolRequest.ImagesToPdf -> R.string.convert_failed
        else -> R.string.error_file_open_generic
    }

    companion object {
        private const val TAG = "ToolRunner"
        private const val TRANSLATION_FILE = "translate/result.json"

        /** "name.ext", or "name (1).ext", "name (2).ext", ... when that is taken. */
        fun uniqueFile(directory: File, name: String, extension: String): File =
            generateSequence(0) { it + 1 }
                .map { File(directory, if (it == 0) "$name.$extension" else "$name ($it).$extension") }
                .first { !it.exists() }
    }
}
