package com.af010.creator.docreader.ui.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.text.format.Formatter
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.DialogOpenPasswordBinding
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.softColorRes
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
        tvMessage.text = context.getString(R.string.fo_locked_message, file.name)
        if (wrong) layoutPassword.setBackgroundResource(R.drawable.bg_field_error)
        tvName.text = file.name
        tvMeta.text = context.getString(
            R.string.document_meta,
            context.getString(R.string.protected_meta),
            Formatter.formatShortFileSize(context, file.length())
        )
        tvError.visibleOrGone(wrong)
        val type = AppUtils.documentTypeOf(file)
        tintAccent(type?.colorRes, btnConfirm)
        type?.let {
            ivType.backgroundTintList = ColorStateList.valueOf(context.getColor(it.softColorRes))
            ivType.imageTintList = ColorStateList.valueOf(context.getColor(it.colorRes))
        }

        btnShow.click {
            shown = !shown
            btnShow.setImageResource(if (shown) R.drawable.ic_fo_eye_off else R.drawable.ic_fo_eye)
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
