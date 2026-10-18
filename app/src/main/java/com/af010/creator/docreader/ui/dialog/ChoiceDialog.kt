package com.af010.creator.docreader.ui.dialog

import android.content.Context
import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.DialogChoiceBinding
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone

/** When a draft and the saved file were last written, compared side by side in the dialog. */
data class DraftTimes(val draft: String, val saved: String)

/**
 * A question from the mascot with two or three stacked answers: the main one, another one
 * (red with [secondaryDanger]), and with [tertiary] a quiet third that only closes the dialog
 * unless [onTertiary] is given. [times] adds the draft / saved file cards.
 */
class ChoiceDialog(
    context: Context,
    @StringRes private val title: Int,
    private val message: String,
    @StringRes private val primary: Int,
    @StringRes private val secondary: Int,
    @StringRes private val tertiary: Int = 0,
    private val secondaryDanger: Boolean = false,
    private val times: DraftTimes? = null,
    private val onPrimary: () -> Unit,
    private val onSecondary: () -> Unit,
    private val onTertiary: () -> Unit = {}
) : BaseDialog<DialogChoiceBinding>(context = context, DialogChoiceBinding::inflate) {

    override fun DialogChoiceBinding.initView() {
        tvTitle.setText(title)
        tvMessage.text = message
        btnPrimary.setText(primary)
        tintAccent(null, btnPrimary)
        btnSecondary.setText(secondary)
        if (secondaryDanger) btnSecondary.setTextColor(context.getColor(R.color.color_file_pdf))
        layoutCompare.visibleOrGone(times != null)
        times?.let {
            // The draft is what Restore brings back: its card is the picked one.
            layoutCompare.getChildAt(0).isSelected = true
            tvDraftTime.text = it.draft
            tvSavedTime.text = it.saved
        }
        btnTertiary.visibleOrGone(tertiary != 0)
        if (tertiary != 0) btnTertiary.setText(tertiary)

        btnPrimary.clickSingle {
            dismiss()
            onPrimary()
        }
        btnSecondary.clickSingle {
            dismiss()
            onSecondary()
        }
        btnTertiary.clickSingle {
            dismiss()
            onTertiary()
        }
    }
}
