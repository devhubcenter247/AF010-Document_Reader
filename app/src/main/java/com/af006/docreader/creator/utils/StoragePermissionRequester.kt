package com.af010.creator.docreader.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

/**
 * Asks for the file access the app needs: "All files access" on Android 11+, read/write
 * storage below. Must be created as a field initializer (before the host is STARTED).
 * [onResult] runs when the system screen closes, whether or not access was granted.
 */
class StoragePermissionRequester(
    caller: ActivityResultCaller,
    private val onResult: () -> Unit
) {
    private val manageStorageLauncher = caller.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onResult()
    }

    private val requestPermissionLauncher = caller.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val activity = requestedFrom
        requestedFrom = null
        // The system no longer asks (denied twice): only the app's settings can grant it.
        val blocked = activity != null && !AppUtils.hasStoragePermission(activity) &&
                !couldAskAgain && !canAskAgain(activity)
        if (activity != null && blocked) openAppSettings(activity) else onResult()
    }

    private var requestedFrom: Activity? = null
    private var couldAskAgain = false

    fun request(context: Context) {
        if (AppUtils.hasStoragePermission(context)) {
            onResult()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
            PermissionSettingsWatcher.watch(context) { AppUtils.hasStoragePermission(context) }
        } else {
            requestedFrom = context as? Activity
            couldAskAgain = requestedFrom?.let(::canAskAgain) == true
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun canAskAgain(activity: Activity) = ActivityCompat.shouldShowRequestPermissionRationale(
        activity, Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private fun openAppSettings(activity: Activity) {
        manageStorageLauncher.launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", activity.packageName, null))
        )
        PermissionSettingsWatcher.watch(activity) { AppUtils.hasStoragePermission(activity) }
    }
}
