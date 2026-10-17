import org.jetbrains.kotlin.gradle.dsl.JvmTarget
        import java.text.SimpleDateFormat
        import java.util.Date

        plugins {
            alias(libs.plugins.android.application)
            alias(libs.plugins.gmsGoogleServices)
            alias(libs.plugins.firebaseCrashlytics)
            id("kotlin-parcelize")
            alias(libs.plugins.serialization)
            id(id = "com.google.devtools.ksp")
            alias(libs.plugins.tripletPlay)
        }

android {
    namespace = "com.af010.creator.docreader"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.af010.creator.docreader"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val formattedDate = SimpleDateFormat("MMM.dd.yyyy").format(Date())
        base.archivesName = "AF006-DocReader-Creator-v$versionName($versionCode)_${formattedDate}"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/keystore.jks")
            storePassword = "blueleft"
            keyAlias = "af006"
            keyPassword = "blueleft"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
           signingConfig = signingConfigs.getByName("release")
        }

        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    flavorDimensions.add("version")
    productFlavors {
        create("dev") {
            applicationId = "com.af010.creator.docreader"
            manifestPlaceholders["ad_app_id"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("boolean", "build_debug", "true")
        }

        create("product") {
            applicationId = "com.alldocs.reader.pdf.editor"
            manifestPlaceholders["ad_app_id"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("boolean", "build_debug", "false")
        }
    }

    bundle {
        language {
            enableSplit = false
        }
        abi {
            enableSplit = true
        }
    }
    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
dependencies {
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    implementation(libs.glide)
    implementation(libs.gson)
    implementation(libs.koin.android)
    implementation(libs.koin.android.compat)
    implementation(libs.koin.core)
    implementation(libs.lottie)
    implementation(libs.material)
    implementation(libs.review.ktx)

    implementation(libs.sdp.android)
    implementation(libs.shimmer)
    implementation(libs.ssp.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    implementation(libs.adlib)
    implementation(libs.inappupdate)

    implementation(project(":lib"))
    // Slim OpenCV build (core, imgproc, imgcodecs) for the document scanner.
    implementation(project(":opencv"))
    // The PDF view (zoom, text selection, annotations, forms) on top of the document SDK's pdfium.
   // implementation(project(":pdfview"))
    implementation(libs.docsdk)
    implementation(libs.androidx.viewpager2)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    annotationProcessor(libs.androidx.room.compiler)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
}
