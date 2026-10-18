package com.af010.creator.docreader.ui.main.reader.pdf

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.af010.creator.docreader.pdf.PdfToolbox
import com.reader.pdfviewer.pdfium.PdfPageText
import com.reader.pdfviewer.pdfium.PdfTextBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Reads the document aloud with the system text to speech, a sentence at a time from [page]
 * on, telling [listener] which sentence it is on (to highlight it and follow its page). Pages
 * without a text layer are skipped. Call [release] when done.
 */
internal class ReadAloud(
    context: Context,
    private val scope: CoroutineScope,
    private val toolbox: PdfToolbox,
    private val file: File,
    private val password: String?,
    private val pageCount: Int,
    private val listener: Listener,
) {
    interface Listener {
        /** The sentence being read: its [page] and boxes relative to the page (0..1). */
        fun onSentence(page: Int, rects: List<RectF>)
        fun onStateChanged(playing: Boolean)
        /** Reading stopped at the end of the document, or could not start ([error]). */
        fun onFinished(error: Boolean)
    }

    /** A sentence with its boxes, one per line it spans. */
    class Sentence(val text: String, val rects: List<RectF>)

    private var ready = false
    private var pendingStart = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) listener.onFinished(error = true)
        else if (pendingStart) { pendingStart = false; speakCurrent() }
    }

    private var page = 0
    private var sentences: List<Sentence> = emptyList()
    private var index = 0
    private var loadJob: Job? = null
    var playing = false
        private set
    /** Speech rate, 1 is normal. */
    var rate = 1f
        set(value) { field = value; tts.setSpeechRate(value); if (playing) speakCurrent() }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                scope.launch { if (playing && utteranceId == "s$page-$index") next() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch { if (playing) next() }
            }
        })
    }

    /** Starts reading at the first sentence of [fromPage]. */
    fun start(fromPage: Int) {
        playing = true
        listener.onStateChanged(true)
        load(fromPage.coerceIn(0, pageCount - 1), 0)
    }

    fun pause() {
        playing = false
        tts.stop()
        listener.onStateChanged(false)
    }

    fun resume() {
        if (playing) return
        playing = true
        listener.onStateChanged(true)
        speakCurrent()
    }

    fun next() {
        if (index + 1 < sentences.size) { index++; speakCurrent() }
        else if (page + 1 < pageCount) load(page + 1, 0)
        else finish()
    }

    fun previous() {
        if (index > 0) { index--; speakCurrent() }
        else if (page > 0) load(page - 1, -1)
    }

    private fun finish() {
        playing = false
        tts.stop()
        listener.onSentence(page, emptyList())
        listener.onStateChanged(false)
        listener.onFinished(error = false)
    }

    /** Reads the sentences of [target], then speaks sentence [at] (-1: the last). Pages without text are skipped. */
    private fun load(target: Int, at: Int) {
        loadJob?.cancel()
        tts.stop()
        loadJob = scope.launch {
            var p = target
            while (p in 0 until pageCount) {
                val text = try { toolbox.pageLayout(file, p, password) } catch (e: Exception) { null }
                val list = text?.let { sentencesOf(it) }.orEmpty()
                if (list.isNotEmpty()) {
                    page = p
                    sentences = list
                    index = if (at < 0) list.lastIndex else at.coerceAtMost(list.lastIndex)
                    if (playing) speakCurrent()
                    return@launch
                }
                p += if (at < 0) -1 else 1
            }
            finish()
        }
    }

    private fun speakCurrent() {
        val s = sentences.getOrNull(index) ?: return
        listener.onSentence(page, s.rects)
        if (!playing) return
        if (!ready) { pendingStart = true; return }
        tts.language = languageOf(s.text)
        tts.setSpeechRate(rate)
        tts.speak(s.text, TextToSpeech.QUEUE_FLUSH, Bundle(), "s$page-$index")
    }

    fun release() {
        loadJob?.cancel()
        playing = false
        tts.stop()
        tts.shutdown()
    }

    private fun languageOf(text: String): Locale =
        if (text.any { it in VIETNAMESE }) Locale("vi", "VN") else Locale.getDefault()

    companion object {
        private const val VIETNAMESE = "ăâđêôơưĂÂĐÊÔƠƯàảãáạằẳẵắặầẩẫấậèẻẽéẹềểễếệìỉĩíịòỏõóọồổỗốộờởỡớợùủũúụừửữứựỳỷỹýỵ"
        private const val MAX_WORDS = 45

        /** The page's words gathered into sentences, each with a box per line it covers (0..1). */
        fun sentencesOf(text: PdfPageText): List<Sentence> {
            val words = text.words
            if (words.isEmpty()) return emptyList()
            val result = ArrayList<Sentence>()
            val current = ArrayList<PdfTextBlock>()
            fun flush() {
                if (current.isEmpty()) return
                val lines = ArrayList<RectF>()
                for (w in current) {
                    if (w.bounds.isEmpty) continue
                    val r = RectF(w.bounds.left / text.pageWidth, w.bounds.top / text.pageHeight,
                        w.bounds.right / text.pageWidth, w.bounds.bottom / text.pageHeight)
                    val line = lines.lastOrNull()
                    if (line != null && abs(line.centerY() - r.centerY()) < line.height() * 0.6f && r.left >= line.left - 0.01f) line.union(r)
                    else lines += r
                }
                result += Sentence(current.joinToString(" ") { it.text }, lines)
                current.clear()
            }
            for (w in words) {
                current += w
                val end = w.text.lastOrNull()
                if (end != null && (end in ".!?…:;" || current.size >= MAX_WORDS)) flush()
            }
            flush()
            return result
        }
    }
}
