package com.af010.creator.docreader.ui.dialog

import android.content.Context
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.DialogDeleteBinding
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle

class DeleteDialog(
    private val context: Context,
    private val count: Int,
    private val onDelete: () -> Unit
) : BaseDialog<DialogDeleteBinding>(context = context, DialogDeleteBinding::inflate) {

    override fun DialogDeleteBinding.initView() {
        tvTitle.text = context.resources.getQuantityString(R.plurals.delete_files_title, count, count)
        tvMessage.text = context.resources.getQuantityString(R.plurals.delete_files_message, count)

        btnCancel.click { dismiss() }
        btnConfirm.clickSingle {
            dismiss()
            onDelete()
        }
    }
}
