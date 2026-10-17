package com.af010.creator.docreader.ui.dialog

import android.content.Context
import androidx.annotation.StringRes
import com.af010.creator.docreader.databinding.DialogMessageBinding
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone

/** Title, message and one button, or two when [cancel] is set; [onConfirm] runs on the main one. */
class MessageDialog(
    context: Context,
    @StringRes private val title: Int,
    @StringRes private val message: Int,
    @StringRes private val confirm: Int,
    @StringRes private val cancel: Int = 0,
    private val onConfirm: () -> Unit = {}
) : BaseDialog<DialogMessageBinding>(context = context, DialogMessageBinding::inflate) {

    override fun DialogMessageBinding.initView() {
        tvTitle.setText(title)
        tvMessage.setText(message)
        btnConfirm.setText(confirm)
        btnCancel.visibleOrGone(cancel != 0)
        if (cancel != 0) btnCancel.setText(cancel)

        btnCancel.click { dismiss() }
        btnConfirm.clickSingle {
            dismiss()
            onConfirm()
        }
    }
}
