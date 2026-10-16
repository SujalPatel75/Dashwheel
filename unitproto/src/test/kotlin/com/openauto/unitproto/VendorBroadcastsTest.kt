package com.openauto.unitproto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorBroadcastsTest {

    @Test
    fun accBroadcasts_decodeToOnOrOff() {
        assertEquals(true, AccBroadcasts.decode("com.glsx.boot.ACCON"))
        assertEquals(true, AccBroadcasts.decode("com.mediatek.intent.action.BOOT_IPO"))
        assertEquals(false, AccBroadcasts.decode("com.cayboy.action.ACC_OFF"))
        assertEquals(false, AccBroadcasts.decode("android.intent.action.ACTION_SHUTDOWN"))
        assertNull(AccBroadcasts.decode("com.qf.action.ACC_ON"))
        assertNull(AccBroadcasts.decode(null))
        assertTrue(AccBroadcasts.BOOT_LIKE.all { it in AccBroadcasts.ON })
    }

    @Test
    fun nwdAndWinca_carryTheCodeInAnExtra() {
        assertEquals(VendorKey("nwd", 12, true), KeyBroadcasts.decode(KeyBroadcasts.NWD, mapOf("extra_key_value" to 12.toByte())))
        assertNull(KeyBroadcasts.decode(KeyBroadcasts.NWD, mapOf("extra_key_value" to 0.toByte())))
        assertEquals(VendorKey("winca", 7, true), KeyBroadcasts.decode(KeyBroadcasts.WINCA, mapOf("com.winca.service.Setting.KEY_ACTION_EXTRA" to 7)))
        assertNull(KeyBroadcasts.decode(KeyBroadcasts.WINCA, emptyMap()))
    }

    @Test
    fun c3_tellsDownFromUp() {
        val extras = mapOf("android.intent.extra.c3_hardkey_keycode" to 24, "android.intent.extra.c3_hardkey_action" to 0)
        assertEquals(VendorKey("c3", 24, true), KeyBroadcasts.decode(KeyBroadcasts.C3, extras))
        assertEquals(VendorKey("c3", 24, false), KeyBroadcasts.decode(KeyBroadcasts.C3, extras + ("android.intent.extra.c3_hardkey_action" to 1)))
        assertNull(KeyBroadcasts.decode(KeyBroadcasts.C3, mapOf("android.intent.extra.c3_hardkey_keycode" to 24)))
    }

    @Test
    fun namedKeys_getFixedCodes() {
        assertEquals(VendorKey("eryanet", KeyBroadcasts.CODE_NEXT, true), KeyBroadcasts.decode(KeyBroadcasts.ERYANET_NEXT, emptyMap()))
        assertEquals(VendorKey("bz", KeyBroadcasts.CODE_HANGUP, true), KeyBroadcasts.decode(KeyBroadcasts.BZ_HANGUP, emptyMap()))
    }

    @Test
    fun byd_readsThePanelCodeFromItsFrame() {
        assertEquals(VendorKey("byd", 0x11, true), KeyBroadcasts.decode(KeyBroadcasts.BYD, mapOf("list" to byteArrayOf(4, 1, 0x11))))
        assertNull(KeyBroadcasts.decode(KeyBroadcasts.BYD, mapOf("list" to byteArrayOf(3, 1, 0x11))))
        assertNull(KeyBroadcasts.decode(KeyBroadcasts.BYD, mapOf("list" to byteArrayOf(4))))
        assertNull(KeyBroadcasts.decode("something.else", emptyMap()))
    }
}
