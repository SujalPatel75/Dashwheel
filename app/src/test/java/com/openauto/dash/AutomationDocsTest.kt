package com.openauto.dash

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every command and event other apps can use is written up in docs/automation.md, with the names the code uses. */
class AutomationDocsTest {

    private val docs: String by lazy {
        // Unit tests run from the module's folder.
        listOf(File("../docs/automation.md"), File("docs/automation.md")).first { it.exists() }.readText()
    }

    @Test
    fun everyCommandIsDocumented() {
        Automation.Command.entries.forEach { assertTrue("${it.name} missing from docs/automation.md", docs.contains("`${it.name}`")) }
    }

    @Test
    fun everyEventIsDocumented() {
        Automation.Event.entries.forEach { assertTrue("${it.name} missing from docs/automation.md", docs.contains("`${it.name}`")) }
    }

    @Test
    fun theWheelActionsNamedExist() {
        val named = Regex("`([A-Z_]+)`").findAll(docs.substringAfter("Steering-wheel actions for").substringBefore("## Events")).map { it.groupValues[1] }.toList()
        assertTrue(named.isNotEmpty())
        named.filter { it != "RUN_ACTION" }.forEach { name ->
            assertTrue("$name is not a steering-wheel action", SteeringWheelAction.entries.any { it.name == name })
        }
    }

    @Test
    fun theSkinsNamedExist() {
        listOf("PANORAMA", "COCKPIT", "RACING").forEach { name -> assertTrue(DashThemeMode.entries.any { it.name == name }) }
    }
}
