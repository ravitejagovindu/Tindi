package com.tindi.app.data

import com.tindi.app.data.remote.ApiError
import com.tindi.app.data.remote.ApiResult
import com.tindi.app.data.remote.LoginRequest
import com.tindi.app.data.remote.NameRequest
import com.tindi.app.data.remote.SessionStore
import com.tindi.app.data.remote.SignUpRequest
import com.tindi.app.data.remote.StoredSession
import com.tindi.app.data.remote.TindiApi
import com.tindi.app.data.remote.apiCall
import com.tindi.app.data.remote.apiUnitCall
import com.tindi.app.data.remote.map
import com.tindi.app.data.remote.toDomain
import com.tindi.app.data.remote.toRequest
import com.tindi.app.domain.AuthValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Maps a failed auth call onto the form: field errors on their inputs, everything else in the form line. */
fun ApiError.toAuthErrors(): AuthErrors {
    val name = fields["name"]
    val email = if (code == "EMAIL_TAKEN") message else fields["email"]
    val password = fields["password"]
    val form = if (name == null && email == null && password == null) message else null
    return AuthErrors(name = name, email = email, password = password, form = form)
}

/** Message for a category name field. */
private fun ApiError.categoryMessage(): String = when (code) {
    "CATEGORY_NAME_TAKEN", "STARTER_READ_ONLY" -> message
    else -> fields["name"] ?: message
}

/** Runs [onSessionEnd] whenever the session goes away (logout or a 401), so cached data never leaks to the next login. */
private fun SessionStore.onSessionEnd(scope: CoroutineScope, action: () -> Unit) {
    scope.launch { session.collect { if (it == null) action() } }
}

class ApiAuthRepository(
    private val api: TindiApi,
    private val store: SessionStore,
    scope: CoroutineScope,
) : AuthRepository {
    private val _profile = MutableStateFlow<Profile?>(null)

    override val ready: Flow<Boolean> = store.loaded
    override val session: Flow<Session?> =
        store.session.map { s -> s?.let { Session(it.name, it.email) } }.distinctUntilChanged()
    override val profile: Flow<Profile?> = _profile

    init {
        store.onSessionEnd(scope) { _profile.value = null }
    }

    override suspend fun signUp(name: String, email: String, password: String): AuthResult {
        AuthValidator.validateSignUp(name, email, password)?.let { return AuthResult.Failure(it) }
        return finish(apiCall { api.signUp(SignUpRequest(name.trim(), email.trim(), password)) })
    }

    override suspend fun logIn(email: String, password: String): AuthResult {
        AuthValidator.validateLogIn(email, password)?.let { return AuthResult.Failure(it) }
        return finish(apiCall { api.logIn(LoginRequest(email.trim(), password)) })
    }

    private fun finish(result: ApiResult<com.tindi.app.data.remote.AuthResponse>): AuthResult = when (result) {
        is ApiResult.Ok -> {
            store.save(StoredSession(result.value.token, result.value.user.name, result.value.user.email))
            AuthResult.Success
        }
        is ApiResult.Fail -> AuthResult.Failure(result.error.toAuthErrors())
    }

    override suspend fun logOut() {
        // If the laptop is unreachable the token stays valid on the server, but the user must still be able to leave.
        apiUnitCall { api.logOut() }
        store.clear()
    }

    override suspend fun refreshProfile(): ApiError? {
        val token = store.session.value?.token ?: return null
        return when (val r = apiCall { api.me() }) {
            is ApiResult.Ok -> {
                if (store.session.value?.token == token) {
                    val me = r.value
                    _profile.value = Profile(me.name, me.email, me.recipeCount, me.categoryCount)
                    val current = store.session.value
                    if (current != null && (current.name != me.name || current.email != me.email)) {
                        store.save(current.copy(name = me.name, email = me.email))
                    }
                }
                null
            }
            is ApiResult.Fail -> r.error
        }
    }
}

