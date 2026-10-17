package com.af010.creator.docreader.ui.entry

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.Tool
import com.af010.creator.docreader.ui.main.newdocument.NewDocumentActivity
import com.af010.creator.docreader.ui.main.search.SearchActivity
import com.af010.creator.docreader.ui.main.tools.openTool
import com.af010.creator.docreader.ui.splash.SplashActivity

/** The entries shown when the launcher icon is long-pressed, first one nearest to the icon. */
enum class AppShortcut(@StringRes val label: Int, @DrawableRes val icon: Int) {
    Scan(R.string.scan, R.drawable.ic_app_tool_scan),
    Search(R.string.search_files, R.drawable.ic_shortcut_search),
    ImagesToPdf(R.string.images_to_pdf, R.drawable.ic_app_tool_images_to_pdf),
    NewDocument(R.string.new_document, R.drawable.ic_shortcut_new_document);

    fun open(activity: Activity) {
        when (this) {
            Scan -> activity.openTool(Tool.Scan)
            Search -> activity.startActivity(Intent(activity, SearchActivity::class.java))
            ImagesToPdf -> activity.openTool(Tool.ImagesToPdf)
            NewDocument -> activity.startActivity(Intent(activity, NewDocumentActivity::class.java))
        }
    }

    companion object {
        const val ACTION = "com.af010.creator.docreader.action.SHORTCUT"
        const val EXTRA_SHORTCUT = "extra_shortcut"

        fun from(intent: Intent?): AppShortcut? {
            if (intent?.action != ACTION) return null
            val name = intent.getStringExtra(EXTRA_SHORTCUT)
            return entries.firstOrNull { it.name == name }
        }

        /**
         * Dynamic rather than `shortcuts.xml`: the labels follow the language chosen in the app
         * and the target does not depend on the flavor's applicationId. Every shortcut starts
         * [SplashActivity], the only way in, so the first-run flow still comes first.
         */
        fun publish(context: Context) {
            val shortcuts = entries.map { shortcut ->
                val label = context.getString(shortcut.label)
                ShortcutInfoCompat.Builder(context, shortcut.name)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, shortcut.icon))
                    .setRank(shortcut.ordinal)
                    .setIntent(
                        Intent(context, SplashActivity::class.java)
                            .setAction(ACTION)
                            .putExtra(EXTRA_SHORTCUT, shortcut.name)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    .build()
            }
            runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
        }
    }
}
