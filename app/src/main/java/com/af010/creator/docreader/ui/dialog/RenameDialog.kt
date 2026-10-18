package com.af010.creator.docreader.ui.dialog

import android.content.Context
import android.widget.Toast
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.DialogRenameBinding
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.colorRes
import com.af010.creator.docreader.utils.extensions.name
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.visibleOrGone
import java.io.File

/** [onRename] gets the new name without the extension. */
class RenameDialog(
    private val context: Context,
    private val document: RecentDocument,
    private val onRename: (String) -> Unit
) : BaseDialog<DialogRenameBinding>(context = context, DialogRenameBinding::inflate) {

    override fun DialogRenameBinding.initView() {
        val name = document.name
        val extension = name.substringAfterLast('.', "")
        edtName.setText(name.substringBeforeLast('.'))
        edtName.setSelection(edtName.text.length)
        tvExtension.text = if (extension.isEmpty()) "" else ".$extension"
        tvHint.visibleOrGone(extension.isNotEmpty())
        tvHint.text = context.getString(R.string.fo_extension_stays, ".$extension")
        tintAccent(AppUtils.documentTypeOf(File(document.path))?.colorRes, btnConfirm)

        btnCancel.click { dismiss() }
        btnConfirm.clickSingle {
            val newName = edtName.text.toString().trim()
            when {
                newName.isEmpty() -> toast(R.string.file_rename_empty)
                newName.contains('/') -> toast(R.string.file_rename_invalid)
                else -> {
                    hideKeyboard()
                    dismiss()
                    onRename(newName)
                }
            }
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
