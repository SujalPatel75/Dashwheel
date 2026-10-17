package com.openauto.dash.unit.mtc

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import com.openauto.unitproto.mtc.MtcProtocol
import java.lang.reflect.Method

/**
 * The Microntek framework's `android.microntek.CarManager`, reached by
 * reflection (the class is in the ROM's framework, never in the SDK): keys
 * arrive as messages on a handler [attach]ed for a type ("KeyDown"), state is
 * read by key name. Null on a unit whose framework has no such class.
 */
internal class MtcCarManager private constructor(private val manager: Any, private val cls: Class<*>) {
    private val attach: Method? = cls.methods.firstOrNull { it.name == "attach" && it.parameterTypes.size == 2 }
    private val detach: Method? = cls.methods.firstOrNull { it.name == "detach" && it.parameterTypes.isEmpty() }
    private val getInt: Method? = cls.methods.firstOrNull { it.name == "getIntState" && it.parameterTypes.size == 1 }
    private val getString: Method? = cls.methods.firstOrNull { it.name == "getStringState" && it.parameterTypes.size == 1 }

    private val thread by lazy { HandlerThread("mtc-car").apply { start() } }

    /**
     * Listens for the unit's keys: each press's code goes to [onKey], on the
     * manager's own thread. True when the framework took the listener.
     */
    fun listenKeys(onKey: (Int) -> Unit): Boolean {
        val attach = attach ?: return false
        val handler = Handler(thread.looper) { msg: Message ->
            val bundle: Bundle? = msg.data
            if (msg.obj == MtcProtocol.ATTACH_KEY_DOWN || bundle?.getString(MtcProtocol.BUNDLE_TYPE) == MtcProtocol.TYPE_KEY) {
                val code = bundle?.getInt(MtcProtocol.BUNDLE_VALUE, -1) ?: -1
                if (code >= 0) onKey(code)
            }
            true
        }
        return runCatching { attach.invoke(manager, handler, MtcProtocol.ATTACH_KEY_DOWN); true }
            .onFailure { Log.w(TAG, "CarManager.attach failed", it) }
            .getOrDefault(false)
    }

    fun stop() {
        runCatching { detach?.invoke(manager) }
    }

    fun intState(key: String): Int? = runCatching { getInt?.invoke(manager, key) as? Int }.getOrNull()

    fun stringState(key: String): String? = runCatching { getString?.invoke(manager, key) as? String }.getOrNull()

    companion object {
        private const val TAG = "MtcCarManager"

        /** A manager over the ROM's class, or null where there is none (or it can't be made). */
        fun open(): MtcCarManager? = runCatching {
            val cls = Class.forName(MtcProtocol.CAR_MANAGER_CLASS)
            MtcCarManager(cls.getDeclaredConstructor().newInstance(), cls)
        }.onFailure { Log.i(TAG, "no ${MtcProtocol.CAR_MANAGER_CLASS}: ${it.javaClass.simpleName}") }.getOrNull()
    }
}