class ApiRecipeRepository(
    private val api: TindiApi,
    private val store: SessionStore,
    scope: CoroutineScope,
) : RecipeRepository {
    private val _recipes = MutableStateFlow<List<Recipe>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    override val recipes: StateFlow<List<Recipe>> = _recipes
    override val loaded: Flow<Boolean> = _loaded
    override val loadError: Flow<String?> = _error

    init {
        store.onSessionEnd(scope) {
            _recipes.value = emptyList()
            _loaded.value = false
            _error.value = null
        }
    }

    /** True while the session that started a call is still the current one. */
    private fun current(token: String) = store.session.value?.token == token

    override suspend fun refresh() {
        val token = store.session.value?.token ?: return
        if (!_loaded.value) _error.value = null
        when (val r = apiCall { api.recipes() }) {
            is ApiResult.Ok -> if (current(token)) {
                _recipes.value = r.value.map { it.toDomain() }
                _loaded.value = true
                _error.value = null
            }
            is ApiResult.Fail -> if (current(token) && !_loaded.value) _error.value = r.error.message
        }
    }

    override suspend fun get(id: String): ApiResult<Recipe> {
        val token = store.session.value?.token ?: return ApiResult.Fail(ApiError(ApiError.Kind.Unauthorized, 401, "UNAUTHORIZED"))
        val result = apiCall { api.recipe(id) }.map { it.toDomain() }
        if (current(token)) when (result) {
            is ApiResult.Ok -> upsert(result.value)
            is ApiResult.Fail -> if (result.error.code == "NOT_FOUND") remove(id)
        }
        return result
    }

    override suspend fun create(input: RecipeInput): ApiResult<Recipe> {
        val token = store.session.value?.token
        val result = apiCall { api.createRecipe(input.toRequest()) }.map { it.toDomain() }
        if (result is ApiResult.Ok && token != null && current(token)) upsert(result.value)
        return result
    }

    override suspend fun update(id: String, input: RecipeInput): ApiResult<Recipe> {
        val token = store.session.value?.token
        val result = apiCall { api.updateRecipe(id, input.toRequest()) }.map { it.toDomain() }
        if (token != null && current(token)) when (result) {
            is ApiResult.Ok -> upsert(result.value)
            is ApiResult.Fail -> if (result.error.code == "NOT_FOUND") remove(id)
        }
        return result
    }

    override suspend fun delete(id: String): ApiResult<Unit> {
        val token = store.session.value?.token
        val result = apiUnitCall { api.deleteRecipe(id) }
        val gone = result is ApiResult.Ok || (result as? ApiResult.Fail)?.error?.code == "NOT_FOUND"
        if (gone && token != null && current(token)) remove(id)
        return result
    }

    /** Replaces in place, or puts a new recipe first (the server lists newest first). */
    private fun upsert(recipe: Recipe) {
        val list = _recipes.value
        _recipes.value = if (list.any { it.id == recipe.id }) list.map { if (it.id == recipe.id) recipe else it }
        else listOf(recipe) + list
    }

    private fun remove(id: String) {
        _recipes.value = _recipes.value.filterNot { it.id == id }
    }
}

class ApiCategoryRepository(
    private val api: TindiApi,
    private val store: SessionStore,
    private val recipes: RecipeRepository,
    scope: CoroutineScope,
) : CategoryRepository {
    private val _categories = MutableStateFlow(Categories.starter)
    private val _loaded = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    override val categories: StateFlow<List<Category>> = _categories
    override val loaded: Flow<Boolean> = _loaded
    override val loadError: Flow<String?> = _error

    init {
        store.onSessionEnd(scope) {
            _categories.value = Categories.starter
            _loaded.value = false
            _error.value = null
        }
    }

    private fun current(token: String) = store.session.value?.token == token

    /** Starters first in their fixed order, then the user's own by name, like the server. */
    private fun ordered(list: List<Category>): List<Category> =
        list.filter { it.builtIn } + list.filterNot { it.builtIn }.sortedBy { it.name.lowercase() }

    override suspend fun refresh() {
        val token = store.session.value?.token ?: return
        if (!_loaded.value) _error.value = null
        when (val r = apiCall { api.categories() }) {
            is ApiResult.Ok -> if (current(token)) {
                _categories.value = r.value.map { it.toDomain() }
                _loaded.value = true
                _error.value = null
            }
            is ApiResult.Fail -> if (current(token) && !_loaded.value) _error.value = r.error.message
        }
    }

    override suspend fun create(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Enter a category name."
        if (n.length > 30) return "Keep it under 30 characters."
        val token = store.session.value?.token ?: return null
        return when (val r = apiCall { api.createCategory(NameRequest(n)) }) {
            is ApiResult.Ok -> {
                if (current(token)) _categories.value = ordered(_categories.value + r.value.toDomain())
                null
            }
            is ApiResult.Fail -> r.error.categoryMessage()
        }
    }

    override suspend fun rename(id: String, name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Enter a category name."
        if (n.length > 30) return "Keep it under 30 characters."
        val token = store.session.value?.token ?: return null
        return when (val r = apiCall { api.renameCategory(id, NameRequest(n)) }) {
            is ApiResult.Ok -> {
                if (current(token)) {
                    val updated = r.value.toDomain()
                    _categories.value = ordered(_categories.value.map { if (it.id == id) updated else it })
                }
                null
            }
            is ApiResult.Fail -> r.error.categoryMessage()
        }
    }

    override suspend fun delete(id: String): String? {
        val token = store.session.value?.token ?: return null
        return when (val r = apiUnitCall { api.deleteCategory(id) }) {
            is ApiResult.Ok -> {
                if (current(token)) _categories.value = _categories.value.filterNot { it.id == id }
                // The server moved the recipes; pick that up.
                recipes.refresh()
                null
            }
            is ApiResult.Fail -> {
                if (r.error.code == "NOT_FOUND" && current(token)) _categories.value = _categories.value.filterNot { it.id == id }
                r.error.message
            }
        }
    }
}
