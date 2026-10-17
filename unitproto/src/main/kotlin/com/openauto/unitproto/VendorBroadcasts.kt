package com.openauto.unitproto

/*
 * Broadcasts the smaller firmware families (and the odd vendor app) send
 * every app: the ignition and the unit's wake-up, and the wheel keys where a
 * firmware hands them out as broadcasts rather than key events. Collected
 * from open-headunit's receivers and the HeadUnit Revived auto-start guide;
 * none is documented by its maker.
 */
object AccBroadcasts {
    /** The car was switched on, or the unit woke from its sleep. */
    val ON = setOf(
        "com.mediatek.intent.action.QUICKBOOT_POWERON",
        "com.mediatek.intent.action.BOOT_IPO",
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
        "autochips.intent.action.QB_POWERON",
        "xy.android.acc.on",
        "com.glsx.boot.ACCON",
        "com.cayboy.action.ACC_ON",
        "com.carboy.action.ACC_ON",
        "android.intent.action.ACTION_MT_COMMAND_SLEEP_OUT"
    )

    /** The car was switched off, or the unit is shutting down. */
    val OFF = setOf(
        "com.cayboy.action.ACC_OFF",
        "com.carboy.action.ACC_OFF",
        "xy.android.acc.off",
        "com.glsx.boot.ACCOFF",
        "android.intent.action.ACTION_SHUTDOWN"
    )

    /**
     * The wake-ups a unit sends instead of BOOT_COMPLETED when its "fast boot"
     * brings it back from a sleep that looked like a power-off (MediaTek's IPO,
     * Autochips' quick boot): the launcher handles them like a boot.
     */
    val BOOT_LIKE = setOf(
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
        "com.mediatek.intent.action.QUICKBOOT_POWERON",
        "com.mediatek.intent.action.BOOT_IPO",
        "autochips.intent.action.QB_POWERON"
    )

    val ALL: Set<String> = ON + OFF

    /** True for on, false for off, null for a broadcast that isn't one of these. */
    fun decode(action: String?): Boolean? = when (action) {
        in ON -> true
        in OFF -> false
        else -> null
    }
}

/** One press of a unit key told by a vendor broadcast: which firmware's code space, the code, down or up. */
data class VendorKey(val vendor: String, val code: Int, val down: Boolean)

/**
 * The wheel and panel key broadcasts seen in the wild, each read into a
 * [VendorKey]. The extras come as a map so the decoding is Android-free.
 */
object KeyBroadcasts {
    const val NWD = "com.nwd.action.ACTION_KEY_VALUE"
    const val WINCA = "com.winca.service.Setting.KEY_ACTION"
    const val C3 = "android.intent.action.C3_HARDKEY"
    const val IKEY = "IKeyClick.KEY_CLICK"
    const val ERYANET_PREV = "com.eryanet.music.prev"
    const val ERYANET_NEXT = "com.eryanet.music.next"
    const val ERYANET_PLAY_PAUSE = "com.eryanet.media.playorpause"
    const val ERYANET_PLAY = "com.eryanet.media.play"
    const val ERYANET_PAUSE = "com.eryanet.media.pause"
    const val BZ_PICKUP = "com.bz.action.phone.pickup"
    const val BZ_HANGUP = "com.bz.action.phone.hangup"
    /** BYD's panel: a byte frame in "list", group 4 sub 1 = a key down, byte 2 the panel code. */
    const val BYD = "APP_REV_DATA"

    val ACTIONS = setOf(NWD, WINCA, C3, IKEY, ERYANET_PREV, ERYANET_NEXT, ERYANET_PLAY_PAUSE, ERYANET_PLAY, ERYANET_PAUSE, BZ_PICKUP, BZ_HANGUP, BYD)

    /** Made-up codes for the broadcasts that name the key instead of numbering it. */
    const val CODE_PREV = 1
    const val CODE_NEXT = 2
    const val CODE_PLAY_PAUSE = 3
    const val CODE_PLAY = 4
    const val CODE_PAUSE = 5
    const val CODE_PICKUP = 1
    const val CODE_HANGUP = 2

    fun decode(action: String?, extras: Map<String, Any?>): VendorKey? {
        fun int(vararg names: String): Int? = names.firstNotNullOfOrNull { (extras[it] as? Number)?.toInt() }
        return when (action) {
            NWD -> int("extra_key_value")?.takeIf { it != 0 }?.let { VendorKey("nwd", it and 0xFF, true) }
            WINCA -> int("com.winca.service.Setting.KEY_ACTION_EXTRA")?.takeIf { it >= 0 }?.let { VendorKey("winca", it, true) }
            C3 -> {
                val code = int("android.intent.extra.c3_hardkey_keycode") ?: return null
                val act = int("android.intent.extra.c3_hardkey_action") ?: return null
                if (code < 0) null else VendorKey("c3", code, down = act == 0)
            }
            IKEY -> int("CLICK_KEY")?.takeIf { it >= 0 }?.let { VendorKey("ikey", it, true) }
            ERYANET_PREV -> VendorKey("eryanet", CODE_PREV, true)
            ERYANET_NEXT -> VendorKey("eryanet", CODE_NEXT, true)
            ERYANET_PLAY_PAUSE -> VendorKey("eryanet", CODE_PLAY_PAUSE, true)
            ERYANET_PLAY -> VendorKey("eryanet", CODE_PLAY, true)
            ERYANET_PAUSE -> VendorKey("eryanet", CODE_PAUSE, true)
            BZ_PICKUP -> VendorKey("bz", CODE_PICKUP, true)
            BZ_HANGUP -> VendorKey("bz", CODE_HANGUP, true)
            BYD -> {
                val frame = extras["list"] as? ByteArray ?: return null
                if (frame.size < 3 || frame[0].toInt() != 4 || frame[1].toInt() != 1) return null
                VendorKey("byd", frame[2].toInt() and 0xFF, true)
            }
            else -> null
        }
    }
}
