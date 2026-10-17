package com.af010.creator.docreader.ui.dialog

import android.content.Context
import android.text.InputType
import android.text.format.Formatter
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.DialogOpenPasswordBinding
import com.af010.creator.docreader.ui.main.tools.typeIcon
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import java.io.File

/** Asks the password of a protected [file]; [wrong] tells the last one did not open it. */
class OpenPasswordDialog(
    private val context: Context,
    private val file: File,
    private val wrong: Boolean,
    private val onOpen: (String) -> Unit
) : BaseDialog<DialogOpenPasswordBinding>(context = context, DialogOpenPasswordBinding::inflate) {

    private var shown = false

    override fun DialogOpenPasswordBinding.initView() {
        ivType.setImageResource(file.typeIcon)
        tvName.text = file.name
        tvMeta.text = context.getString(
            R.string.document_meta,
            context.getString(R.string.protected_meta),
            Formatter.formatShortFileSize(context, file.length())
        )
        tvError.visibleOrGone(wrong)

        btnShow.click {
            shown = !shown
            btnShow.setImageResource(if (shown) R.drawable.ic_app_eyeoff else R.drawable.ic_app_eye)
            edtPassword.inputType = InputType.TYPE_CLASS_TEXT or
                    if (shown) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            edtPassword.setSelection(edtPassword.text.length)
        }
        btnCancel.click { dismiss() }
        btnConfirm.clickSingle {
            val password = edtPassword.text.toString()
            if (password.isEmpty()) return@clickSingle
            hideKeyboard()
            onOpen(password)
            dismiss()
        }
    }
}
