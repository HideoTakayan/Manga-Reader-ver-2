package com.example.manga_readerver2.core.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

/**
 * Một hệ thống Preference tối giản nhưng mạnh mẽ chuẩn Mihon.
 * Hỗ trợ Flow để cập nhật UI ngay lập tức khi cài đặt thay đổi.
 */
class PreferenceStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("manga_reader_prefs", Context.MODE_PRIVATE)

    fun getString(key: String, defaultValue: String): Preference<String> {
        return Preference(prefs, key, defaultValue,
            { k, d -> prefs.getString(k, d) ?: d },
            { k, v -> prefs.edit().putString(k, v).apply() }
        )
    }

    fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return Preference(prefs, key, defaultValue, 
            { k, d -> prefs.getStringSet(k, d) ?: d },
            { k, v -> prefs.edit().putStringSet(k, v).apply() }
        )
    }

    fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return Preference(prefs, key, defaultValue,
            { k, d -> prefs.getInt(k, d) },
            { k, v -> prefs.edit().putInt(k, v).apply() }
        )
    }

    fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return Preference(prefs, key, defaultValue,
            { k, d -> prefs.getLong(k, d) },
            { k, v -> prefs.edit().putLong(k, v).apply() }
        )
    }

    fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return Preference(prefs, key, defaultValue,
            { k, d -> prefs.getBoolean(k, d) },
            { k, v -> prefs.edit().putBoolean(k, v).apply() }
        )
    }

    fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return Preference(prefs, key, defaultValue,
            { k, d -> prefs.getFloat(k, d) },
            { k, v -> prefs.edit().putFloat(k, v).apply() }
        )
    }
}

class Preference<T>(
    private val prefs: SharedPreferences,
    private val key: String,
    private val defaultValue: T,
    private val getter: (String, T) -> T,
    private val setter: (String, T) -> Unit
) {
    fun get(): T = getter(key, defaultValue)

    fun set(value: T) = setter(key, value)

    fun asFlow(): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) trySend(get())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart { emit(get()) }
}
