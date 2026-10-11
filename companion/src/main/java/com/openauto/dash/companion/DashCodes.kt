package com.openauto.dash.companion

import com.openauto.dash.link.DashShareCode

/**
 * Dashboards shared as a code ("DW1:…", made by the launcher's Share): the
 * phone only finds the code, in a scanned QR code or a shared message, and
 * passes it to the car, which shows it before anything changes.
 */
object DashCodes {
    private val CODE = Regex("DW1:[A-Za-z0-9_-]+")

    fun find(text: String): String? = CODE.find(text)?.value?.takeIf { it.length <= DashShareCode.MAX_CHARS }

    /** Sends the code in [text] to the car; false when there is none, or no car on the link. */
    fun send(text: String): Boolean {
        val code = find(text) ?: return false
        if (LinkServer.state.value !is LinkState.Connected) return false
        LinkServer.send(DashShareCode(code))
        return true
    }
}
