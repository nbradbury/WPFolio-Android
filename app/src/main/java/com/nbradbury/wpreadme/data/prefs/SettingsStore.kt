package com.nbradbury.wpreadme.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { LIGHT, PAPER, DARK, SYSTEM }

data class ReaderSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textScale: Float = 1f,
)

data class WpcomAccount(
    val token: String,
    val displayName: String,
    val avatarUrl: String?,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<ReaderSettings> = store.data.map { prefs ->
        ReaderSettings(
            theme = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            textScale = prefs[TEXT_SCALE] ?: 1f,
        )
    }.distinctUntilChanged()

    val account: Flow<WpcomAccount?> = store.data.map { prefs ->
        prefs[TOKEN]?.let { token ->
            WpcomAccount(
                token = token,
                displayName = prefs[DISPLAY_NAME].orEmpty(),
                avatarUrl = prefs[AVATAR_URL],
            )
        }
    }.distinctUntilChanged()

    suspend fun token(): String? = store.data.first()[TOKEN]

    suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[THEME] = theme.name }
    }

    suspend fun setTextScale(scale: Float) {
        store.edit { it[TEXT_SCALE] = scale }
    }

    suspend fun saveAccount(account: WpcomAccount) {
        store.edit {
            it[TOKEN] = account.token
            it[DISPLAY_NAME] = account.displayName
            if (account.avatarUrl != null) it[AVATAR_URL] = account.avatarUrl else it.remove(AVATAR_URL)
        }
    }

    suspend fun clearAccount() {
        store.edit {
            it.remove(TOKEN)
            it.remove(DISPLAY_NAME)
            it.remove(AVATAR_URL)
        }
    }

    suspend fun setOAuthState(state: String?) {
        store.edit { if (state != null) it[OAUTH_STATE] = state else it.remove(OAUTH_STATE) }
    }

    suspend fun oauthState(): String? = store.data.first()[OAUTH_STATE]

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val TOKEN = stringPreferencesKey("wpcom_token")
        val DISPLAY_NAME = stringPreferencesKey("wpcom_display_name")
        val AVATAR_URL = stringPreferencesKey("wpcom_avatar_url")
        val OAUTH_STATE = stringPreferencesKey("wpcom_oauth_state")
    }
}
