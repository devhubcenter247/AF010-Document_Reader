// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.gmsGoogleServices) apply false
    alias(libs.plugins.firebaseCrashlytics) apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.tripletPlay) apply false
}
