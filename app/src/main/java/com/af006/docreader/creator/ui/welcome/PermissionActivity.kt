package com.af010.creator.docreader.ui.welcome

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityPermissionBinding
import com.af010.creator.docreader.databinding.LayoutDocCardBinding
import com.af010.creator.docreader.databinding.LayoutPointCardBinding
import com.af010.creator.docreader.ui.common.show
import com.af010.creator.docreader.ui.main.MainActivity
import com.af010.creator.docreader.ui.main.premium.IntroPaywallActivity
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.ui.baselib.extensions.clickSingle

/** The last first-run step. Allowing access leads to the one-time offer; "Maybe later" goes straight in. */
class PermissionActivity : BaseAppActivity<ActivityPermissionBinding>(ActivityPermissionBinding::inflate) {

    private val permissionRequester = StoragePermissionRequester(this) { finishFirstRun(showOffer = true) }

    override fun initialize() = Unit

    override fun backPressed() = finishFirstRun(showOffer = false)

    override fun ActivityPermissionBinding.setData() {
        steps.show(current = 3, total = 3)
        cardPdf.tag("PDF", R.drawable.bg_chip_pdf, R.color.color_file_pdf, tilt = -10f)
        cardDocx.tag("DOCX", R.drawable.bg_chip_word, R.color.color_file_word, tilt = 8f)
        pointList.show(R.drawable.ic_fo_folder, R.string.fo_access_point_1, R.string.fo_access_point_1_desc)
        pointPrivate.show(R.drawable.ic_fo_shield, R.string.fo_access_point_2, R.string.fo_access_point_2_desc)
        pointInPlace.show(R.drawable.ic_fo_pen, R.string.fo_access_point_3, R.string.fo_access_point_3_desc)
    }

    override fun ActivityPermissionBinding.onClick() {
        btnAllowAccess.clickSingle { permissionRequester.request(this@PermissionActivity) }
        btnNotNow.clickSingle { finishFirstRun(showOffer = false) }
    }

    private fun LayoutPointCardBinding.show(@DrawableRes icon: Int, @StringRes title: Int, @StringRes desc: Int) {
        ivIcon.setImageResource(icon)
        tvTitle.setText(title)
        tvDesc.setText(desc)
    }

    /** The little documents in the folder; <include> drops rotation, so it is set here. */
    private fun LayoutDocCardBinding.tag(type: String, @DrawableRes chip: Int, @ColorRes ink: Int, tilt: Float) {
        root.rotation = tilt
        tvChip.text = type
        tvChip.setBackgroundResource(chip)
        tvChip.setTextColor(getColor(ink))
    }

    private fun finishFirstRun(showOffer: Boolean) {
        preferenceHelper.isFinishFirstFlow = true
        if (showOffer) launchAndFinish<IntroPaywallActivity>() else launchAndFinish<MainActivity>()
    }
}
