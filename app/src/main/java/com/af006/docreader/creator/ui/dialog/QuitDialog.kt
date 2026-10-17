package com.af010.creator.docreader.ui.dialog

import android.content.Context
import com.af010.creator.docreader.databinding.DialogQuitBinding
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle

/**
 * Asked on back from the Files tab, with room for a native ad between the question and the
 * buttons. TODO: the ad is a placeholder (layoutNativeAd) until the ads SDK is added.
 */
class QuitDialog(
    context: Context,
    private val onExit: () -> Unit
) : BaseDialog<DialogQuitBinding>(context = context, DialogQuitBinding::inflate) {

    override fun DialogQuitBinding.initView() {
        btnStay.click { dismiss() }
        btnExit.clickSingle {
            dismiss()
            onExit()
        }
    }
}
