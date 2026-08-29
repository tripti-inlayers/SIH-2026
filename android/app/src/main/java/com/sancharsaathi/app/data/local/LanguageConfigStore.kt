package com.sancharsaathi.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी")
}

class LanguageConfigStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("language_config_prefs", Context.MODE_PRIVATE)

    private val _currentLanguageFlow = MutableStateFlow(currentLanguage)
    val currentLanguageFlow: StateFlow<AppLanguage> = _currentLanguageFlow.asStateFlow()

    var currentLanguage: AppLanguage
        get() {
            val code = prefs.getString(KEY_LANG, AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
            return if (code == AppLanguage.HINDI.code) AppLanguage.HINDI else AppLanguage.ENGLISH
        }
        set(value) {
            prefs.edit().putString(KEY_LANG, value.code).commit()
            _currentLanguageFlow.value = value
            applyLocale(value)
        }

    fun applyLocale(language: AppLanguage) {
        try {
            java.util.Locale.setDefault(java.util.Locale(language.code))
            val appLocales = LocaleListCompat.forLanguageTags(language.code)
            AppCompatDelegate.setApplicationLocales(appLocales)
        } catch (e: Exception) {
            android.util.Log.e("LanguageConfigStore", "Failed to set application locales: ${e.message}")
        }
    }

    companion object {
        private const val KEY_LANG = "app_language"
    }
}
