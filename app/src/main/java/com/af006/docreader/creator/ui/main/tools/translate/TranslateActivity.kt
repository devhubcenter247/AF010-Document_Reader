package com.af010.creator.docreader.ui.main.tools.translate

import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityTranslateBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.pdf.PdfTranslator
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.ui.main.tools.sizeText
import com.google.mlkit.nl.translate.TranslateLanguage
import com.af010.creator.docreader.app.remote.remoteConfig
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visible
import org.koin.android.ext.android.inject
import java.io.File
import java.util.Locale

/** Choose the languages for translating a PDF; the free version translates the first pages only. */
class TranslateActivity : BaseAppActivity<ActivityTranslateBinding>(ActivityTranslateBinding::inflate) {

    private val toolbox: PdfToolbox by inject()
    private val translator: PdfTranslator by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }

    private var pageCount = 0

    /** null = let the translator detect it. */
    private var fromLanguage: String? = null
    private var toLanguage: String = defaultTarget()

    /** Pages the free version translates; no limit while the paywall is switched off. */
    private val freePages: Int get() = if (remoteConfig.paywallEnabled) FREE_PAGES else Int.MAX_VALUE
    private val pagesToTranslate: Int get() = minOf(pageCount, freePages)

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityTranslateBinding.setData() {
        header.show(Tool.Translate, R.string.translate_pdf, R.string.translate_subtitle)
        file.show(source, source.sizeText(this@TranslateActivity))
        render()

        launchMain {
            pageCount = runCatching { toolbox.pageCount(source) }.getOrDefault(0)
            if (pageCount <= 0) {
                toast(R.string.error_file_open_generic)
                finish()
                return@launchMain
            }
            file.tvMeta.text = pageCountText(pageCount)
            render()

            val detected = translator.detectLanguage(source, pageCount) ?: return@launchMain
            file.tvMeta.text = getString(
                R.string.translate_detected, pageCountText(pageCount), translator.languageName(detected)
            )
            if (fromLanguage == null) {
                fromLanguage = detected
                // Translating a language into itself is pointless: fall back to English or the device language.
                if (toLanguage == detected) {
                    toLanguage = if (detected == TranslateLanguage.ENGLISH) defaultTarget(skip = detected)
                    else TranslateLanguage.ENGLISH
                }
                render()
            }
        }
    }

    override fun ActivityTranslateBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        btnFrom.clickSingle {
            LanguageBottomSheet(
                this@TranslateActivity, R.string.translate_from, translator.languages, fromLanguage
            ) {
                fromLanguage = it
                render()
            }.show()
        }
        btnTo.clickSingle {
            LanguageBottomSheet(
                this@TranslateActivity, R.string.translate_to, translator.languages, toLanguage
            ) {
                toLanguage = it
                render()
            }.show()
        }
        action.btnAction.clickSingle {
            if (pageCount <= 0) return@clickSingle
            ProcessingActivity.start(
                this@TranslateActivity,
                ToolRequest.Translate(
                    source.path, (0 until pagesToTranslate).toList(), fromLanguage, toLanguage
                )
            )
            finish()
        }
    }

    private fun ActivityTranslateBinding.render() {
        tvFrom.text = fromLanguage?.let(translator::languageName) ?: getString(R.string.translate_detect)
        tvTo.text = translator.languageName(toLanguage)

        val pages = pagesToTranslate.coerceAtLeast(1)
        if (pageCount > freePages) {
            action.tvNote.visible()
            action.tvNote.text = resources.getQuantityString(R.plurals.translate_free_note, pages, pages)
            action.btnAction.text = resources.getQuantityString(R.plurals.translate_first_pages, pages, pages)
        } else {
            action.btnAction.setText(R.string.start_translation)
        }
        action.btnAction.alpha = if (pageCount > 0) 1f else 0.5f
    }

    /** The device language when ML Kit can translate into it, else English. */
    private fun defaultTarget(skip: String? = null): String {
        val device = TranslateLanguage.fromLanguageTag(Locale.getDefault().language)
        return device?.takeIf { it != skip }
            ?: if (skip == TranslateLanguage.ENGLISH) TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH
    }

    companion object {
        /** TODO: lift this limit for Premium once billing exists. */
        const val FREE_PAGES = 3
    }
}
