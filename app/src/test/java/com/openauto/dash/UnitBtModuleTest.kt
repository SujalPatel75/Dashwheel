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
        assertTrue(UnitBtModule.misrouted(misroutedLog, mac))
    }

    @Test
    fun aLinkRoutedAsItShouldBeIsLeftAlone() {
        assertFalse(UnitBtModule.misrouted(healthyLog, mac))
        assertFalse(UnitBtModule.misrouted("", mac))
        assertFalse(UnitBtModule.misrouted(misroutedLog, "AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun theLastTryDecides() {
        // Misrouted once, then a new try that went through: nothing to restart.
        assertFalse(UnitBtModule.misrouted(misroutedLog + "\n" + healthyLog, mac))
        // A good try, then a misrouted one: restart.
        assertTrue(UnitBtModule.misrouted(healthyLog + "\n" + misroutedLog, mac))
    }

    @Test
    fun theDaemonsServiceIsFoundInGetprop() {
        assertEquals("gocsdk_zj", UnitBtModule.serviceName("[init.svc.gpsd]: [running]\n[init.svc.gocsdk_zj]: [running]"))
        assertEquals("gocsdk_zj", UnitBtModule.serviceName("nothing here"))
    }
}
