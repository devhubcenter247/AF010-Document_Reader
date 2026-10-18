package com.af010.creator.docreader.ui.dialog

import android.content.Context
import androidx.annotation.StringRes
import com.af010.creator.docreader.databinding.DialogRenameBinding
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone

/**
 * One-line text prompt; [onConfirm] gets the trimmed text when it is not empty. [value] is the
 * text to start from, and with [allowEmpty] clearing it is an answer too.
 */
class TextInputDialog(
    private val context: Context,
    @StringRes private val title: Int,
    @StringRes private val hint: Int,
    @StringRes private val confirm: Int,
    private val value: String = "",
    private val allowEmpty: Boolean = false,
    private val onConfirm: (String) -> Unit
) : BaseDialog<DialogRenameBinding>(context = context, DialogRenameBinding::inflate) {

    override fun DialogRenameBinding.initView() {
        tvTitle.setText(title)
        tvLabel.setText(hint)
        edtName.setHint(hint)
        tvExtension.gone()
        edtName.setText(value)
        edtName.setSelection(edtName.text.length)
        btnConfirm.setText(confirm)
        tintAccent(null, btnConfirm)

        btnCancel.click { dismiss() }
        btnConfirm.clickSingle {
            val text = edtName.text.toString().trim()
            if (text.isEmpty() && !allowEmpty) return@clickSingle
            hideKeyboard()
            dismiss()
            onConfirm(text)
        }
    }
}
