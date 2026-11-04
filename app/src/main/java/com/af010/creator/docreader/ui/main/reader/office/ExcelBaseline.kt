package com.af010.creator.docreader.ui.main.reader.office

import com.editor.docsdk.DocumentEditor

/**
 * Where a workbook's edit session stood when editing began. SDK 1.0.0 records a step nobody made
 * when it starts editing some workbooks: undo turns on and the session reports changes, even once
 * that step is undone. Changes count only once the undo or redo stack moves past this point, or
 * once the session is a new one (the SDK starts one after each save).
 *
 * The session is reached through [ExcelSessions] (SDK members by their R8 names).
 */
class ExcelBaseline private constructor(val session: Any, private val undoDepth: Int) {

    /** Nothing has changed since editing began. */
    fun isUnchanged(editor: DocumentEditor): Boolean {
        val now = sessionOf(editor) ?: return false
        return now === session && ExcelSessions.undoDepth(now) == undoDepth && ExcelSessions.redoDepth(now) == 0
    }

    /** Undo would only take back the step the SDK made up. */
    fun undoIsMadeUp(editor: DocumentEditor): Boolean {
        val now = sessionOf(editor) ?: return false
        return now === session && ExcelSessions.undoDepth(now) <= undoDepth
    }

    companion object {
        /** Null when [editor] does not edit a workbook. */
        fun of(editor: DocumentEditor): ExcelBaseline? =
            sessionOf(editor)?.let { ExcelBaseline(it, ExcelSessions.undoDepth(it)) }

        private fun sessionOf(editor: DocumentEditor): Any? =
            runCatching { ExcelSessions.session(editor.panel) }.getOrNull()
    }
}
