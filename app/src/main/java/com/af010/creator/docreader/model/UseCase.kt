package com.af010.creator.docreader.model

import androidx.annotation.StringRes
import com.af010.creator.docreader.R

/** What the user picked on the Welcome screen; decides the suggested tools on the Files tab. */
enum class UseCase(val id: String, @StringRes val title: Int, val tools: List<Tool>) {
    Work("work", R.string.use_case_work, listOf(Tool.Sign, Tool.Compress, Tool.Merge)),
    Study("study", R.string.use_case_study, listOf(Tool.Translate, Tool.Annotate, Tool.Ocr)),
    Personal("personal", R.string.use_case_personal, listOf(Tool.ImagesToPdf, Tool.Protect, Tool.Compress)),
    Reading("reading", R.string.use_case_reading, listOf(Tool.Merge, Tool.Split, Tool.Translate));

    companion object {
        fun of(ids: List<String>): List<UseCase> =
            entries.filter { it.id in ids }.ifEmpty { listOf(Work) }

        /** Up to [limit] tools, taken in turn from each chosen use case. */
        fun suggestedTools(ids: List<String>, limit: Int = 3): List<Tool> {
            val useCases = of(ids)
            return (0 until limit)
                .flatMap { index -> useCases.mapNotNull { it.tools.getOrNull(index) } }
                .distinct()
                .take(limit)
        }
    }
}
