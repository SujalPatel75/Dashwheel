package com.openauto.dash

import android.content.Context
import android.content.SharedPreferences

/**
 * A typed settings key: its name in the settings file and the value read when
 * nothing is stored. Declared once per store, so a key is never misspelled or
 * read with another type or default than it is written with.
 *
 * The names and files are the stored ones: changing either loses what
 * existing installs saved.
 */
internal sealed class PrefKey<T>(val name: String, val default: T) {
    abstract fun read(prefs: SharedPreferences): T
    abstract fun write(editor: SharedPreferences.Editor, value: T)

    class Bool(name: String, default: Boolean) : PrefKey<Boolean>(name, default) {
        override fun read(prefs: SharedPreferences) = prefs.getBoolean(name, default)
        override fun write(editor: SharedPreferences.Editor, value: Boolean) {
            editor.putBoolean(name, value)
        }
    }

    class IntKey(name: String, default: Int) : PrefKey<Int>(name, default) {
        override fun read(prefs: SharedPreferences) = prefs.getInt(name, default)
        override fun write(editor: SharedPreferences.Editor, value: Int) {
            editor.putInt(name, value)
        }
    }

    class LongKey(name: String, default: Long) : PrefKey<Long>(name, default) {
        override fun read(prefs: SharedPreferences) = prefs.getLong(name, default)
        override fun write(editor: SharedPreferences.Editor, value: Long) {
            editor.putLong(name, value)
        }
    }

    class FloatKey(name: String, default: Float) : PrefKey<Float>(name, default) {
        override fun read(prefs: SharedPreferences) = prefs.getFloat(name, default)
        override fun write(editor: SharedPreferences.Editor, value: Float) {
            editor.putFloat(name, value)
        }
    }

    /** A text; null stored or read back as "not set" (putString(name, null) removes it). */
    class Text(name: String, default: String?) : PrefKey<String?>(name, default) {
        override fun read(prefs: SharedPreferences): String? = prefs.getString(name, default)
        override fun write(editor: SharedPreferences.Editor, value: String?) {
            editor.putString(name, value)
        }
    }
}

internal operator fun <T> SharedPreferences.get(key: PrefKey<T>): T = key.read(this)

internal fun <T> SharedPreferences.Editor.put(key: PrefKey<T>, value: T): SharedPreferences.Editor =
    apply { key.write(this, value) }

/**
 * One settings file (a SharedPreferences name) and its typed keys:
 *
 *     private val file = PrefFile("drive_lock")
 *     private val ENABLED = file.bool("enabled", default = true)
 *     fun load(context: Context) = file[context, ENABLED]
 *     fun save(context: Context, on: Boolean) = file.set(context, ENABLED, on)
 */
internal class PrefFile(val fileName: String) {
    fun open(context: Context): SharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    fun bool(name: String, default: Boolean) = PrefKey.Bool(name, default)
    fun int(name: String, default: Int) = PrefKey.IntKey(name, default)
    fun long(name: String, default: Long) = PrefKey.LongKey(name, default)
    fun float(name: String, default: Float) = PrefKey.FloatKey(name, default)
    fun text(name: String, default: String? = null) = PrefKey.Text(name, default)

    operator fun <T> get(context: Context, key: PrefKey<T>): T = open(context)[key]

    /** Writes one value (apply(): in memory at once, on disk in the background). */
    operator fun <T> set(context: Context, key: PrefKey<T>, value: T) {
        open(context).edit().put(key, value).apply()
    }

    /** Several writes in one apply(). */
    fun edit(context: Context, block: SharedPreferences.Editor.() -> Unit) {
        open(context).edit().apply(block).apply()
    }
}
