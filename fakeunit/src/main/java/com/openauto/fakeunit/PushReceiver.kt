package com.openauto.fakeunit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Pushes a value into the fake firmware from the command line, or relays a
 * broadcast with a byte array (which `am broadcast` cannot send itself):
 *
 *   adb shell am broadcast -a com.openauto.fakeunit.PUSH --ei module 7 --ei code 1031 --eia ints 57
 *   adb shell am broadcast -a com.openauto.fakeunit.PUSH --ei module 2 --ei code 9 --eia ints 4
 *   adb shell am broadcast -a com.openauto.fakeunit.PUSH --es action com.microntek.sync --es extra syncdata --es hex "00 39 0A ..."
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra("action")
        if (action != null) {
            val out = Intent(action)
            intent.getStringExtra("hex")?.let { hex ->
                val bytes = hex.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
                out.putExtra(intent.getStringExtra("extra") ?: "syncdata", bytes)
            }
            intent.getStringExtra("str")?.let { out.putExtra(intent.getStringExtra("strName") ?: "value", it) }
            if (intent.hasExtra("int")) out.putExtra(intent.getStringExtra("intName") ?: "value", intent.getIntExtra("int", 0))
            Log.i(TAG, "relaying $action ${out.extras?.keySet()}")
            context.sendBroadcast(out)
            return
        }
        val module = intent.getIntExtra("module", -1)
        val code = intent.getIntExtra("code", -1)
        if (module < 0 || code < 0) {
            Log.w(TAG, "PUSH needs --ei module and --ei code, or --es action")
            return
        }
        val ints = intent.getIntArrayExtra("ints")
        val flts = intent.getFloatArrayExtra("floats")
        val strs = intent.getStringArrayExtra("strs")?.map { it as String? }?.toTypedArray()
        Log.i(TAG, "push module $module code $code ints=${ints?.toList()} flts=${flts?.toList()} strs=${strs?.toList()}")
        FakeFirmware.push(module, code, ints, flts, strs)
    }

    companion object {
        private const val TAG = "FakePush"
    }
}
