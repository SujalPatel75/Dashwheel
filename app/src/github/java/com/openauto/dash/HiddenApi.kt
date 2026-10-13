package com.openauto.dash

import java.lang.reflect.Method
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The @hide calls the GitHub edition makes (windowing mode, the tile's input,
 * Wi-Fi and Bluetooth joins), through HiddenApiBypass. The Play edition has
 * its own HiddenApi that refuses them, so the library isn't in its bundle:
 * Google Play rejects API-bypass SDKs. Every caller already has a fallback.
 */
object HiddenApi {
    fun invoke(cls: Class<*>, target: Any?, name: String, vararg args: Any?): Any? =
        HiddenApiBypass.invoke(cls, target, name, *args)

    fun getDeclaredMethod(cls: Class<*>, name: String, vararg types: Class<*>?): Method =
        HiddenApiBypass.getDeclaredMethod(cls, name, *types)
}
