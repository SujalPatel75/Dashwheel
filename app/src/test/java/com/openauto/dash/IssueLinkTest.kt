package com.openauto.dash

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/** The GitHub issues a crash or a compatibility report opens, filled in. */
class IssueLinkTest {

    private val facts = listOf("Device" to "ROCO K706", "Firmware" to "QF")

    @Test
    fun compatibilityReportSaysWhatWorks() {
        val body = IssueLink.compatibilityBody(facts, mapOf(ReportItem.WHEEL to ReportAnswer.WORKS, ReportItem.MAPS to ReportAnswer.FAILS), null)
        assertTrue(body.contains("- Device: ROCO K706"))
        assertTrue(body.contains("- wheel: works"))
        assertTrue(body.contains("- maps: does NOT work"))
        assertTrue(body.contains("- obd: not tried"))
    }

    @Test
    fun addressStaysScannable() {
        val trace = (1..400).joinToString("\n") { "    at com.openauto.dash.Something.method$it(Something.kt:$it)" }
        val url = IssueLink.url("o", "r", "Crash: java.lang.IllegalStateException", IssueLink.crashBody(facts, "java.lang.IllegalStateException: boom\n$trace"), "crash")
        assertTrue(url.length <= IssueLink.MAX_URL)
        val body = URLDecoder.decode(url.substringAfter("&body="), "UTF-8")
        assertTrue(body.contains("IllegalStateException: boom"))
        assertTrue(url.startsWith("https://github.com/o/r/issues/new?labels=crash&title="))
    }
}
