package com.tindi.app.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException

/** Shared by the Retrofit converter and the error parser. */
val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** `{"error":{"code","message","fields"}}` as documented in backend/API.md. */
@Serializable
private data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
private data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
    val fields: Map<String, String>? = null,
)

class ApiError(
    val kind: Kind,
    val httpStatus: Int = 0,
    val code: String? = null,
    private val serverMessage: String? = null,
    val fields: Map<String, String> = emptyMap(),
) {
    enum class Kind { Network, Unauthorized, Server, Other }

    val isNetwork: Boolean get() = kind == Kind.Network

    /** Text that is safe to show to the user. */
    val message: String
        get() = when {
            kind == Kind.Network -> CANT_REACH
            code == "INVALID_CREDENTIALS" -> "Wrong email or password."
            code == "EMAIL_TAKEN" -> "An account with this email already exists."
            code == "CATEGORY_NAME_TAKEN" -> "You already have a category with this name."
            code == "STARTER_READ_ONLY" -> "Starter categories cannot be renamed or deleted."
            code == "UNAUTHORIZED" -> "Your session ended. Log in again."
            code == "NOT_FOUND" -> "That item no longer exists."
            kind == Kind.Server -> "The server had a problem. Try again in a moment."
            !serverMessage.isNullOrBlank() -> serverMessage
            else -> "Something went wrong. Try again."
        }

    companion object {
        const val CANT_REACH = "Can't reach server. Check your connection and try again."

        fun network() = ApiError(Kind.Network)

        fun parse(status: Int, body: String?): ApiError {
            val parsed = body?.takeIf { it.isNotBlank() }?.let {
                runCatching { ApiJson.decodeFromString<ErrorEnvelope>(it).error }.getOrNull()
            }
            val kind = when {
                status == 401 -> Kind.Unauthorized
                status >= 500 -> Kind.Server
                else -> Kind.Other
            }
            return ApiError(kind, status, parsed?.code, parsed?.message, parsed?.fields.orEmpty())
        }
    }
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Fail(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(f(value))
    is ApiResult.Fail -> this
}

/** Runs one call. Never throws except for coroutine cancellation: the laptop being off is a normal case. */
suspend fun <T : Any> apiCall(block: suspend () -> Response<T>): ApiResult<T> = guarded {
    val r = block()
    val body = r.body()
    when {
        r.isSuccessful && body != null -> ApiResult.Ok(body)
        r.isSuccessful -> ApiResult.Fail(ApiError(ApiError.Kind.Other, r.code(), serverMessage = "Empty response from server."))
        else -> ApiResult.Fail(ApiError.parse(r.code(), r.errorBody()?.string()))
    }
}

/** For 204 responses, which have no body. */
suspend fun apiUnitCall(block: suspend () -> Response<Unit>): ApiResult<Unit> = guarded {
    val r = block()
    if (r.isSuccessful) ApiResult.Ok(Unit) else ApiResult.Fail(ApiError.parse(r.code(), r.errorBody()?.string()))
}

private suspend fun <T> guarded(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    ApiResult.Fail(ApiError.network())
} catch (e: Exception) {
    ApiResult.Fail(ApiError(ApiError.Kind.Other, serverMessage = "Unexpected response from the server."))
}
