// A fake head unit firmware for the emulator: pretends to be the FYT main
// server app (com.syu.ms) with its toolkit service, and relays other
// firmwares' broadcasts, so Dashwheel's adapters can be exercised without
// the hardware. Debug only; never shipped. See tools/fakeunit/README.md.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openauto.fakeunit"
    compileSdk = 36

    defaultConfig {
        // The package the FYT adapter binds: the fake takes its place on the emulator.
        applicationId = "com.syu.ms"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "fake"
    }

    buildTypes {
        release {
            // Never a release: the debug build is the only one meant to exist.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // The FYT descriptors, transaction codes and module numbers, the same the launcher speaks.
    implementation(project(":unitproto"))
}
