package com.openauto.fakeunit

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.openauto.unitproto.fyt.FytBt
import com.openauto.unitproto.fyt.FytModules
import com.openauto.unitproto.fyt.FytProtocol
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The FYT toolkit and its modules as plain Binders, speaking the real
 * descriptors and transaction codes ([FytProtocol]). Each module keeps the
 * last value of every update code and the callbacks registered for it;
 * [push] hands a new value to them the way the firmware does (one way).
 *
 * The real firmware's quirk is kept: cmd, register and unregister answer
 * with an empty parcel ([EMPTY_REPLIES]), so the launcher's reading of those
 * replies is exercised too.
 */
object FakeFirmware {
    private const val TAG = "FakeFirmware"

    /** Answer cmd / register / unregister with nothing, as Joying's com.syu.ms does. */
    @Volatile var EMPTY_REPLIES = true

    private val modules = ConcurrentHashMap<Int, FakeModule>()

    fun module(code: Int): FakeModule = modules.getOrPut(code) { FakeModule(code) }

    val toolkit: Binder = object : Binder() {
        init {
            attachInterface(null, FytProtocol.DESCRIPTOR_TOOLKIT)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != FytProtocol.TX_GET_MODULE) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(FytProtocol.DESCRIPTOR_TOOLKIT)
            val moduleCode = data.readInt()
            Log.i(TAG, "module ${FytModules.name(moduleCode)} asked for")
            reply?.writeNoException()
            reply?.writeStrongBinder(module(moduleCode))
            return true
        }
    }

    /** Pushes [code]'s new value on module [module] to every callback registered for it, and keeps it as current. */
    fun push(module: Int, code: Int, ints: IntArray? = null, flts: FloatArray? = null, strs: Array<String?>? = null) {
        module(module).push(code, ints, flts, strs)
    }

    class FakeModule(val code: Int) : Binder() {
        private val current = ConcurrentHashMap<Int, Update>()
        private val callbacks = ConcurrentHashMap<Int, CopyOnWriteArraySet<IBinder>>()

        data class Update(val ints: IntArray?, val flts: FloatArray?, val strs: Array<String?>?)

        init {
            attachInterface(null, FytProtocol.DESCRIPTOR_MODULE)
        }

        override fun onTransact(tx: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (tx) {
                FytProtocol.TX_CMD -> {
                    data.enforceInterface(FytProtocol.DESCRIPTOR_MODULE)
                    val cmd = data.readInt()
                    val ints = data.createIntArray()
                    val flts = data.createFloatArray()
                    val strs = data.createStringArray()
                    Log.i(TAG, "${FytModules.name(code)} cmd $cmd ints=${ints?.toList()} flts=${flts?.toList()} strs=${strs?.toList()}")
                    onCmd(cmd)
                    if (!EMPTY_REPLIES) reply?.writeNoException()
                }
                FytProtocol.TX_GET -> {
                    data.enforceInterface(FytProtocol.DESCRIPTOR_MODULE)
                    data.readInt()
                    // A null ModuleObject: the launcher doesn't read values this way.
                    reply?.writeNoException()
                    reply?.writeInt(0)
                }
                FytProtocol.TX_REGISTER -> {
                    data.enforceInterface(FytProtocol.DESCRIPTOR_MODULE)
                    val cb = data.readStrongBinder()
                    val update = data.readInt()
                    val flag = data.readInt()
                    if (cb != null) {
                        callbacks.getOrPut(update) { CopyOnWriteArraySet() }.add(cb)
                        if (flag == FytProtocol.REGISTER_WITH_VALUE) current[update]?.let { send(cb, update, it) }
                    }
                    if (!EMPTY_REPLIES) reply?.writeNoException()
                }
                FytProtocol.TX_UNREGISTER -> {
                    data.enforceInterface(FytProtocol.DESCRIPTOR_MODULE)
                    val cb = data.readStrongBinder()
                    val update = data.readInt()
                    if (cb != null) callbacks[update]?.remove(cb)
                    if (!EMPTY_REPLIES) reply?.writeNoException()
                }
                else -> return super.onTransact(tx, data, reply, flags)
            }
            return true
        }

        /** What a command does to the scripted car: the phone's commands move its call along. */
        private fun onCmd(cmd: Int) {
            if (code != FytModules.BT) return
            when (cmd) {
                // Answered: a state that is neither idle nor ringing (the real code is unconfirmed).
                FytBt.C_PICKUP -> push(FytBt.U_PHONE_STATE, intArrayOf(STATE_TALKING), null, null)
                FytBt.C_HANG -> push(FytBt.U_PHONE_STATE, intArrayOf(FytBt.STATE_CONNECTED), null, null)
            }
        }

        fun push(update: Int, ints: IntArray?, flts: FloatArray?, strs: Array<String?>?) {
            val u = Update(ints, flts, strs)
            current[update] = u
            callbacks[update]?.forEach { send(it, update, u) }
        }

        private fun send(cb: IBinder, update: Int, u: Update) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(FytProtocol.DESCRIPTOR_CALLBACK)
                data.writeInt(update)
                data.writeIntArray(u.ints)
                data.writeFloatArray(u.flts)
                data.writeStringArray(u.strs)
                cb.transact(FytProtocol.TX_UPDATE, data, null, IBinder.FLAG_ONEWAY)
            } catch (e: Exception) {
                Log.w(TAG, "callback gone", e)
                callbacks[update]?.remove(cb)
            } finally {
                data.recycle()
            }
        }

        companion object {
            /** A made-up "in a call" state for the fake phone. */
            const val STATE_TALKING = 5
        }
    }
}
