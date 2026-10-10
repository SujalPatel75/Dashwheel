// Records the launcher's baseline profile: a cold start and a few swipes run on
// an emulator or test device, and the code they touch is written to the app's
// sources. Never part of a normal build or of CI. See docs/baseline-profile.md.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.openauto.dash.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Profile recording needs Android 9+ with root (an emulator), or Android 13+.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // The app's two editions; each records into its own variant.
    flavorDimensions += "edition"
    productFlavors {
        create("github") { dimension = "edition" }
        create("play") { dimension = "edition" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Runs on the device adb already sees (the HeadUnit_K706 AVD), not a managed one.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
