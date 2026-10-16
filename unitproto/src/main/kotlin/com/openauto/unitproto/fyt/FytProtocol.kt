package com.openauto.unitproto.fyt

/*
 * The FYT / SYU firmware's app-facing interface, as the community worked it
 * out (AxesOfEvil/FYTCanbusMonitor, Pacjonek/fyt-dialer, open-headunit,
 * cssbruno/honda-customizer): one "toolkit" service in the firmware's main
 * server app, com.syu.ms, handing out one binder per module (radio, Bluetooth,
 * CAN bus...). Each module takes commands, answers reads, and pushes updates
 * to registered callbacks; everything travels as an update code with int,
 * float and string arrays.
 *
 * The Android side (binding, parcels) lives in the app; this file holds the
 * names, numbers and the small pure rules, so they are unit-tested here and
 * shared with the fake firmware (:fakeunit).
 */
object FytProtocol {
    /** The firmware's main server app, which hosts the toolkit service. */
    const val PACKAGE = "com.syu.ms"
    /** The toolkit service's class and the action its intent filter declares. */
    const val SERVICE_CLASS = "app.ToolkitService"
    const val ACTION = "com.syu.ms.toolkit"

    const val DESCRIPTOR_TOOLKIT = "com.syu.ipc.IRemoteToolkit"
    const val DESCRIPTOR_MODULE = "com.syu.ipc.IRemoteModule"
    const val DESCRIPTOR_CALLBACK = "com.syu.ipc.IModuleCallback"

    /** IRemoteToolkit: getRemoteModule(int moduleCode) → IRemoteModule. */
    const val TX_GET_MODULE = 1

    /** IRemoteModule: cmd(int code, int[] ints, float[] flts, String[] strs). */
    const val TX_CMD = 1
    /** IRemoteModule: get(int code, int[], float[], String[]) → ModuleObject. */
    const val TX_GET = 2
    /** IRemoteModule: register(IModuleCallback cb, int updateCode, int flag); flag 1 also pushes the current value. */
    const val TX_REGISTER = 3
    /** IRemoteModule: unregister(IModuleCallback cb, int updateCode). */
    const val TX_UNREGISTER = 4

    /** IModuleCallback: update(int updateCode, int[] ints, float[] flts, String[] strs); one way. */
    const val TX_UPDATE = 1

    /** The value of register's flag that asks for the current value at once. */
    const val REGISTER_WITH_VALUE = 1

    /**
     * Whether a module transaction's reply carries anything to read. Some
     * firmwares (Joying's 2.23 com.syu.ms, as honda-customizer found) answer
     * cmd, register and unregister with a zero-length parcel: reading an
     * exception header from it would throw. Only a read answers with a body.
     */
    fun moduleReplyHasBody(tx: Int): Boolean = tx == TX_GET

    /** The ACC broadcasts the firmware sends every app (receivable by anyone; sending one needs a system app). */
    const val ACTION_ACC_ON = "com.fyt.boot.ACCON"
    const val ACTION_ACC_OFF = "com.fyt.boot.ACCOFF"

    /** The firmware's media command relay (stock players' next / previous), extras unverified. */
    const val ACTION_EXTERNAL_COMMAND = "com.syu.ms.action.external_command"

    /** Set to "2", the app binding the toolkit gets the steering wheel keys through module MAIN (open-headunit's CarFYTReceiver). */
    const val PROP_CARLINK_TYPE = "sys.carlink.type"
    const val CARLINK_TAKE_KEYS = "2"

    /** The firmware's own apps, for package visibility and the report. */
    val PACKAGES = setOf(PACKAGE, "com.syu.canbus", "com.syu.carlink", "com.syu.air", "com.syu.bt", "com.syu.radio", "com.android.launcher4", "com.android.launcher6")
}

/** The toolkit's module codes (getRemoteModule). */
object FytModules {
    const val MAIN = 0
    const val RADIO = 1
    const val BT = 2
    const val DVD = 3
    const val SOUND = 4
    const val IPOD = 5
    const val TV = 6
    const val CANBUS = 7
    const val TPMS = 8
    const val DVR = 9
    const val STEER = 10
    const val CUSTOMER = 11
    const val OBD = 12
    const val TEST = 13
    const val CAN_UPDATE = 14
    const val AMP = 15
    const val EMITTER = 16
    const val GSENSOR = 17

    /** A module's name, for logs and the finder. */
    fun name(code: Int): String = when (code) {
        MAIN -> "main"; RADIO -> "radio"; BT -> "bt"; DVD -> "dvd"; SOUND -> "sound"; IPOD -> "ipod"; TV -> "tv"
        CANBUS -> "canbus"; TPMS -> "tpms"; DVR -> "dvr"; STEER -> "steer"; CUSTOMER -> "customer"; OBD -> "obd"
        TEST -> "test"; CAN_UPDATE -> "canup"; AMP -> "amp"; EMITTER -> "emitter"; GSENSOR -> "gsensor"
        else -> "module$code"
    }
}

