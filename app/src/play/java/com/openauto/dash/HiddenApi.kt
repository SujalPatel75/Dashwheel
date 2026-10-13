package com.openauto.dash

import java.lang.reflect.Method

/**
 * Play edition: no @hide calls. Google Play refuses API-bypass SDKs
 * (hiddenapibypass), so every call here fails and its caller takes the
 * public way, or goes without, as it does on a unit that refuses the call.
 */
object HiddenApi {
    fun invoke(cls: Class<*>, target: Any?, name: String, vararg args: Any?): Any? =
        throw UnsupportedOperationException(NOT_HERE)

    fun getDeclaredMethod(cls: Class<*>, name: String, vararg types: Class<*>?): Method =
        throw NoSuchMethodException(NOT_HERE)

    private const val NOT_HERE = "no hidden API calls in the Play edition"
}
