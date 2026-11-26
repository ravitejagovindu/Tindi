package com.tindi.app.data

import com.tindi.app.data.remote.ApiError
import com.tindi.app.data.remote.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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
    /** False until the saved session has been read from the device. */
    val ready: Flow<Boolean>

    /** Null means logged out. Any 401 from the server sets this back to null. */
    val session: Flow<Session?>

    /** From GET /me. Null until [refreshProfile] has succeeded. */
    val profile: Flow<Profile?>

    suspend fun signUp(name: String, email: String, password: String): AuthResult
    suspend fun logIn(email: String, password: String): AuthResult

    /** Calls the server, then clears the token and cached data. Clears locally even if the server is unreachable. */
    suspend fun logOut()

    /** Returns an error to show, or null on success. */
    suspend fun refreshProfile(): ApiError?
}

interface RecipeRepository {
    /** False until the first load has finished. Drives the Loading skeleton. */
    val loaded: Flow<Boolean>

    /** Why the first load failed, shown with a Retry. Null once loaded. */
    val loadError: Flow<String?>
    val recipes: StateFlow<List<Recipe>>

    /** GET /recipes. Silent when data is already shown. */
    suspend fun refresh()

    /** GET /recipes/{id}. Updates the cached copy, or drops it on 404. */
    suspend fun get(id: String): ApiResult<Recipe>
    suspend fun create(input: RecipeInput): ApiResult<Recipe>
    suspend fun update(id: String, input: RecipeInput): ApiResult<Recipe>
    suspend fun delete(id: String): ApiResult<Unit>
}

interface CategoryRepository {
    /** Starter set first, then the user's own. Shows the starter set until the first load finishes. */
    val categories: StateFlow<List<Category>>
    val loaded: Flow<Boolean>
    val loadError: Flow<String?>
    suspend fun refresh()

    /** Each returns an error message to show, or null on success. */
    suspend fun create(name: String): String?
    suspend fun rename(id: String, name: String): String?

    /** Recipes in the deleted category move to Uncategorised (the server does it, recipes are refreshed). */
    suspend fun delete(id: String): String?
}

/** What the server found for one YouTube link (POST /source/fetch). */
data class VideoSource(
    /** The video link to keep with the recipe, as the server returns it. */
    val url: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String?,
    val description: String,
    val comments: List<SourceComment>,
    val suggestedIngredients: List<String>,
    val suggestedSteps: List<String>,
    val confidence: Confidence,
    val foundIn: FoundIn,
)

/** Why a fetch failed. Each one has a message for the user in [FetchFailure.message]. */
enum class FetchFailure(val message: String, val canRetry: Boolean) {
    NotFound("Video not found or private", false),
    QuotaExceeded("Daily fetch limit reached, try again tomorrow or paste the text", false),
    Unavailable("Fetching isn't available right now, paste the text instead", true),
    Network("Can't reach server", true),
    Other("Something went wrong while getting the video", true),
}

sealed interface FetchResult {
    data class Success(val source: VideoSource) : FetchResult

    /** Not a YouTube link (Instagram and so on). No network call was needed to know. */
    data object NotYouTube : FetchResult
    data class Failed(val reason: FetchFailure) : FetchResult
}

/** Turns a link into a video's text. The real one asks the Tindi server, which asks YouTube. */
interface SourceFetcher {
    suspend fun fetch(url: String): FetchResult
}
