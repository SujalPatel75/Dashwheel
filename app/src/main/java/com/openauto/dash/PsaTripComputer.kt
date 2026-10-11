package com.openauto.dash

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * The trip computer of a Peugeot, Citroën or DS, as its CAN box (RZC protocol)
 * tells the head unit: instant consumption, range, both trips and the car's
 * warning messages. The car app keeps these to its own screen (its shared data
 * stays empty for these cars), so they are read off the CAN box stream itself
 * ([McuFeed], cmd 65, or the root log through [McuReader]). Frames are
 * `FD LEN TYPE data… sum`: LEN counts itself, the type, the data and the sum;
 * the sum is the low byte of LEN + TYPE + data. Worked out from the firmware
 * (PeugeotDataParser), not yet seen on the car.
 */

/**
 * The CAN box's frames out of its stream, which comes in pieces that need not
 * end on a frame: `FD LEN TYPE data… sum`, a frame kept only when its sum is right.
 */
internal class RzcFrames {
    private val buffer = ByteArray(512)
    private var filled = 0

    /** Adds [bytes] from [from] on and hands every whole frame to [onFrame] (type, data). */
    fun push(bytes: ByteArray, from: Int, onFrame: (Int, ByteArray) -> Unit) {
        for (i in from until bytes.size) {
            if (filled == buffer.size) filled = 0
            buffer[filled++] = bytes[i]
        }
        var start = 0
        while (true) {
            // Resync on a frame head.
            while (start < filled && !isHead(buffer[start])) start++
            if (filled - start < 4) break
            val len = buffer[start + 1].toInt() and 0xFF
            // No frame of this box is longer: a stray head byte would hold the stream up.
            if (len < 3 || len > MAX_LEN) { start++; continue }
            if (filled - start < len + 1) break
            val frame = buffer.copyOfRange(start, start + len + 1)
            if (sumOk(frame)) {
                onFrame(frame[2].toInt() and 0xFF, frame.copyOfRange(3, frame.size - 1))
                start += frame.size
            } else {
                start++
            }
        }
        System.arraycopy(buffer, start, buffer, 0, filled - start)
        filled -= start
    }

    private fun isHead(b: Byte) = b == 0xFD.toByte() || b == 0xFC.toByte()

    companion object {
        private const val MAX_LEN = 64

        /** The low byte of LEN + TYPE + data equals the last byte. */
        fun sumOk(frame: ByteArray): Boolean {
            var sum = 0
            for (i in 1 until frame.size - 1) sum += frame[i].toInt() and 0xFF
            return (sum and 0xFF) == (frame.last().toInt() and 0xFF)
        }
    }
}

/** One trip of the trip computer; null where the car says "--". */
data class PsaTripPage(val avgL100: Float?, val avgKmh: Int?, val distanceKm: Int?)

/** The trip computer as the car last told it. */
data class PsaTrip(
    /** Instant consumption, L/100 km. */
    val instantL100: Float? = null,
    val rangeKm: Int? = null,
    val toDestinationKm: Int? = null,
    val trip1: PsaTripPage? = null,
    val trip2: PsaTripPage? = null,
    /** The car's warning codes now (0x3A), its "no message" codes left out. */
    val warnings: List<Int> = emptyList(),
    val at: Long = 0L
)

object PsaTripComputer {
    private const val TAG = "PsaTrip"

    internal const val TYPE_PAGE0 = 0x33
    internal const val TYPE_TRIP1 = 0x34
    internal const val TYPE_TRIP2 = 0x35
    internal const val TYPE_WARNINGS = 0x3A
    private const val TYPE_QUERY = 0x8F

    /** A reading older than this is not shown. */
    private const val FRESH_MS = 5 * 60_000L
    /** The pages are asked again this often while someone looks. */
    private const val QUERY_EVERY_MS = 30_000L

    private val _state = MutableStateFlow<PsaTrip?>(null)
    val state: StateFlow<PsaTrip?> = _state

    /** [state] while fresh. */
    fun fresh(): PsaTrip? = _state.value?.takeIf { SystemClock.elapsedRealtime() - it.at <= FRESH_MS }

    /** Whether the car in the profile has this trip computer: the frame types mean other things on other makes. */
    fun forThisCar(): Boolean = isPsa(CarProfileStore.current.make)

    internal fun isPsa(make: String): Boolean {
        val m = make.trim().lowercase()
        return m.startsWith("peugeot") || m.startsWith("citro") || m == "ds" || m.startsWith("ds ") || m.startsWith("ds automobiles")
    }

    private val frames = RzcFrames()

