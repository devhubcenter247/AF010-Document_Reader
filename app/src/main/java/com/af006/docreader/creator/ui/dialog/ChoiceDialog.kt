package com.af010.creator.docreader.ui.dialog

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.af010.creator.docreader.databinding.DialogChoiceBinding
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone

/**
 * A question with an icon and two or three stacked answers: the main one, another one, and
 * with [tertiary] a quiet third that only closes the dialog unless [onTertiary] is given.
 */
class ChoiceDialog(
    context: Context,
    @DrawableRes private val icon: Int,
    @StringRes private val title: Int,
    private val message: String,
    @StringRes private val primary: Int,
    @StringRes private val secondary: Int,
    @StringRes private val tertiary: Int = 0,
    private val onPrimary: () -> Unit,
    private val onSecondary: () -> Unit,
    private val onTertiary: () -> Unit = {}
) : BaseDialog<DialogChoiceBinding>(context = context, DialogChoiceBinding::inflate) {

    override fun DialogChoiceBinding.initView() {
        ivIcon.setImageResource(icon)
        tvTitle.setText(title)
        tvMessage.text = message
        btnPrimary.setText(primary)
        btnSecondary.setText(secondary)
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
