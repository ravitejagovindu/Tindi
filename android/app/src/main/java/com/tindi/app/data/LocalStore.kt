package com.tindi.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredAccount(
    val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
)

@Serializable
data class AccountData(
    val account: StoredAccount,
    val recipes: List<Recipe> = emptyList(),
    /** The user's own categories. The starter set is never stored. */
    val categories: List<Category> = emptyList(),
)

@Serializable
data class Db(
    val accounts: List<AccountData> = emptyList(),
    val currentAccountId: String? = null,
) {
    val current: AccountData? get() = accounts.firstOrNull { it.account.id == currentAccountId }

    fun updateCurrent(transform: (AccountData) -> AccountData): Db {
        val id = currentAccountId ?: return this
        return copy(accounts = accounts.map { if (it.account.id == id) transform(it) else it })
    }
}

/** In-memory state mirrored to one JSON file in app-private storage. */
class LocalStore(private val file: File, scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val _db = MutableStateFlow(Db())
    private val _loaded = MutableStateFlow(false)

    val db: StateFlow<Db> = _db
    val loaded: StateFlow<Boolean> = _loaded

    init {
        scope.launch(Dispatchers.IO) {
            val read = runCatching {
                if (file.exists()) json.decodeFromString(Db.serializer(), file.readText()) else Db()
            }.getOrDefault(Db())
            _db.value = read
            _loaded.value = true
        }
    }

    suspend fun update(transform: (Db) -> Db) {
        _loaded.first { it }
        mutex.withLock {
            val next = transform(_db.value)
            _db.value = next
            withContext(Dispatchers.IO) {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.encodeToString(Db.serializer(), next))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText())
                    tmp.delete()
                }
            }
        }
    }
}
