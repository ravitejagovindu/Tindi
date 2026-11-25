package com.tindi.app

import com.tindi.app.data.remote.ApiError
import com.tindi.app.data.remote.ApiResult
import com.tindi.app.data.remote.apiCall
import com.tindi.app.data.toAuthErrors
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ErrorMappingTest {
    private val validation = """{"error":{"code":"VALIDATION_FAILED","message":"Please fix the highlighted fields","fields":{"email":"Enter a valid email","steps[2]":"Too long"}}}"""

    @Test fun parsesCodeMessageAndFields() {
        val e = ApiError.parse(400, validation)
        assertEquals("VALIDATION_FAILED", e.code)
        assertEquals("Enter a valid email", e.fields["email"])
        assertEquals("Too long", e.fields["steps[2]"])
        assertEquals("Please fix the highlighted fields", e.message)
        assertEquals(ApiError.Kind.Other, e.kind)
    }

    @Test fun namedCodesGetClearMessages() {
        fun msg(status: Int, code: String) = ApiError.parse(status, """{"error":{"code":"$code","message":"server text"}}""").message
        assertEquals("Wrong email or password.", msg(401, "INVALID_CREDENTIALS"))
        assertEquals("An account with this email already exists.", msg(409, "EMAIL_TAKEN"))
        assertEquals("You already have a category with this name.", msg(409, "CATEGORY_NAME_TAKEN"))
        assertEquals("Starter categories cannot be renamed or deleted.", msg(403, "STARTER_READ_ONLY"))
    }

    @Test fun http401IsUnauthorizedAnd5xxIsServer() {
        assertEquals(ApiError.Kind.Unauthorized, ApiError.parse(401, """{"error":{"code":"UNAUTHORIZED","message":"x"}}""").kind)
        assertEquals(ApiError.Kind.Server, ApiError.parse(500, """{"error":{"code":"INTERNAL_ERROR","message":"boom"}}""").kind)
    }

    @Test fun garbageBodyDoesNotCrash() {
        val e = ApiError.parse(502, "<html>Bad gateway</html>")
        assertNull(e.code)
        assertEquals(ApiError.Kind.Server, e.kind)
        assertEquals("The server had a problem. Try again in a moment.", e.message)
        assertEquals("Something went wrong. Try again.", ApiError.parse(418, null).message)
    }

    @Test fun authErrorsLandOnTheMatchingInputs() {
        val fields = ApiError.parse(400, validation).toAuthErrors()
        assertEquals("Enter a valid email", fields.email)
        assertNull(fields.form)

        val taken = ApiError.parse(409, """{"error":{"code":"EMAIL_TAKEN","message":"x","fields":{"email":"taken"}}}""").toAuthErrors()
        assertEquals("An account with this email already exists.", taken.email)

        val creds = ApiError.parse(401, """{"error":{"code":"INVALID_CREDENTIALS","message":"x"}}""").toAuthErrors()
        assertEquals("Wrong email or password.", creds.form)
        assertNull(creds.email)

        assertEquals(ApiError.CANT_REACH, ApiError.network().toAuthErrors().form)
    }

    // ---- through a real HTTP stack ----

    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun stop() { runCatching { server.shutdown() } }

    @Test fun unreachableServerIsANetworkErrorNotACrash() = runBlocking {
        val api = TestEnv.api(server.url("/api/v1/").toString().also { server.shutdown() })
        val r = apiCall { api.me() }
        assertTrue(r is ApiResult.Fail)
        val e = (r as ApiResult.Fail).error
        assertTrue(e.isNetwork)
        assertEquals("Can't reach server. Check your connection and try again.", e.message)
    }

    @Test fun errorBodyIsReadFromARealResponse() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":{"code":"EMAIL_TAKEN","message":"Email taken","fields":{"email":"Email taken"}}}"""))
        val api = TestEnv.api(server.url("/api/v1").toString())
        val e = (apiCall { api.logIn(com.tindi.app.data.remote.LoginRequest("a@b.co", "x")) } as ApiResult.Fail).error
        assertEquals("EMAIL_TAKEN", e.code)
        assertEquals(409, e.httpStatus)
    }

    @Test fun malformedSuccessBodyIsHandled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        val api = TestEnv.api(server.url("/api/v1").toString())
        assertTrue(apiCall { api.me() } is ApiResult.Fail)
    }
}
