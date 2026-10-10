package com.openauto.dash.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The package under test: com.openauto.dash (GitHub) or com.dashwheel.app (Play), passed by the plugin. */
private val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId not passed: run through the baselineprofile plugin tasks")

/** A cold start of the dashboard, then a swipe each way along the middle row and back. */
private fun MacrobenchmarkScope.startAndSwipe() {
    pressHome()
    startActivityAndWait(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
    )
    device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5_000)
    // The whole window: a page swipe lands on the pager wherever it is.
    val root = device.findObject(By.pkg(packageName).depth(0)) ?: return
    root.setGestureMargin(device.displayWidth / 5)
    repeat(2) {
        root.swipe(Direction.LEFT, 0.8f)
        device.waitForIdle()
    }
    repeat(2) {
        root.swipe(Direction.RIGHT, 0.8f)
        device.waitForIdle()
    }
}

/**
 * Records the profile: ./gradlew :app:generateGithubReleaseBaselineProfile
 * (docs/baseline-profile.md). The device must be awake and unlocked.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndSwipes() = rule.collect(
        packageName = targetPackage,
        // Startup code is also written as the startup profile (DEX layout).
        includeInStartupProfile = true,
    ) {
        startAndSwipe()
    }
}

/**
 * Measures what the profile buys: the same cold start compiled with and without
 * it. ./gradlew :baselineprofile:connectedGithubBenchmarkReleaseAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupWithoutProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithProfile() = startup(CompilationMode.Partial())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = targetPackage,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        startAndSwipe()
    }
}