    /** Bytes of the CAN box stream from [from] on (an MCU message, cmd 65 first). */
    @Synchronized
    fun feed(bytes: ByteArray, from: Int) {
        if (!forThisCar()) return
        frames.push(bytes, from, ::onFrame)
    }

    private fun onFrame(type: Int, d: ByteArray) {
        val was = _state.value ?: PsaTrip()
        val next = decode(was, type, d) ?: return
        val now = SystemClock.elapsedRealtime()
        // Repeats of the same page only refresh its time.
        _state.value = next.copy(at = now)
        if (type == TYPE_PAGE0) next.rangeKm?.let { McuReader.carBoxWrite(null, it) }
        if (type == TYPE_WARNINGS) PsaWarnings.onCodes(next.warnings, was.warnings)
    }

    /** [was] with the page of [type] read from its data [d]; null for a frame that isn't one of these pages. */
    internal fun decode(was: PsaTrip, type: Int, d: ByteArray): PsaTrip? = when (type) {
        TYPE_PAGE0 -> if (d.size < 6) null else was.copy(
            instantL100 = u16(d, 0).takeIf { it <= 300 }?.let { it / 10f },
            rangeKm = u16(d, 2).takeIf { it in 1..2000 },
            toDestinationKm = u16(d, 4).takeIf { it <= 6000 }
        )
        TYPE_TRIP1, TYPE_TRIP2 -> if (d.size < 6) null else {
            val page = PsaTripPage(
                avgL100 = u16(d, 0).takeIf { it <= 300 }?.let { it / 10f },
                avgKmh = u16(d, 2).takeIf { it <= 250 },
                distanceKm = u16(d, 4).takeIf { it <= 9999 }
            )
            if (type == TYPE_TRIP1) was.copy(trip1 = page) else was.copy(trip2 = page)
        }
        TYPE_WARNINGS -> if (d.isEmpty()) null else {
            val n = (d[0].toInt() and 0xFF).coerceAtMost(d.size - 1)
            was.copy(warnings = (1..n).map { d[it].toInt() and 0xFF }.filter { it != 0x00 && it != 0xF0 && it != 0xFF }.distinct())
        }
        else -> null
    }

    private fun u16(d: ByteArray, i: Int) = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)

    // --- Asking the CAN box ----------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchers = 0
    private var poll: Job? = null

    /**
     * Someone shows the trip computer: the stream is read, and the pages are
     * asked for now and every [QUERY_EVERY_MS], as the car app does when its
     * own trip screen opens. [unwatch] when done.
     */
    @Synchronized
    fun watch(context: Context) {
        if (watchers++ > 0) return
        McuReader.start()
        val app = context.applicationContext
        poll = scope.launch {
            while (isActive) {
                if (forThisCar() && !DemoMode.isOn) CanBoxLink.ask(app, listOf(TYPE_PAGE0, TYPE_TRIP1, TYPE_TRIP2, TYPE_WARNINGS).map { queryFrame(it) })
                delay(QUERY_EVERY_MS)
            }
        }
    }

    @Synchronized
    fun unwatch() {
        if (watchers == 0 || --watchers > 0) return
        poll?.cancel()
        poll = null
        McuReader.stop()
    }

    /** The car app's own question for one page: `FD 04 8F type sum` (the service puts the sum in). */
    internal fun queryFrame(type: Int) = byteArrayOf(0xFD.toByte(), 0x04, TYPE_QUERY.toByte(), type.toByte(), 0)

    internal fun log(msg: String, e: Throwable? = null) = Log.w(TAG, msg, e)
}

/*
 * The car app's CAN service (com.qf.vehicle, exported, no permission): one call,
 * "write this frame to the CAN box" (ICanBusServiceFeature tx 25,
 * doCheckSumAndWriteData2Uart4CanBox), which works the sum out itself. Bound
 * while there is something to send, let go a minute after.
 */
internal object CanBoxLink {
    private const val PACKAGE = "com.qf.vehicle"
    private const val ACTION = "com.qf.vehicle.service.ACTION_CAN_SERVICE"
    private const val DESCRIPTOR = "com.qf.vehicle.service.ICanBusServiceFeature"
    private const val TX_WRITE = 25
    private const val LINGER_MS = 60_000L

