
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

            resValue("string", "admob_app_Open_id", "ca-app-pub-3940256099942544/9257395921")

            resValue("string", "bannerSplash", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "bannerMain", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "banner_detail", "ca-app-pub-3940256099942544/6300978111")
            resValue("string", "banner_pack", "ca-app-pub-3940256099942544/6300978111")

            resValue("string", "Inter_Main", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "Inter_Back", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "Inter_OB", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "Inter_Splash", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "Inter_subCat", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "Inter_Add_WA", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "Inter_Add_Single", "ca-app-pub-3940256099942544/1033173712")
            resValue("string", "Inter_Download", "ca-app-pub-3940256099942544/1033173712")

            resValue("string", "native_language", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_language_dup", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_language_other", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_language_otherDup", "ca-app-pub-3940256099942544/2247696110")

            resValue("string", "native_ob1", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_ob2", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_ob3", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_ob4", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_obfull", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_obfull2", "ca-app-pub-3940256099942544/2247696110")

            resValue("string", "native_Ad_Detail", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_Main", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_Ad_Splash", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_subCat", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_setting", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_dialog", "ca-app-pub-3940256099942544/2247696110")
            resValue("string", "native_single", "ca-app-pub-3940256099942544/2247696110")
        }
    }

    buildFeatures {
        viewBinding = true
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

    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.2.1")

    // Firebase Remote Config (compile-time). Runtime needs google-services.json + the
    // com.google.gms.google-services plugin to initialize FirebaseApp.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config.ktx)

    // Shimmer (native/banner ad loading placeholders)
    implementation(libs.shimmer)

    // Scalable dp/sp used by the ported ad + onboarding layouts
    implementation(libs.intuit.sdp)
    implementation(libs.intuit.ssp)
}
