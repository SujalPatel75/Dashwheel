package com.openauto.dash

import android.content.Intent
import android.os.Bundle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The helper's hand-off to Dashwheel across the activity manager's releases (SystemDisplayHelper). */
class SystemDisplayHelperTest {
    // Stand-ins for the hidden IApplicationThread and IIntentReceiver: any class but the ones filled.
    private class Caller
    private class Receiver

    private val int = Int::class.javaPrimitiveType!!
    private val bool = Boolean::class.javaPrimitiveType!!
    private val str = String::class.java
    private val strings = Array<String>::class.java
    private val intent = Intent::class.java
    private val bundle = Bundle::class.java

    /** Android 10: caller, intent, resolvedType, resultTo, resultCode, resultData, map, requiredPermissions, appOp, options, serialized, sticky, userId. */
    private val android10 = listOf(Caller::class.java, intent, str, Receiver::class.java, int, str, bundle, strings, int, bundle, bool, bool, int)

    /** Android 11: a calling feature id after the caller. */
    private val android11 = listOf(Caller::class.java, str, intent, str, Receiver::class.java, int, str, bundle, strings, int, bundle, bool, bool, int)

    /** Android 12: excluded permissions after the required ones. */
    private val android12 = listOf(Caller::class.java, str, intent, str, Receiver::class.java, int, str, bundle, strings, strings, int, bundle, bool, bool, int)

    /** Android 13 to 16: excluded packages as well. */
    private val android13 = listOf(Caller::class.java, str, intent, str, Receiver::class.java, int, str, bundle, strings, strings, strings, int, bundle, bool, bool, int)

    private val sent = Any()

    @Test
    fun theNewerCallIsPreferredWhenTheReleaseHasIt() {
        assertEquals("broadcastIntent", SystemDisplayHelper.broadcastMethod(listOf("startActivity", "broadcastIntent")))
        assertEquals("broadcastIntentWithFeature", SystemDisplayHelper.broadcastMethod(listOf("broadcastIntent", "broadcastIntentWithFeature")))
    }

    @Test
    fun android10ArgumentsAreTheOnesSentSinceTheStart() {
        assertArrayEquals(
            arrayOf(null, sent, null, null, -1, null, null, null, -1, null, true, false, 0),
            SystemDisplayHelper.broadcastArguments(android10, sent, userId = 0)
        )
    }

    @Test
    fun android11TakesANullFeatureIdAfterTheCaller() {
        assertArrayEquals(
            arrayOf(null, null, sent, null, null, -1, null, null, null, -1, null, true, false, 0),
            SystemDisplayHelper.broadcastArguments(android11, sent, userId = 0)
        )
    }

    @Test
    fun android12AndroidExcludesNoPermissions() {
        assertArrayEquals(
            arrayOf(null, null, sent, null, null, -1, null, null, null, null, -1, null, true, false, 0),
            SystemDisplayHelper.broadcastArguments(android12, sent, userId = 0)
        )
    }

    @Test
    fun android13ExcludesNoPackagesEither() {
        assertArrayEquals(
            arrayOf(null, null, sent, null, null, -1, null, null, null, null, null, -1, null, true, false, 0),
            SystemDisplayHelper.broadcastArguments(android13, sent, userId = 0)
        )
    }

    @Test
    fun theUserIdIsTheLastIntWhateverTheRelease() {
        for (types in listOf(android10, android11, android12, android13)) {
            val args = SystemDisplayHelper.broadcastArguments(types, sent, userId = 10)
            assertEquals(10, args.last())
            assertEquals(types.size, args.size)
        }
    }

    @Test
    fun theSystemDisplaysAreTrustedFromAndroid11() {
        val flags = SystemDisplays.FLAGS
        assertEquals(flags, SystemDisplayHelper.systemFlags(29, flags))
        assertEquals(flags or (1 shl 10), SystemDisplayHelper.systemFlags(30, flags))
        assertEquals(flags or (1 shl 10), SystemDisplayHelper.systemFlags(35, flags))
    }
}
