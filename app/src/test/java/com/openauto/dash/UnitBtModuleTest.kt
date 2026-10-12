package com.openauto.dash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The K706's Bluetooth module giving the OBD link to Android Auto, read from its own log (lines from the car, 2026-10-12). */
class UnitBtModuleTest {

    private val mac = "01:23:45:67:89:BA"

    private val misroutedLog = """
        D/sd      (31185): obd_addr:0123456789BA
        D/sd      (31185): uart send:32 byte/s
        D/sd      (31185): hlp_gap_conn_event_cbk:1,2,00
        D/sd      (31185): acl connected
        D/sd      (31185): spp_connect: wireless_conn[0x01]
        D/sd      (31185): check_iap_addr:40137
        D/sd      (31185): aa connected.., handle:40137.
        D/sd      (31185): send_current_phone_support_iap
    """.trimIndent()

    private val healthyLog = """
        D/sd      ( 6743): obd_addr:0123456789BA
        D/sd      ( 6743): acl connected
        D/sd      ( 6743): spp_connect: wireless_conn[0x01]
        D/sd      ( 6743): check_iap_addr:40045
        D/sd      ( 6743): spp connected, handle:40045. spp_connect_status=0
        D/sd      ( 6743): obd_connected_addr:BA8967452301
    """.trimIndent()

    @Test
    fun theLinkGivenToAndroidAutoIsSeen() {
        assertTrue(UnitBtModule.read(misroutedLog, mac) == UnitBtModule.Finding.MISROUTED)
    }

    @Test
    fun aLinkRoutedAsItShouldBeIsLeftAlone() {
        assertEquals(UnitBtModule.Finding.NONE, UnitBtModule.read(healthyLog, mac))
        assertEquals(UnitBtModule.Finding.NONE, UnitBtModule.read("", mac))
        assertEquals(UnitBtModule.Finding.NONE, UnitBtModule.read(misroutedLog, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun theLastTryDecides() {
        // Misrouted once, then a new try that went through: nothing to restart.
        assertEquals(UnitBtModule.Finding.NONE, UnitBtModule.read(misroutedLog + "\n" + healthyLog, mac))
        // A good try, then a misrouted one: restart.
        assertEquals(UnitBtModule.Finding.MISROUTED, UnitBtModule.read(healthyLog + "\n" + misroutedLog, mac))
    }

    @Test
    fun theDaemonsServiceIsFoundInGetprop() {
        assertEquals("gocsdk_zj", UnitBtModule.serviceName("[init.svc.gpsd]: [running]\n[init.svc.gocsdk_zj]: [running]"))
        assertEquals("gocsdk_zj", UnitBtModule.serviceName("nothing here"))
    }

    /** The daemon with the phone's wireless Android Auto going round, and not one try at the adapter. */
    private val jammedLog = (1..30).joinToString("\n") {
        """
        D/sd      (15972): uart recv:5 byte/s
        D/sd      (15972): uart send:3 byte/s
        D/sd      (15972): spp_disconnect: wireless_conn[0x02]
        D/sd      (15972): SUPPORT_ZJ_CARPLAY, AA DISCONN
        D/sd      (15972): aa connected.., handle:e0051.
        D/sd      (15972): SUPPORT_ZJ_CARPLAY, AA CONNECT
        """.trimIndent()
    }

    @Test
    fun requestsIgnoredWhileAndroidAutoGoesRoundAreSeen() {
        assertEquals(UnitBtModule.Finding.IGNORED, UnitBtModule.read(jammedLog, mac))
        assertTrue(UnitBtModule.androidAutoLoop(jammedLog))
        // A short log says too little to call the adapter ignored.
        assertEquals(UnitBtModule.Finding.NONE, UnitBtModule.read("D/sd      (1): uart recv:5 byte/s", mac))
        assertFalse(UnitBtModule.androidAutoLoop(healthyLog))
    }
}
