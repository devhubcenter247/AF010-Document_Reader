package com.af010.creator.docreader.ui.main

import android.view.View
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityMainBinding
import com.af010.creator.docreader.ui.dialog.QuitDialog
import com.af010.creator.docreader.ui.entry.AppShortcut
import com.af010.creator.docreader.ui.entry.PendingLaunch
import com.af010.creator.docreader.ui.main.files.sheet.CreateBottomSheet
import com.af010.creator.docreader.ui.main.newdocument.createBlankDocument
import com.af010.creator.docreader.ui.main.tools.openTool
import com.ui.baselib.extensions.clickSingle

/** Files, Tools and Settings under one floating bar, with the "+" for making something new. */
class MainActivity : BaseAppActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    private val nav: NavController
        get() = (supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment).navController

    /** Each tab of the bar and the destination it shows. */
    private val tabs: Map<View, Int>
        get() = with(binding) {
            mapOf(tabFiles to R.id.filesFragment, tabTools to R.id.toolsFragment, tabSettings to R.id.settingsFragment)
        }

    override fun initialize() {
        AppShortcut.publish(this)
        PendingLaunch.consume(this)
    }

    override fun backPressed() {
        if (nav.currentDestination?.id != R.id.filesFragment) show(R.id.filesFragment)
        else QuitDialog(this) { finish() }.show()
    }

    override fun ActivityMainBinding.setData() {
        nav.addOnDestinationChangedListener { _, destination, _ ->
            tabs.forEach { (tab, id) -> tab.isSelected = id == destination.id }
        }
    }

    override fun ActivityMainBinding.onClick() {
        tabs.forEach { (tab, id) -> tab.setOnClickListener { show(id) } }
        btnCreate.clickSingle { showCreateSheet() }
    }

    fun selectTab(route: String) = show(
        when (route) {
            ROUTE_FILES -> R.id.filesFragment
            ROUTE_TOOLS -> R.id.toolsFragment
            ROUTE_SETTINGS -> R.id.settingsFragment
            else -> error("Unknown main tab route: $route")
        }
    )

    fun showCreateSheet() {
        CreateBottomSheet(
            this,
            onTool = { openTool(it) },
            onNewDocument = { createBlankDocument(it) }
        ).show()
    }

    /** Switches tab, keeping each tab's state, with Files at the root of the back stack. */
    private fun show(destination: Int) {
        if (nav.currentDestination?.id == destination) return
        nav.navigate(
            destination, null,
            NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(nav.graph.findStartDestination().id, inclusive = false, saveState = true)
                .build()
        )
    }

    companion object {
        const val ROUTE_FILES = "files"
        const val ROUTE_TOOLS = "tools"
        const val ROUTE_SETTINGS = "settings"
    }
}
