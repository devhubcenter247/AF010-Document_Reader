package com.af010.creator.docreader.ui.main.tools

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.af010.creator.docreader.ui.main.tools.pick.PickFileActivity
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.databinding.LayoutOptionBinding
import com.af010.creator.docreader.databinding.LayoutToolFileBinding
import com.af010.creator.docreader.databinding.LayoutScreenBarBinding
import com.af010.creator.docreader.databinding.LayoutStepProgressBinding
import com.af010.creator.docreader.databinding.LayoutToolActionBinding
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.common.show
import com.af010.creator.docreader.ui.common.showTypeTile
import com.af010.creator.docreader.utils.AppUtils
import com.af010.creator.docreader.utils.extensions.iconRes
import java.io.File

/** Intent extra carrying the absolute paths of the files a tool screen works on. */
const val EXTRA_PATHS = "EXTRA_PATHS"

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

val File.isImage: Boolean
    get() = extension.lowercase() in IMAGE_EXTENSIONS

val File.typeIcon: Int
    get() = AppUtils.documentTypeOf(this)?.iconRes ?: when {
        isImage -> R.drawable.ic_file_image
        extension.equals("zip", ignoreCase = true) -> R.drawable.ic_file_zip
        else -> R.drawable.ic_file_txt
    }

fun File.sizeText(context: Context): String = Formatter.formatShortFileSize(context, length())

fun Context.pageCountText(count: Int): String =
    resources.getQuantityString(R.plurals.translate_page_count, count, count)

/**
 * The top bar of a tool: its name, an optional line under it, and the Pro chip when the
 * tool is a Premium one and the paywall is on.
 */
fun LayoutScreenBarBinding.show(tool: Tool, @StringRes title: Int = tool.title, subtitle: CharSequence? = null) {
    tvTitle.setText(title)
    tvSubtitle.text = subtitle
    tvSubtitle.isVisible = !subtitle.isNullOrEmpty()
    tvPro.isVisible = tool.isPremium && remoteConfig.paywallEnabled
}

/** "Step 2 of 3 · [detail]": every setup screen is the middle step between picking and the result. */
fun LayoutStepProgressBinding.showSetupStep(@StringRes detail: Int) =
    show(SETUP_STEP, TOOL_STEPS, root.context.getString(detail))

const val TOOL_STEPS = 3
const val SETUP_STEP = 2

fun LayoutToolFileBinding.show(file: File, meta: String) {
    tvType.showTypeTile(file)
    tvName.text = file.name
    tvName.isSelected = true
    tvMeta.text = meta
}

fun LayoutOptionBinding.show(@StringRes title: Int, desc: String, badge: String? = null) {
    tvTitle.setText(title)
    tvDesc.text = desc
    tvBadge.text = badge
    tvBadge.isVisible = badge != null
}

fun LayoutOptionBinding.setChecked(checked: Boolean) {
    root.isSelected = checked
}

fun LayoutToolActionBinding.show(text: CharSequence, @DrawableRes icon: Int) {
    btnAction.text = text
    btnAction.setIconResource(icon)
}

/**
 * "Change" on a one-file tool: opens the picker for [tool] again and hands the new file to
 * [onPicked]. Register it as a property, before the activity starts.
 */
class FileChanger(private val activity: ComponentActivity, private val tool: Tool, onPicked: (File) -> Unit) {
    private val launcher: ActivityResultLauncher<android.content.Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringArrayListExtra(EXTRA_PATHS)?.firstOrNull()?.let { onPicked(File(it)) }
        }

    fun launch() = launcher.launch(PickFileActivity.intent(activity, tool, forResult = true))
}
