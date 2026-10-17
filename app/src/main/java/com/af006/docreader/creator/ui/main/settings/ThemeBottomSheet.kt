package com.af010.creator.docreader.ui.main.settings

import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.af010.creator.docreader.R
import com.af010.creator.docreader.databinding.SheetThemeBinding
import com.af010.creator.docreader.model.ThemeMode
import com.ui.baselib.base.BaseBottomSheet
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.utils.dpToPx

class ThemeBottomSheet(
    activity: FragmentActivity,
    private var selected: ThemeMode,
    private val onDone: (ThemeMode) -> Unit
) : BaseBottomSheet<SheetThemeBinding>(activity, SheetThemeBinding::inflate) {

    override fun SheetThemeBinding.onBind() {
        header.tvTitle.setText(R.string.theme)
        header.btnClose.click { dismissSafe() }

        options().forEach { (mode, card, label) ->
            label.setText(mode.title)
            card.click {
                selected = mode
                render()
            }
        }
        btnDone.clickSingle {
            dismissSafe()
            onDone(selected)
        }
        render()
    }

    private fun SheetThemeBinding.options() = listOf(
        Triple(ThemeMode.Light, cardLight, tvLight),
        Triple(ThemeMode.Dark, cardDark, tvDark),
        Triple(ThemeMode.System, cardSystem, tvSystem)
    )

    private fun SheetThemeBinding.render() {
        val context = root.context
        options().forEach { (mode, card, label) ->
            val isSelected = mode == selected
            card.helper.bgColor = ContextCompat.getColor(
                context,
                if (isSelected) R.color.color_bg_selected else R.color.color_surface_card
            )
            card.helper.stColor = ContextCompat.getColor(
                context,
                if (isSelected) R.color.primary else R.color.color_stroke
            )
            card.helper.stWidth = context.dpToPx(if (isSelected) 2 else 1).toFloat()
            card.invalidate()
            label.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (isSelected) R.drawable.ic_app_radio_on else R.drawable.ic_app_radio_off, 0, 0, 0
            )
        }
    }
}