/** Module MAIN's update codes. */
object FytMain {
    /** A steering wheel or panel key: ints[0] is the key code (open-headunit); 576 is the voice key there. */
    const val U_KEY = 133
    const val KEY_VOICE = 576
}

/** Module BT's command and update codes (fyt-dialer, from the decompiled com.syu.bt). */
object FytBt {
    const val C_DIAL = 7
    const val C_REDIAL = 8
    const val C_PICKUP = 9
    const val C_HANG = 10
    /** strs[0] = the connected phone's name (or strs[0] = phone index "0"/"1" and strs[1] = name on dual-phone units). */
    const val U_PHONE_NAME = 7
    /** ints[0] = the HFP state: [STATE_CONNECTED] idle and connected, [STATE_RINGING] incoming. The others are unconfirmed. */
    const val U_PHONE_STATE = 9
    const val STATE_CONNECTED = 2
    const val STATE_RINGING = 4
}

/**
 * Module CANBUS's generic update codes (the 1000s, the same on every car;
 * FYTCanbusMonitor's CanbusIdCodes). Codes below 1000 are per car and per
 * CAN decoder (com.syu.canbus): speed may be 101 on one car and nothing on
 * another, which is what the Signal Finder is for.
 */
object FytCanbus {
    /** ints[0]: the CAN decoder's id, [decoderOf] the box type and [carOf] the car it was set up for. */
    const val U_CANBUS_ID = 1000
    const val U_EXIST_DOOR = 1004
    const val U_CANBUS_VER = 1005
    const val U_EXIST_AIR = 1007
    const val U_EXIST_TEMP_OUT = 1012
    /** Which camera the unit shows; believed to say reverse, unconfirmed. */
    const val U_CAMERA_MODE = 1013
    const val U_SHOW_BACKCAR_HOST = 1030
    /** ints[0]: the car's speed, unit unconfirmed (km/h expected). */
    const val U_CUR_SPEED = 1031
    /** ints[0]: engine revolutions per minute, unconfirmed. */
    const val U_ENGINE_SPEED = 1032
    const val U_GPS_ANGLE = 1033

    /** The generic codes a Dashwheel subscribes to on every FYT unit. */
    val GENERIC = intArrayOf(U_CANBUS_ID, U_EXIST_DOOR, U_CANBUS_VER, U_EXIST_AIR, U_EXIST_TEMP_OUT, U_CAMERA_MODE, U_CUR_SPEED, U_ENGINE_SPEED)

    /** The range the Signal Finder's sniffer subscribes to while it is open: every per-car code and the generic ones. */
    val SNIFF_RANGE = 0..1100

    /** The low 16 bits of the CAN bus id: the decoder box type. */
    fun decoderOf(canbusId: Int): Int = canbusId and 0xFFFF

    /** The high 16 bits of the CAN bus id: the car make / model it was set up for. */
    fun carOf(canbusId: Int): Int = (canbusId ushr 16) and 0xFFFF

    /** A plausible speed in km/h from an update's first int, or null. */
    fun speedKmh(ints: IntArray?): Float? = ints?.firstOrNull()?.takeIf { it in 0..400 }?.toFloat()

    /** A plausible engine speed from an update's first int, or null. */
    fun rpm(ints: IntArray?): Int? = ints?.firstOrNull()?.takeIf { it in 0..12_000 }
}

/**
 * How an FYT update is keyed for the Signal Finder: "fyt:<module>:<code>#<i>"
 * for the i-th int, "#f<i>" for a float and "#s<i>" for a string. One piece
 * each (the finder slices no bytes out of them).
 */
object FytKey {
    const val PREFIX = "fyt"

    data class Parsed(val module: Int, val code: Int, val kind: Char, val index: Int)

    fun int(module: Int, code: Int, index: Int) = "$PREFIX:$module:$code#$index"
    fun float(module: Int, code: Int, index: Int) = "$PREFIX:$module:$code#f$index"
    fun string(module: Int, code: Int, index: Int) = "$PREFIX:$module:$code#s$index"

    /** The parts of an FYT key, or null when [key] isn't one. */
    fun parse(key: String): Parsed? {
        val m = KEY.matchEntire(key) ?: return null
        val kind = m.groupValues[3].ifEmpty { "i" }[0]
        return Parsed(m.groupValues[1].toInt(), m.groupValues[2].toInt(), kind, m.groupValues[4].toInt())
    }

    /** Every finder key one update produces, with its value as text. */
    fun values(module: Int, code: Int, ints: IntArray?, flts: FloatArray?, strs: Array<String?>?): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ints?.forEachIndexed { i, v -> out[int(module, code, i)] = v.toString() }
        flts?.forEachIndexed { i, v -> out[float(module, code, i)] = v.toString() }
        strs?.forEachIndexed { i, v -> if (v != null) out[string(module, code, i)] = v }
        return out
    }

    private val KEY = Regex("^$PREFIX:(\\d+):(\\d+)#([fs]?)(\\d+)$")
}
