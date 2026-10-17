package com.af010.creator.docreader.ui.main.tools.protect

import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityProtectBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.pdf.PdfToolbox
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.pageCountText
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.ui.main.tools.sizeText
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import org.koin.android.ext.android.inject
import java.io.File

class ProtectActivity : BaseAppActivity<ActivityProtectBinding>(ActivityProtectBinding::inflate) {

    private val toolbox: PdfToolbox by inject()
    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }
    private var passwordVisible = false

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivityProtectBinding.setData() {
        header.show(Tool.Protect, R.string.protect_pdf, R.string.protect_subtitle)
        file.show(source, source.sizeText(this@ProtectActivity))
        launchMain {
            val pages = runCatching { toolbox.pageCount(source) }.getOrNull() ?: return@launchMain
            file.tvMeta.text = getString(
                R.string.document_meta, pageCountText(pages),
                source.sizeText(this@ProtectActivity)
            )
        }
        action.btnAction.setText(R.string.lock_file)
    }

    override fun ActivityProtectBinding.onClick() {
        topBar.btnBack.clickSingle { finish() }
        btnShowPassword.click {
            passwordVisible = !passwordVisible
            val method = if (passwordVisible) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
            listOf(edtPassword, edtConfirm).forEach {
                it.transformationMethod = method
                it.setSelection(it.text.length)
            }
            btnShowPassword.setImageResource(
                if (passwordVisible) R.drawable.ic_app_eyeoff else R.drawable.ic_app_eye
            )
        }
        action.btnAction.clickSingle {
            val password = edtPassword.text.toString()
            when {
                password.isEmpty() -> toast(R.string.password_empty)
                password != edtConfirm.text.toString() -> toast(R.string.password_mismatch)
                else -> {
                    hideKeyboard()
                    ProcessingActivity.start(
                        this@ProtectActivity,
                        ToolRequest.Protect(source.path, password)
                    )
                    finish()
                }
            }
        }
    }
}