    private var binder: IBinder? = null
    private var bound = false
    private var pending: List<ByteArray> = emptyList()
    private var unbindAt = 0L
    private var appContext: Context? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val frames = synchronized(this@CanBoxLink) {
                binder = service
                pending.also { pending = emptyList() }
            }
            if (frames.isNotEmpty()) Thread { write(frames) }.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(this@CanBoxLink) { binder = null }
        }
    }

    /** Writes [frames] to the CAN box, binding the service first when needed. */
    fun ask(context: Context, frames: List<ByteArray>) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        val b = synchronized(this) {
            appContext = app
            unbindAt = now + LINGER_MS
            if (binder == null) {
                pending = frames
                if (!bound) {
                    bound = runCatching {
                        app.bindService(Intent(ACTION).setPackage(PACKAGE), connection, Context.BIND_AUTO_CREATE)
                    }.onFailure { PsaTripComputer.log("no CAN service", it) }.getOrDefault(false)
                }
                null
            } else binder
        }
        if (b != null) write(frames)
        // Let go of the service once nobody asked for a while.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ releaseIfIdle() }, LINGER_MS + 1_000L)
    }

    private fun write(frames: List<ByteArray>) {
        val b = synchronized(this) { binder } ?: return
        for (f in frames) {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeByteArray(f)
                b.transact(TX_WRITE, data, reply, 0)
                reply.readException()
            } catch (e: Exception) {
                PsaTripComputer.log("CAN box write failed", e)
                return
            } finally {
                data.recycle()
                reply.recycle()
            }
            // The car app leaves no gap either, but the box reads a frame at a time.
            Thread.sleep(40)
        }
    }

    @Synchronized
    private fun releaseIfIdle() {
        if (!bound || SystemClock.elapsedRealtime() < unbindAt) return
        runCatching { appContext?.unbindService(connection) }
        bound = false
        binder = null
    }
}

/*
 * The car's own warning messages (0x3A), the ones worth a line on screen
 * and, for the serious ones, a word out loud when car warnings are spoken.
 * Codes are the CAN box's (PeugeotDataParser.getWarningString); a code not
 * listed shows as its number.
 */
internal object PsaWarnings {
    enum class Level { STOP, SERVICE, INFO }

    class Warning(val textRes: Int, val level: Level)

    private val known: Map<Int, Warning> = mapOf(
        0x0B to Warning(R.string.car_warn_engine_temp, Level.STOP),
        0x0E to Warning(R.string.car_warn_oil_pressure, Level.STOP),
        0x0F to Warning(R.string.car_warn_brakes, Level.STOP),
        0x1A to Warning(R.string.car_warn_brakes, Level.STOP),
        0x76 to Warning(R.string.car_warn_engine_stop, Level.STOP),
        0x0C to Warning(R.string.car_warn_coolant, Level.SERVICE),
        0x0D to Warning(R.string.car_warn_oil_level, Level.SERVICE),
        0x13 to Warning(R.string.car_warn_dpf, Level.SERVICE),
        0x16 to Warning(R.string.car_warn_steering, Level.SERVICE),
        0x1E to Warning(R.string.car_warn_abs, Level.SERVICE),
        0x1F to Warning(R.string.car_warn_esp, Level.SERVICE),
        0x21 to Warning(R.string.car_warn_gearbox, Level.SERVICE),
        0x23 to Warning(R.string.car_warn_additive, Level.SERVICE),
        0x27 to Warning(R.string.car_warn_battery, Level.SERVICE),
        0x5A to Warning(R.string.car_warn_airbag, Level.SERVICE),
        0x5B to Warning(R.string.car_warn_water_diesel, Level.SERVICE),
        0x77 to Warning(R.string.car_warn_engine_repair, Level.SERVICE),
        0x85 to Warning(R.string.car_warn_pollution, Level.SERVICE),
        0x11 to Warning(R.string.car_warn_open, Level.INFO),
        0x28 to Warning(R.string.car_warn_tyres, Level.INFO),
        0x39 to Warning(R.string.car_warn_tyres, Level.INFO),
        0x33 to Warning(R.string.car_warn_washer, Level.INFO),
        0x34 to Warning(R.string.car_warn_fuel, Level.INFO),
        0x31 to Warning(R.string.car_warn_ice, Level.INFO)
    )

    fun of(code: Int): Warning? = known[code]

    @Volatile private var appContext: Context? = null

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * New codes since [before]: the serious ones are said, once while they
     * last. Low fuel, ice and an open door have their own spoken alerts.
     */
    fun onCodes(now: List<Int>, before: List<Int>) {
        val context = appContext ?: return
        if (DemoMode.isOn) return
        for (code in now - before.toSet()) {
            val w = known[code] ?: continue
            if (w.level == Level.INFO) continue
            if (!SpokenEvents.isOn(context, SpokenEvent.CAR_TIPS)) return
            val res = AppLanguage.wrap(context).resources
            CarVoice.announce(res.getString(R.string.car_say_car_warning, res.getString(w.textRes)), InAppNav.locale(context), urgent = w.level == Level.STOP)
        }
    }
}
