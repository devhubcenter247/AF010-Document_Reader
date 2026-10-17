package com.af010.creator.docreader.ui.main

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivityMainBinding
import com.af010.creator.docreader.ui.dialog.QuitDialog
import com.af010.creator.docreader.ui.entry.AppShortcut
import com.af010.creator.docreader.ui.entry.PendingLaunch

class MainActivity : BaseAppActivity<ActivityMainBinding>(ActivityMainBinding::inflate) {

    override fun backPressed() {
        val navController = navController()
        if (navController.currentDestination?.id != R.id.filesFragment) {
            binding.navBar.selectItemById(R.id.filesFragment)
        } else {
            QuitDialog(this) { finish() }.show()
        }
    }

    override fun initialize() {
        AppShortcut.publish(this)
        PendingLaunch.consume(this)
    }

    override fun ActivityMainBinding.setData() {
        setupNavigation()
    }

    override fun ActivityMainBinding.onClick() = Unit

    fun selectTab(route: String) {
        val destinationId = when (route) {
            ROUTE_FILES -> R.id.filesFragment
            ROUTE_TOOLS -> R.id.toolsFragment
            ROUTE_SETTINGS -> R.id.settingsFragment
            else -> error("Unknown main tab route: $route")
        }
        binding.navBar.selectItemById(destinationId)
    }

    private fun setupNavigation() {
        val navController = navController()
        binding.navBar.setOnItemSelectedListener { item, _ ->
            if (navController.currentDestination?.id == item.id) {
                return@setOnItemSelectedListener
            }

            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    navController.graph.findStartDestination().id,
                    inclusive = false,
                    saveState = true
                )
                .build()
            navController.navigate(item.id, null, options)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.navBar.selectItemById(destination.id, triggerListener = false)
        }
    }

    private fun navController() =
        (supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment)
            .navController

    companion object {
        const val ROUTE_FILES = "files"
        const val ROUTE_TOOLS = "tools"
        const val ROUTE_SETTINGS = "settings"
    }
}
