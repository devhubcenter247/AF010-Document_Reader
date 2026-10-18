package com.af010.creator.docreader

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.multidex.MultiDexApplication
import com.af010.creator.docreader.app.remote.remoteConfig
import com.af010.creator.docreader.utils.PreferenceHelper
import com.editor.docsdk.DialogStyle
import com.editor.docsdk.EditStyle
import com.af010.creator.docreader.utils.LocateManager
import com.af010.creator.docreader.di.appModule
import com.af010.creator.docreader.di.viewModelModule
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class DocumentApplication : MultiDexApplication(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        LocateManager.initDeviceLocate()
        startKoin {
            androidLogger()
            androidContext(this@DocumentApplication)
            modules(
                listOf(
                    appModule,
                    viewModelModule,
                )
            )
        }
        AppCompatDelegate.setDefaultNightMode(get<PreferenceHelper>().themeMode.nightMode)
        // The document SDK's dialogs (its error dialog, colour and border pickers…) in the app's look.
        DialogStyle.customizer = DialogStyle.Customizer { context, style ->
            style.copy(
                background = ContextCompat.getColor(context, R.color.color_surface_card),
                title = ContextCompat.getColor(context, R.color.text_primary),
                text = ContextCompat.getColor(context, R.color.text_secondary),
                caption = ContextCompat.getColor(context, R.color.text_secondary),
                accent = ContextCompat.getColor(context, R.color.primary),
                divider = ContextCompat.getColor(context, R.color.color_stroke),
                rowPressed = ContextCompat.getColor(context, R.color.color_bg_selected),
                cornerDp = 20f
            )
        }
        // Its context bar (the commands for the selection, over the keyboard) in the ribbon's look.
        EditStyle.customizer = EditStyle.Customizer { context, style ->
            style.copy(
                toolbarBackground = ContextCompat.getColor(context, R.color.color_surface_card),
                divider = ContextCompat.getColor(context, R.color.color_stroke),
                icon = ContextCompat.getColor(context, R.color.text_primary),
                iconActive = ContextCompat.getColor(context, R.color.text_on_selected),
                activeBackground = ContextCompat.getColor(context, R.color.color_bg_selected),
                deleteIcon = ContextCompat.getColor(context, R.color.color_error),
                accent = ContextCompat.getColor(context, R.color.primary),
                onAccent = ContextCompat.getColor(context, R.color.on_primary),
                tabText = ContextCompat.getColor(context, R.color.text_secondary),
                labelText = ContextCompat.getColor(context, R.color.text_secondary)
            )
        }
        FirebaseApp.initializeApp(this)
        remoteConfig.fetchRemoteConfigAsync(
            CoroutineScope(Dispatchers.IO + SupervisorJob()),
            BuildConfig.DEBUG
        )


    }
}
