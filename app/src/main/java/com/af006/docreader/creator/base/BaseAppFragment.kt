package com.af010.creator.docreader.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.viewbinding.ViewBinding
import com.af010.creator.docreader.ui.dialog.DialogNoInternet
import com.ui.baselib.base.BaseFragment
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.network.isNetworkAvailable
import com.ui.baselib.network.observeNetworkConnectivity

abstract class BaseAppFragment<VB : ViewBinding>(
      bindingFactory: (LayoutInflater) -> VB
) : BaseFragment<VB>(bindingFactory) {
    private var dialogNoInternet: DialogNoInternet? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialogNoInternet = (appActivity as? BaseAppActivity<*>)?.dialogNoInternet
        observeNetworkConnectivity(onNetworkAvailable = {
            dialogNoInternet?.dismiss()
        }, onNetworkLost = { dialogNoInternet?.show() }) {
            dialogNoInternet?.dismiss()
        }
    }

    fun View.internetClick(action: (View) -> Unit = {}) {
        click {
            if (appContext().isNetworkAvailable()) {
                action.invoke(this@internetClick)
            } else {
                dialogNoInternet?.show()
            }
        }
    }

    fun View.internetSingleClick(action: (View) -> Unit = {}) {
        clickSingle {
            if (appContext().isNetworkAvailable()) {
                action.invoke(this@internetSingleClick)
            } else {
                dialogNoInternet?.show()
            }
        }
    }

    fun runWithInternet(action: () -> Unit = {}) {
        if (appContext().isNetworkAvailable()) {
            action()
        } else {
            dialogNoInternet?.show()
        }
    }
}