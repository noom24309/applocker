plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "app.lock.photo.valut"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.privatelock.vault.applock"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )


            resValue("string", "admob_app_id", "ca-app-pub-3940256099942544~3347511713")
            resValue("string", "admob_app_open_id", "ca-app-pub-3940256099942544/9257395921")
            resValue("string", "nativeSplash", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "InterSplash", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "NativeLanaguge", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "NativeLanagugeDup", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "NativeLanagugeOther", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "NativeLanagugeOtherDup", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "InterLanaguge", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "InterOB", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "OB1", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "OB2", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "OB3", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "OB4", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "OB5", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "OBFull", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "OBFull2", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "bannerAll", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "InterAd_hf", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "InterAd_lf", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "nativeAll", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "bannerRemovebg", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "bannerEditImage", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "bannerAiCut", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "bannerEditVideo", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "bannerText", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "bannerPacks", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "nativeCreate", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeAiCut", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeEditVideo", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeTrim", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeDialog", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeText", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativePacks", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeDetail", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeSingle", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeremoveBg", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeEditImage", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "nativeStickerPreview", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "rewardedId", "/21775744923/example/rewarded_interstitial")
        }
    }



    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)

    // Lifecycle / ViewModel / StateFlow
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Biometric
    implementation(libs.androidx.biometric)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // RecyclerView + image loading + media playback
    implementation(libs.androidx.recyclerview)
    implementation(libs.glide)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // CameraX (Private Camera)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase Remote Config (compile-time). Runtime needs google-services.json + the
    // com.google.gms.google-services plugin to initialize FirebaseApp.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config.ktx)

    // Scalable dp/sp used by the ported onboarding/language layouts
    implementation(libs.intuit.sdp)
    implementation(libs.intuit.ssp)

    //admobs
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation(project(":ads"))
}
