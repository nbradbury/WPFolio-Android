package com.nbradbury.wpfolio.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class WpcomAccount(
    val token: String,
    val displayName: String,
    val avatarUrl: String?,
    /** Implicit-flow tokens can't be refreshed; once this passes the user has to sign in again. */
    val expiresAt: Long? = null,
) {
    fun isExpired() = expiresAt != null && System.currentTimeMillis() >= expiresAt
}

data class PendingOAuth(val state: String, val codeVerifier: String)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    /** Whether the reader has swiped between posts, after which the swipe hint stays hidden. */
    val swipeHintSeen: Flow<Boolean> = store.data.map { it[SWIPE_HINT_SEEN] == true }.distinctUntilChanged()

    suspend fun setSwipeHintSeen() {
        store.edit { it[SWIPE_HINT_SEEN] = true }
    }

    val account: Flow<WpcomAccount?> = store.data.map { prefs ->
        prefs[TOKEN]?.let { token ->
            WpcomAccount(
                token = token,
                displayName = prefs[DISPLAY_NAME].orEmpty(),
                avatarUrl = prefs[AVATAR_URL],
                expiresAt = prefs[EXPIRES_AT],
            )
        }
    }.distinctUntilChanged()

    suspend fun token(): String? = store.data.first()[TOKEN]

    suspend fun saveAccount(account: WpcomAccount) {
        store.edit {
            it[TOKEN] = account.token
            it[DISPLAY_NAME] = account.displayName
            if (account.avatarUrl != null) it[AVATAR_URL] = account.avatarUrl else it.remove(AVATAR_URL)
            if (account.expiresAt != null) it[EXPIRES_AT] = account.expiresAt else it.remove(EXPIRES_AT)
        }
    }

    suspend fun clearAccount() {
        store.edit {
            it.remove(TOKEN)
            it.remove(DISPLAY_NAME)
            it.remove(AVATAR_URL)
            it.remove(EXPIRES_AT)
        }
    }

    /** Saves the state and PKCE verifier for a sign-in in progress, surviving process death. */
    suspend fun setPendingOAuth(pending: PendingOAuth?) {
        store.edit {
            if (pending != null) {
                it[OAUTH_STATE] = pending.state
                it[OAUTH_VERIFIER] = pending.codeVerifier
            } else {
                it.remove(OAUTH_STATE)
                it.remove(OAUTH_VERIFIER)
            }
        }
    }

    suspend fun pendingOAuth(): PendingOAuth? {
        val prefs = store.data.first()
        val state = prefs[OAUTH_STATE] ?: return null
        val verifier = prefs[OAUTH_VERIFIER] ?: return null
        return PendingOAuth(state, verifier)
    }

    private companion object {
        val TOKEN = stringPreferencesKey("wpcom_token")
        val DISPLAY_NAME = stringPreferencesKey("wpcom_display_name")
        val AVATAR_URL = stringPreferencesKey("wpcom_avatar_url")
        val EXPIRES_AT = longPreferencesKey("wpcom_token_expires_at")
        val OAUTH_STATE = stringPreferencesKey("wpcom_oauth_state")
        val OAUTH_VERIFIER = stringPreferencesKey("wpcom_oauth_verifier")
        val SWIPE_HINT_SEEN = booleanPreferencesKey("swipe_hint_seen")
    }
}
