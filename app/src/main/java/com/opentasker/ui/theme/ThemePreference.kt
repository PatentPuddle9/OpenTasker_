package com.opentasker.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    System,
    Dark,
    Amoled,
    Light,
    HighContrast,

    /** Material You. Requires API 31; below that it renders as [System]. */
    Dynamic;

    companion object {
        fun fromString(value: String?): ThemeMode = when (value) {
            "system" -> System
            "dark" -> Dark
            "amoled" -> Amoled
            "light" -> Light
            "high_contrast" -> HighContrast
            "dynamic" -> Dynamic
            else -> Amoled
        }
    }

    fun toStorageString(): String = when (this) {
        System -> "system"
        Dark -> "dark"
        Amoled -> "amoled"
        Light -> "light"
        HighContrast -> "high_contrast"
        Dynamic -> "dynamic"
    }
}

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

object ThemePreference {
    const val DEFAULT_ACCENT_HEX = "#B69CFF"
    private val KEY = stringPreferencesKey("theme_mode")
    private val ACCENT_KEY = stringPreferencesKey("accent_color_hex")

    fun observe(context: Context): Flow<ThemeMode> =
        context.themeDataStore.data.map { prefs ->
            ThemeMode.fromString(prefs[KEY])
        }

    fun observeAccentColor(context: Context): Flow<String> =
        context.themeDataStore.data.map { prefs -> prefs[ACCENT_KEY] ?: DEFAULT_ACCENT_HEX }

    suspend fun setAccentColor(context: Context, hex: String) {
        val normalized = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        require(Regex("#[0-9A-Fa-f]{6}").matches(normalized)) { "Accent must be a six-digit HEX color" }
        context.themeDataStore.edit { prefs -> prefs[ACCENT_KEY] = normalized.uppercase() }
    }

    suspend fun set(context: Context, mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY] = mode.toStorageString()
        }
    }
}
