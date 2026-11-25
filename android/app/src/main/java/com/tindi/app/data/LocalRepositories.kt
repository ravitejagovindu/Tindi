package com.tindi.app.data

import com.tindi.app.domain.AuthValidator
import com.tindi.app.domain.removeCategoryFromRecipes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private fun hash(password: String): String =
    MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        .joinToString("") { "%02x".format(it) }

class LocalAuthRepository(private val store: LocalStore) : AuthRepository {
    override val ready: Flow<Boolean> = store.loaded
    override val session: Flow<Session?> = store.db.map { db ->
        db.current?.account?.let { Session(it.name, it.email) }
    }

    override suspend fun signUp(name: String, email: String, password: String): AuthResult {
        val errors = AuthValidator.validateSignUp(name, email, password)
        if (errors != null) return AuthResult.Failure(errors)
        val normalized = email.trim().lowercase()
        var taken = false
        store.update { db ->
            if (db.accounts.any { it.account.email == normalized }) {
                taken = true
                db
            } else {
                val id = UUID.randomUUID().toString()
                val data = AccountData(
                    account = StoredAccount(id, name.trim(), normalized, hash(password)),
                    recipes = if (SEED_SAMPLE_RECIPES) SampleData.recipes() else emptyList(),
                )
                db.copy(accounts = db.accounts + data, currentAccountId = id)
            }
        }
        return if (taken) {
            AuthResult.Failure(AuthErrors(email = "An account with this email already exists."))
        } else AuthResult.Success
    }

    override suspend fun logIn(email: String, password: String): AuthResult {
        val errors = AuthValidator.validateLogIn(email, password)
        if (errors != null) return AuthResult.Failure(errors)
        val normalized = email.trim().lowercase()
        var ok = false
        store.update { db ->
            val match = db.accounts.firstOrNull {
                it.account.email == normalized && it.account.passwordHash == hash(password)
            }
            if (match != null) {
                ok = true
                db.copy(currentAccountId = match.account.id)
            } else db
        }
        return if (ok) AuthResult.Success
        else AuthResult.Failure(AuthErrors(form = "Wrong email or password."))
    }

    override suspend fun logOut() {
        store.update { it.copy(currentAccountId = null) }
    }
}

class LocalRecipeRepository(private val store: LocalStore) : RecipeRepository {
    override val loaded: Flow<Boolean> = store.loaded
    override val recipes: Flow<List<Recipe>> = store.db.map { it.current?.recipes.orEmpty() }

    override suspend fun add(recipe: Recipe) {
        store.update { db -> db.updateCurrent { it.copy(recipes = listOf(recipe) + it.recipes) } }
    }
}

class LocalCategoryRepository(private val store: LocalStore) : CategoryRepository {
    override val categories: Flow<List<Category>> = store.db.map {
        Categories.starter + it.current?.categories.orEmpty()
    }

    private fun nameError(name: String, existing: List<Category>, ignoreId: String? = null): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Enter a category name."
        if (n.length > 30) return "Keep it under 30 characters."
        val all = Categories.starter + existing
        if (n.equals(Categories.UNCATEGORISED_NAME, true) ||
            all.any { it.id != ignoreId && it.name.equals(n, true) }
        ) return "You already have a category with this name."
        return null
    }

    override suspend fun create(name: String): String? {
        var error: String? = null
        store.update { db ->
            val own = db.current?.categories.orEmpty()
            error = nameError(name, own)
            if (error != null) db
            else db.updateCurrent {
                it.copy(categories = it.categories + Category(UUID.randomUUID().toString(), name.trim()))
            }
        }
        return error
    }

    override suspend fun rename(id: String, name: String): String? {
        var error: String? = null
        store.update { db ->
            val own = db.current?.categories.orEmpty()
            error = nameError(name, own, ignoreId = id)
            if (error != null) db
            else db.updateCurrent { acc ->
                acc.copy(categories = acc.categories.map { if (it.id == id) it.copy(name = name.trim()) else it })
            }
        }
        return error
    }

    override suspend fun delete(id: String) {
        store.update { db ->
            db.updateCurrent { acc ->
                // Only user-made categories can go; starter ids are not in this list.
                if (acc.categories.none { it.id == id }) acc
                else acc.copy(
                    categories = acc.categories.filterNot { it.id == id },
                    recipes = removeCategoryFromRecipes(acc.recipes, id),
                )
            }
        }
    }
}
