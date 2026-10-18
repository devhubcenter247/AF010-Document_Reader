package com.af010.creator.docreader.ui.main.tools.translate

import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.af010.creator.docreader.R
import com.af010.creator.docreader.ui.main.common.FilterChip
import com.af010.creator.docreader.ui.main.common.FilterChipAdapter
import com.af010.creator.docreader.ui.main.premium.PaywallActivity
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
import org.koin.android.ext.android.inject
import java.io.File
import java.util.Locale

/** Choose the languages for translating a PDF; the free version translates the first pages only. */
class TranslateActivity : BaseAppActivity<ActivityTranslateBinding>(ActivityTranslateBinding::inflate) {

    private val toolbox: PdfToolbox by inject()
    private val translator: PdfTranslator by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }

    private var pageCount = 0
    private val recentAdapter = FilterChipAdapter<String>()

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
        topBar.show(Tool.Translate, R.string.translate_pdf, getString(R.string.translate_subtitle))
        file.show(source, source.sizeText(this@TranslateActivity))
        rcvRecent.adapter = recentAdapter
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
        btnSwap.clickSingle {
            val from = fromLanguage ?: return@clickSingle
            fromLanguage = toLanguage
            toLanguage = from
            render()
        }
        recentAdapter.onChipClick {
            toLanguage = it
            render()
        }
        btnPro.clickSingle { PaywallActivity.start(this@TranslateActivity, Tool.Translate) }
        action.btnAction.clickSingle {
            if (pageCount <= 0) return@clickSingle
            preferenceHelper.recentTranslateTargets =
                (listOf(toLanguage) + preferenceHelper.recentTranslateTargets).distinct().take(MAX_RECENT)
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
        btnSwap.isEnabled = fromLanguage != null
        btnSwap.alpha = if (fromLanguage != null) 1f else 0.4f

        // The languages picked lately, plus the current one so the row is never a surprise.
        val recent = (listOf(toLanguage) + preferenceHelper.recentTranslateTargets).distinct().take(MAX_RECENT)
        layoutRecent.isVisible = preferenceHelper.recentTranslateTargets.isNotEmpty()
        recentAdapter.submitList(recent.map { tag ->
            FilterChip(tag, 0, isSelected = tag == toLanguage, label = translator.languageName(tag))
        })

        val limited = pageCount > freePages
        layoutQuota.isVisible = limited
        if (limited) {
            tvQuota.text = resources.getQuantityString(R.plurals.fo_free_first_pages, freePages, freePages)
            tvQuotaCount.text = getString(R.string.fo_x_of_y, freePages, pageCount)
            viewQuotaFill.updateLayoutParams<LinearLayout.LayoutParams> {
                weight = (freePages * 100f / pageCount).coerceAtLeast(4f)
            }
            btnPro.text = resources.getQuantityString(R.plurals.fo_translate_all_pro, pageCount, pageCount)
        }

        action.tvNote.isVisible = true
        action.tvNote.text = getString(R.string.fo_pack_note, translator.languageName(toLanguage))
        val pages = pagesToTranslate.coerceAtLeast(1)
        action.show(resources.getQuantityString(R.plurals.fo_translate_pages, pages, pages), R.drawable.ic_fo_translate)
        action.btnAction.isEnabled = pageCount > 0
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
        private const val MAX_RECENT = 4
    }
}
