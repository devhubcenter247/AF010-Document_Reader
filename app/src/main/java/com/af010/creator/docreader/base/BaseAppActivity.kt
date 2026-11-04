package com.af010.creator.docreader.base

import android.content.Context
import android.content.res.Resources
import com.af010.creator.docreader.ui.language.deviceCode
import com.af010.creator.docreader.ui.language.listLanguage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.viewbinding.ViewBinding
import com.af010.creator.docreader.ui.dialog.DialogNoInternet
import com.af010.creator.docreader.utils.LocateManager
import com.af010.creator.docreader.utils.PreferenceHelper
import com.ui.baselib.base.BaseActivity
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.network.isNetworkAvailable
import com.ui.baselib.network.observeNetworkConnectivity
import com.ui.baselib.network.openWifiSettings
import org.koin.android.ext.android.inject

abstract class BaseAppActivity<VB : ViewBinding>(
      bindingFactory: (LayoutInflater) -> VB,
) : BaseActivity<VB>(bindingFactory) {
     open val preferenceHelper: PreferenceHelper by inject()

    override fun attachBaseContext(newBase: Context?) {
        if (newBase == null) return super.attachBaseContext(null)
        // Before a language is picked (first run), the device's own language when the app has it.
        val chosen = preferenceHelper.isLanguageChosen || preferenceHelper.isFinishFirstFlow
        val languageCode = if (chosen) {
            preferenceHelper.languageSelected
        } else {
            listLanguage.deviceCode(Resources.getSystem().configuration.locales[0]) ?: preferenceHelper.languageSelected
        }
        super.attachBaseContext(LocateManager.createLocale(newBase, languageCode))
    }

    val dialogNoInternet by lazy {
        DialogNoInternet(this@BaseAppActivity) {
            openWifiSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeNetworkConnectivity(
            onNetworkAvailable = {
                dialogNoInternet.dismiss()
            },
            onNetworkLost = { dialogNoInternet.show() }) {
            dialogNoInternet.dismiss()
        }
    }
    fun View.internetClick(action: (View) -> Unit = {}) {
        click {
            if (isNetworkAvailable()) {
                action(this@internetClick)
            } else {
                dialogNoInternet.show()
            }
        }
    }

    fun View.internetSingleClick(action: (View) -> Unit = {}) {
        clickSingle {
            if (isNetworkAvailable()) {
                action(this@internetSingleClick)
            } else {
                dialogNoInternet.show()
            }
        }
    }

    fun runWithInternet(action: () -> Unit = {}) {
        if(isNetworkAvailable()){
            action()
        } else {
            dialogNoInternet.show()
        }
    }
}