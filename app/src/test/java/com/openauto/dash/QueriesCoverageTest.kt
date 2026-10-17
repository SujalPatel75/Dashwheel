package com.openauto.dash

import com.openauto.dash.unit.PlainAdapters
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every vendor package an adapter talks to must be declared in the
 * manifest's queries: with targetSdk 30 and later, an undeclared package is
 * invisible to the package manager, and the adapter would take the firmware's
 * app for absent.
 */
class QueriesCoverageTest {
    @Test
    fun everyVendorPackage_isDeclaredInTheManifest() {
        // Unit tests run with the module directory as their working directory.
        val manifest = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }.readText()
        val declared = Regex("""<package android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        val missing = PlainAdapters.ALL_VENDOR_PACKAGES - declared
        assertTrue("not in <queries>: $missing", missing.isEmpty())
    }
}
