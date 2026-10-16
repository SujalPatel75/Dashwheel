package com.openauto.unitproto.mtc

/*
 * The Microntek firmware's app-facing interface (MTCD / MTCE on Rockchip
 * PX3, PX5, PX6, PX30; HCT's MTCH / HCTGQ on Qualcomm QCM6125), as the
 * community worked it out (f1xpl/MtcdTools, Hal9k and Malaysk's XDA threads,
 * open-headunit): a CarManager class baked into the framework over the
 * "carservice" binder for keys and state, and a handful of broadcasts for
 * everything else. The Android side lives in the app; this file holds the
 * names, numbers and the small pure rules.
 */
object MtcProtocol {
    /** The framework class every MTC ROM ships (android.microntek.CarManager) and the binder behind it. */
    const val CAR_MANAGER_CLASS = "android.microntek.CarManager"
    const val CAR_SERVICE = "carservice"
    /** CarManager.attach(handler, type): the type for the unit's keys; the message's bundle has "type" = "key" and "value" = the code. */
    const val ATTACH_KEY_DOWN = "KeyDown"
    const val BUNDLE_TYPE = "type"
    const val BUNDLE_VALUE = "value"
    const val TYPE_KEY = "key"

    /** The unit's keys as broadcasts (PX3, and some ROMs beside the CarManager): extra [EXTRA_KEY_CODE]. */
    const val ACTION_IR_KEY_DOWN = "com.microntek.irkeyDown"
    const val ACTION_IR_KEY_UP = "com.microntek.irkeyUp"
    const val EXTRA_KEY_CODE = "keyCode"
    /** Hal9k's ROM: extras "type" and "value" like the CarManager's bundle. */
    const val ACTION_REPORT_EVENT = "com.microntek.report.event"

    /** Sent as the unit powers down: extra [EXTRA_CLASS] = [CLASS_POWEROFF]. */
    const val ACTION_BOOTCHECK = "com.microntek.bootcheck"
    const val EXTRA_CLASS = "class"
    const val CLASS_POWEROFF = "poweroff"
    /** Sent as the unit wakes from sleep (one guide's report; unconfirmed). */
    const val ACTION_START_APP = "com.microntek.startApp"

    /** The Vehicle app's car data while it runs: a 20-byte array ([EXTRA_SYNC]) with speed, revs, volts, temperature, mileage. */
    const val ACTION_SYNC = "com.microntek.sync"
    const val EXTRA_SYNC = "syncdata"
    const val ACTION_CANBUS_DISPLAY = "com.microntek.canbusdisplay"
    /** The reversing view's CAN frame (steering angle inside). */
    const val ACTION_CANBUS_BACKVIEW = "com.microntek.canbusbackview"
    const val ACTION_RADIO_REPORT = "com.microntek.radio.report"
    const val ACTION_FINISH = "com.microntek.finish"
    /** Sets the volume: extra "type" = "add" / "sub", or "volume" = a level. Works on some ROMs only. */
    const val ACTION_VOLUME_SET = "com.microntek.VOLUME_SET"

    /** The Vehicle app writes the doors here (Settings.System), 1 open; a system app's business to write, anyone's to read. */
    const val SETTINGS_DOOR = "com.microntek.controlinfo.door"
    const val SETTINGS_PREFIX = "com.microntek.controlinfo."

    /** The genuine firmware's sleep whitelist app. */
    const val PACKAGE_BOOTCONTROL = "com.microntek.bootcontrol"
    val PACKAGES = setOf(PACKAGE_BOOTCONTROL)

    /** The broadcasts worth hearing for the Signal Finder. */
    val BROADCASTS = setOf(
        ACTION_SYNC, ACTION_CANBUS_DISPLAY, ACTION_CANBUS_BACKVIEW, ACTION_RADIO_REPORT, ACTION_REPORT_EVENT,
        ACTION_BOOTCHECK, ACTION_START_APP, ACTION_IR_KEY_DOWN, ACTION_IR_KEY_UP
    )
}

/** The MCU's key codes (android.microntek.IRTable), the ones a steering wheel or panel sends. */
object MtcIrKeys {
    const val MODE = 256
    const val PLAY_PAUSE = 257
    const val MUTE = 258
    const val VOLUP = 273
    const val VOLDOWN = 281
    const val RADIO = 297
    const val PREV = 299
    const val NEXT = 300
    const val BT = 304
    const val GPS = 305
    const val BACK = 308
    const val MENU = 309
    const val HOME = 310
    const val ANSWER = 316
    const val HANGUP = 317
    const val SPEECH = 326
    const val MUSIC = 331
    const val VOICE = 366
    const val PWR = 2048

    /** A short name for the code, for the learning screen; null for one without a known meaning. */
    fun name(code: Int): String? = when (code) {
        MODE -> "mode"; PLAY_PAUSE -> "play_pause"; MUTE -> "mute"; VOLUP -> "volume_up"; VOLDOWN -> "volume_down"
        RADIO -> "radio"; PREV -> "previous"; NEXT -> "next"; BT -> "phone"; GPS -> "navi"; BACK -> "back"
        MENU -> "menu"; HOME -> "home"; ANSWER -> "answer"; HANGUP -> "hang_up"; SPEECH, VOICE -> "voice"
        MUSIC -> "music"; PWR -> "power"
        else -> null
    }
}

/**
 * The Vehicle app's 20-byte sync frame. Which byte is what is not public: a
 * [Layout] names the offsets once a tester confirmed them against the
 * dashboard; until then ([Layout.UNKNOWN]) nothing is decoded and the
 * Signal Finder's byte and bit matching is the way to learn from it.
 */
object MtcSync {
    const val LENGTH = 20

    /** Byte offsets into the frame; null = not known. [rpmAt] and [mileageAt] read big-endian words (2 and 3 bytes). */
    data class Layout(
        val speedAt: Int? = null,
        val rpmAt: Int? = null,
        val voltsTenthsAt: Int? = null,
        val tempAt: Int? = null,
        val mileageAt: Int? = null
    ) {
        companion object {
            val UNKNOWN = Layout()
        }
    }

    data class Data(val speedKmh: Int?, val rpm: Int?, val volts: Double?, val tempC: Int?, val mileageKm: Int?) {
        val isEmpty: Boolean get() = speedKmh == null && rpm == null && volts == null && tempC == null && mileageKm == null
    }

    /** The frame's fields by [layout], or null for a frame of the wrong size. */
    fun decode(bytes: ByteArray?, layout: Layout = Layout.UNKNOWN): Data? {
        if (bytes == null || bytes.size < LENGTH) return null
        fun u8(at: Int?): Int? = at?.takeIf { it in bytes.indices }?.let { bytes[it].toInt() and 0xFF }
        fun u16(at: Int?): Int? = at?.takeIf { it + 1 in bytes.indices }?.let { ((bytes[it].toInt() and 0xFF) shl 8) or (bytes[it + 1].toInt() and 0xFF) }
        fun u24(at: Int?): Int? = at?.takeIf { it + 2 in bytes.indices }?.let { (u16(it)!! shl 8) or (bytes[it + 2].toInt() and 0xFF) }
        return Data(
            speedKmh = u8(layout.speedAt)?.takeIf { it <= 400 },
            rpm = u16(layout.rpmAt)?.takeIf { it <= 12_000 },
            volts = u8(layout.voltsTenthsAt)?.let { it / 10.0 }?.takeIf { it in 6.0..20.0 },
            tempC = u8(layout.tempAt)?.let { if (it > 127) it - 256 else it }?.takeIf { it in -40..150 },
            mileageKm = u24(layout.mileageAt)
        )
    }
}
