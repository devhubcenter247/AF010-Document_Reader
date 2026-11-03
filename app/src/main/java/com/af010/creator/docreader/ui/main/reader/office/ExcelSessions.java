package com.af010.creator.docreader.ui.main.reader.office;

import androidx.annotation.Nullable;

import com.editor.docsdk.internal.xc;
import com.wxiwei.office.editor.ui.ExcelEditPanel;

/**
 * Reaches a workbook's edit session, which Kotlin cannot see (backing fields of the SDK's Kotlin
 * properties). R8 names in docsdk 1.0.0: ExcelEditPanel.session = M,
 * com.wxiwei.office.editor.xlsx.SheetEditSession = xc, its undoStack = i, redoStack = j.
 * Check them when the SDK version changes (see {@link ExcelBaseline}).
 */
final class ExcelSessions {
    private ExcelSessions() {
    }

    @Nullable
    static Object session(@Nullable Object panel) {
        return panel instanceof ExcelEditPanel ? ((ExcelEditPanel) panel).M : null;
    }

    static int undoDepth(Object session) {
        return ((xc) session).i.size();
    }

    static int redoDepth(Object session) {
        return ((xc) session).j.size();
    }
}
