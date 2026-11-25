package com.tindi.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The bearer token plus the name and email shown in the app. The token never expires. */
data class StoredSession(val token: String, val name: String, val email: String)

interface SessionStore {
    /** False until the saved session has been read from the device. */
    val loaded: StateFlow<Boolean>
    val session: StateFlow<StoredSession?>
    fun save(session: StoredSession)
    fun clear()

    /** Clears only if [token] is still the current one. */
    fun clearIf(token: String) {
        if (session.value?.token == token) clear()
    }
}

class InMemorySessionStore(initial: StoredSession? = null) : SessionStore {
    override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
    private val _session = MutableStateFlow(initial)
    override val session: StateFlow<StoredSession?> = _session
    override fun save(session: StoredSession) { _session.value = session }
    override fun clear() { _session.value = null }
}

/** Token kept in EncryptedSharedPreferences. Falls back to memory only if the keystore is unusable. */
class EncryptedSessionStore(private val context: Context, scope: CoroutineScope) : SessionStore {
    private val _loaded = MutableStateFlow(false)
    private val _session = MutableStateFlow<StoredSession?>(null)
    override val loaded: StateFlow<Boolean> = _loaded
    override val session: StateFlow<StoredSession?> = _session

    @Volatile private var prefs: SharedPreferences? = null

    init {
        scope.launch(Dispatchers.IO) {
            prefs = open()
            val p = prefs
            val token = p?.getString(KEY_TOKEN, null)
            if (p != null && token != null) {
                _session.value = StoredSession(token, p.getString(KEY_NAME, "").orEmpty(), p.getString(KEY_EMAIL, "").orEmpty())
            }
            _loaded.value = true
        }
    }

    private fun create(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun open(): SharedPreferences? = try {
        create()
    } catch (e: Exception) {
        // Corrupt keystore or file (restored backup, changed lock screen). Start clean.
        context.deleteSharedPreferences(FILE)
        try { create() } catch (e2: Exception) { null }
    }

    override fun save(session: StoredSession) {
        _session.value = session
        prefs?.edit()
            ?.putString(KEY_TOKEN, session.token)
            ?.putString(KEY_NAME, session.name)
            ?.putString(KEY_EMAIL, session.email)
            ?.apply()
    }

    override fun clear() {
        _session.value = null
        prefs?.edit()?.clear()?.apply()
    }

    private companion object {
        const val FILE = "tindi_session"
        const val KEY_TOKEN = "token"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
    }
}
