package com.tindi.app.data

import kotlinx.coroutines.flow.Flow

data class AuthErrors(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    val form: String? = null,
)

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val errors: AuthErrors) : AuthResult
}

interface AuthRepository {
    /** False until saved data has been read from the device. */
    val ready: Flow<Boolean>
    val session: Flow<Session?>
    suspend fun signUp(name: String, email: String, password: String): AuthResult
    suspend fun logIn(email: String, password: String): AuthResult
    suspend fun logOut()
}

interface RecipeRepository {
    /** False until saved data has been read. Drives the Loading skeleton. */
    val loaded: Flow<Boolean>
    val recipes: Flow<List<Recipe>>
    suspend fun add(recipe: Recipe)
}

interface CategoryRepository {
    /** Starter set first, then the user's own. */
    val categories: Flow<List<Category>>

    /** Each returns an error message to show in the field, or null on success. */
    suspend fun create(name: String): String?
    suspend fun rename(id: String, name: String): String?

    /** Recipes in the deleted category move to Uncategorised if they have no other category. */
    suspend fun delete(id: String)
}

data class VideoSource(
    val url: String,
    val title: String,
    val channel: String,
    val description: String,
    val pinnedComment: String,
    val topComments: List<String>,
)

sealed interface FetchResult {
    data class Success(val source: VideoSource) : FetchResult
    data object NotYouTube : FetchResult
    data object Failed : FetchResult
}

/** Shaped after the YouTube Data API: video snippet + comment threads for one link. */
interface SourceFetcher {
    suspend fun fetch(url: String): FetchResult
}
