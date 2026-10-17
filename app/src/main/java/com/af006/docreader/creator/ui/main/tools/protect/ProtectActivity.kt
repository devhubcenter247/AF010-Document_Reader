package com.af010.creator.docreader.ui.main.tools.protect

import android.content.res.ColorStateList
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityProtectBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.model.ToolRequest
import com.af010.creator.docreader.ui.main.tools.EXTRA_PATHS
import com.af010.creator.docreader.ui.main.tools.result.ProcessingActivity
import com.af010.creator.docreader.ui.main.tools.show
import com.af010.creator.docreader.ui.main.tools.showSetupStep
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import java.io.File

/** Asks for the password twice, rating it as it is typed. */
class ProtectActivity : BaseAppActivity<ActivityProtectBinding>(ActivityProtectBinding::inflate) {

    /** How the password rates: how many of the four bars light up, their colour and the word. */
    private enum class Strength(val bars: Int, @ColorRes val color: Int, @StringRes val label: Int) {
        Weak(1, R.color.color_file_pdf, R.string.fo_pw_weak),
        Okay(2, R.color.color_tool_amber, R.string.fo_pw_okay),
        Good(3, R.color.color_file_excel, R.string.fo_pw_good),
        Strong(4, R.color.color_file_excel, R.string.fo_pw_strong);

        companion object {
            fun of(password: String): Strength {
                val kinds = listOf(
                    password.any(Char::isLowerCase),
                    password.any(Char::isUpperCase),
                    password.any(Char::isDigit),
                    password.any { !it.isLetterOrDigit() }
                ).count { it }
                return when {
                    password.length < 6 -> Weak
                    password.length < 8 || kinds < 2 -> Okay
                    password.length < 12 && kinds < 3 -> Good
                    else -> Strong
                }
            }
        }
    }

    private val source: File by lazy { File(intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty().first()) }
    private var passwordVisible = false

    override fun backPressed() = finish()

    override fun initialize() = Unit

    override fun ActivityProtectBinding.setData() {
        topBar.show(Tool.Protect, R.string.protect_pdf)
        steps.showSetupStep(R.string.fo_step_pick_password)
        tvFileName.text = source.name
        action.show(getString(R.string.fo_lock_this_file), R.drawable.ic_fo_lock)
        render()
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
            btnShowPassword.setImageResource(if (passwordVisible) R.drawable.ic_fo_eye_off else R.drawable.ic_fo_eye)
            btnShowPassword.contentDescription =
                getString(if (passwordVisible) R.string.fo_hide_password else R.string.fo_show_password)
        }
        edtPassword.afterTextChanged { render() }
        edtConfirm.afterTextChanged { render() }
        action.btnAction.clickSingle {
            val password = edtPassword.text.toString()
            when {
                password.isEmpty() -> toast(R.string.password_empty)
                password != edtConfirm.text.toString() -> toast(R.string.password_mismatch)
                else -> {
                    hideKeyboard()
                    ProcessingActivity.start(this@ProtectActivity, ToolRequest.Protect(source.path, password))
                    finish()
                }
            }
        }
    }

    private fun ActivityProtectBinding.render() {
        val password = edtPassword.text.toString()
        layoutStrength.isInvisible = password.isEmpty()
        ivMatch.isVisible = password.isNotEmpty() && password == edtConfirm.text.toString()
        if (password.isEmpty()) return
        val strength = Strength.of(password)
        val lit = ColorStateList.valueOf(getColor(strength.color))
        val off = ColorStateList.valueOf(getColor(R.color.color_handle))
        if (layoutBars.childCount == 0) {
            val gap = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._4sdp)
            repeat(Strength.entries.size) { index ->
                layoutBars.addView(View(this@ProtectActivity).apply {
                    setBackgroundResource(R.drawable.bg_badge)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (index > 0) marginStart = gap
                    }
                })
            }
        }
        for (index in 0 until layoutBars.childCount) {
            layoutBars.getChildAt(index).backgroundTintList = if (index < strength.bars) lit else off
        }
        tvStrength.setText(strength.label)
        tvStrength.setTextColor(lit)
    }
}
