package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReaderPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val fontSizeSp: Float = 15f,
    val continuousPdfScroll: Boolean = true,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = true
)

class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("universal_reader_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<ReaderPreferences> = _preferences.asStateFlow()

    private fun loadPreferences(): ReaderPreferences {
        val themeStr = prefs.getString(KEY_THEME, AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val theme = try {
            AppThemeMode.valueOf(themeStr)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
        val fontSize = prefs.getFloat(KEY_FONT_SIZE, 15f)
        val continuousPdf = prefs.getBoolean(KEY_CONTINUOUS_PDF, true)
        val lineNumbers = prefs.getBoolean(KEY_LINE_NUMBERS, true)
        val wordWrap = prefs.getBoolean(KEY_WORD_WRAP, true)

        return ReaderPreferences(
            themeMode = theme,
            fontSizeSp = fontSize,
            continuousPdfScroll = continuousPdf,
            showLineNumbers = lineNumbers,
            wordWrap = wordWrap
        )
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    fun setFontSize(sizeSp: Float) {
        prefs.edit().putFloat(KEY_FONT_SIZE, sizeSp).apply()
        _preferences.value = _preferences.value.copy(fontSizeSp = sizeSp)
    }

    fun setContinuousPdfScroll(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTINUOUS_PDF, enabled).apply()
        _preferences.value = _preferences.value.copy(continuousPdfScroll = enabled)
    }

    fun setShowLineNumbers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LINE_NUMBERS, enabled).apply()
        _preferences.value = _preferences.value.copy(showLineNumbers = enabled)
    }

    fun setWordWrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORD_WRAP, enabled).apply()
        _preferences.value = _preferences.value.copy(wordWrap = enabled)
    }

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_CONTINUOUS_PDF = "continuous_pdf"
        private const val KEY_LINE_NUMBERS = "line_numbers"
        private const val KEY_WORD_WRAP = "word_wrap"
    }
}
