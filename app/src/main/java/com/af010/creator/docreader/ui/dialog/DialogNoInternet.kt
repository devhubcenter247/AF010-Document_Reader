package com.af010.creator.docreader.ui.dialog

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.ContextMenu
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.DialogNoInternetBinding
import com.ui.baselib.base.BaseDialog
import com.ui.baselib.extensions.click

class DialogNoInternet(
    private val context: Context,
    private val onTryAgain: () -> Unit = {}
) : BaseDialog<DialogNoInternetBinding>(context = context, DialogNoInternetBinding::inflate) {
    override fun DialogNoInternetBinding.initView() {
        btnTryAgain.click {
            dismiss()
            onTryAgain()
        }
        btnWifi.click {
            runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        }
    }


}