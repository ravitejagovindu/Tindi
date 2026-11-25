package com.tindi.app.data.remote

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/** Endpoints from backend/API.md. Paths are relative to the base URL. */
interface TindiApi {
    @POST("auth/signup") suspend fun signUp(@Body body: SignUpRequest): retrofit2.Response<AuthResponse>
    @POST("auth/login") suspend fun logIn(@Body body: LoginRequest): retrofit2.Response<AuthResponse>
    @POST("auth/logout") suspend fun logOut(): retrofit2.Response<Unit>
    @GET("me") suspend fun me(): retrofit2.Response<MeDto>

    @GET("categories") suspend fun categories(): retrofit2.Response<List<CategoryDto>>
    @POST("categories") suspend fun createCategory(@Body body: NameRequest): retrofit2.Response<CategoryDto>
    @PATCH("categories/{id}") suspend fun renameCategory(@Path("id") id: String, @Body body: NameRequest): retrofit2.Response<CategoryDto>
    @DELETE("categories/{id}") suspend fun deleteCategory(@Path("id") id: String): retrofit2.Response<Unit>

    @GET("recipes") suspend fun recipes(): retrofit2.Response<List<RecipeDto>>
    @GET("recipes/{id}") suspend fun recipe(@Path("id") id: String): retrofit2.Response<RecipeDto>
    @POST("recipes") suspend fun createRecipe(@Body body: RecipeRequest): retrofit2.Response<RecipeDto>
    @PUT("recipes/{id}") suspend fun updateRecipe(@Path("id") id: String, @Body body: RecipeRequest): retrofit2.Response<RecipeDto>
    @DELETE("recipes/{id}") suspend fun deleteRecipe(@Path("id") id: String): retrofit2.Response<Unit>
}

/** Adds the bearer token. A 401 on a call that carried a token ends the session. */
class AuthInterceptor(private val store: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = store.session.value?.token
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else chain.request()
        val response = chain.proceed(request)
        // Only the session that sent this request may be ended by it (not one that logged in meanwhile).
        if (response.code == 401 && token != null) store.clearIf(token)
        return response
    }
}

object ApiFactory {
    fun create(baseUrl: String, store: SessionStore): TindiApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(store))
            .build()
        val normalized = baseUrl.trim().trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
            .build()
            .create(TindiApi::class.java)
    }
}
