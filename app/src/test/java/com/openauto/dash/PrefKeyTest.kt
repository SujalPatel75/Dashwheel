package com.openauto.dash

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An in-memory SharedPreferences: what an existing install has on disk, for the JVM. */
private class FakePrefs : SharedPreferences {
    val values = HashMap<String, Any?>()
    var applies = 0

    override fun getAll(): MutableMap<String, *> = HashMap(values)
    override fun getString(key: String, defValue: String?) = if (key in values) values[key] as String? else defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        if (key in values) values[key] as MutableSet<String>? else defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class Editor : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { if (value == null) removed += key else pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            removed.forEach { values.remove(it) }
            values.putAll(pending)
            return true
        }
        override fun apply() {
            applies++
            commit()
        }
    }
}

/** A Context whose settings files live in memory, by file name. */
private class PrefsContext : ContextWrapper(null) {
    val files = HashMap<String, FakePrefs>()
    fun file(name: String) = files.getOrPut(name) { FakePrefs() }
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        assertEquals(Context.MODE_PRIVATE, mode)
        return file(name)
    }
}

class PrefKeyTest {
    @Test
    fun eachKindReadsItsDefaultThenWhatWasWritten() {
        val context = PrefsContext()
        val file = PrefFile("test")
        val flag = file.bool("flag", default = true)
        val count = file.int("count", default = 3)
        val at = file.long("at", default = 7L)
        val gain = file.float("gain", default = 0.5f)
        val name = file.text("name")
        assertTrue(file[context, flag])
        assertEquals(3, file[context, count])
        assertEquals(7L, file[context, at])
        assertEquals(0.5f, file[context, gain])
        assertNull(file[context, name])

        file[context, flag] = false
        file[context, count] = 9
        file[context, at] = 1_000L
        file[context, gain] = 1.5f
        file[context, name] = "C4"
        assertFalse(file[context, flag])
        assertEquals(9, file[context, count])
        assertEquals(1_000L, file[context, at])
        assertEquals(1.5f, file[context, gain])
        assertEquals("C4", file[context, name])
        // Stored under the plain names and types, as the old code wrote them.
        assertEquals(mapOf("flag" to false, "count" to 9, "at" to 1_000L, "gain" to 1.5f, "name" to "C4"), context.file("test").values)

        // Null clears a text.
        file[context, name] = null
        assertFalse("name" in context.file("test").values)
    }

    @Test
    fun editWritesSeveralValuesInOneApply() {
        val context = PrefsContext()
        val file = PrefFile("test")
        val a = file.bool("a", false)
        val b = file.int("b", 0)
        file.edit(context) { put(a, true).put(b, 2) }
        assertEquals(1, context.file("test").applies)
        assertTrue(file[context, a])
        assertEquals(2, file[context, b])
    }

    // --- The stores moved to typed keys still read what existing installs saved -----------------

    @Test
    fun driveLockReadsTheSavedSetting() {
        val context = PrefsContext()
        assertTrue("on by default", DriveLockStore.load(context))
        context.file("drive_lock").values["enabled"] = false
        assertFalse(DriveLockStore.load(context))
        DriveLockStore.save(context, true)
        assertEquals(true, context.file("drive_lock").values["enabled"])
    }

    @Test
    fun setupReadsTheSavedSettings() {
        val context = PrefsContext()
        assertFalse(SetupStore.isDone(context))
        assertFalse(SetupStore.pillOff(context))
        assertFalse(SetupStore.noObd(context))
        context.file("setup").values.putAll(mapOf("done" to true, "pill_off" to true, "no_obd" to true))
        assertTrue(SetupStore.isDone(context))
        assertTrue(SetupStore.pillOff(context))
        assertTrue(SetupStore.noObd(context))

        val fresh = PrefsContext()
        SetupStore.markDone(fresh)
        SetupStore.setPillOff(fresh, true)
        SetupStore.setNoObd(fresh, false)
        assertEquals(mapOf("done" to true, "pill_off" to true, "no_obd" to false), fresh.file("setup").values)
    }

    @Test
    fun tourReadsTheSavedSettings() {
        val context = PrefsContext()
        assertFalse(TourStore.isPending(context))
        assertTrue("read aloud by default", TourStore.readAloud(context))
        context.file("tour").values.putAll(mapOf("pending" to true, "speak" to false))
        assertTrue(TourStore.isPending(context))
        assertFalse(TourStore.readAloud(context))
        TourStore.setReadAloud(context, true)
        assertEquals(true, context.file("tour").values["speak"])
    }

    @Test
    fun theTourIsOfferedOnceAndNotNowTwiceCountsAsSeen() {
        val context = PrefsContext()
        TourStore.offer(context)
        assertTrue(TourStore.isPending(context))
        TourStore.notNow(context)
        assertEquals(1, context.file("tour").values["declined"])
        assertTrue("still pending after one Not now", TourStore.isPending(context))
        TourStore.notNow(context)
        assertFalse(TourStore.isPending(context))
        assertEquals(true, context.file("tour").values["seen"])
        // Seen: not offered again.
        TourStore.offer(context)
        assertFalse(TourStore.isPending(context))
    }

    @Test
    fun aTourSeenByAnOlderBuildIsNotOfferedAgain() {
        val context = PrefsContext()
        context.file("tour").values["seen"] = true
        TourStore.offer(context)
        assertFalse(TourStore.isPending(context))
    }
}
