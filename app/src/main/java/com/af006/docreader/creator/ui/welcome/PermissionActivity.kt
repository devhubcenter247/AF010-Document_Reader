package com.af010.creator.docreader.ui.welcome

import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityPermissionBinding
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.ui.baselib.extensions.clickSingle

class PermissionActivity : BaseAppActivity<ActivityPermissionBinding>(ActivityPermissionBinding::inflate) {

    override fun backPressed() {
        finishPermissionFlow()
    }

    private val permissionRequester = StoragePermissionRequester(this) {
        finishPermissionFlow()
    }

    override fun initialize() {

    }

    override fun ActivityPermissionBinding.setData() {

    }

    override fun ActivityPermissionBinding.onClick() {
        btnAllowAccess.clickSingle {
            permissionRequester.request(this@PermissionActivity)
        }

        btnNotNow.clickSingle {
            finishPermissionFlow()
        }
    }

    private fun finishPermissionFlow() {
        preferenceHelper.isFinishFirstFlow = true
        launchAndFinish<MainActivity>()
    }
}