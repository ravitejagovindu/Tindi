package com.tindi.app

import com.tindi.app.data.ApiAuthRepository
import com.tindi.app.data.ApiCategoryRepository
import com.tindi.app.data.ApiRecipeRepository
import com.tindi.app.data.AuthResult
import com.tindi.app.data.Categories
import com.tindi.app.data.Ingredient
import com.tindi.app.data.RecipeInput
import com.tindi.app.data.SourceType
import com.tindi.app.data.remote.ApiFactory
import com.tindi.app.data.remote.ApiResult
import com.tindi.app.data.remote.InMemorySessionStore
import com.tindi.app.data.remote.SessionStore
import com.tindi.app.data.remote.StoredSession
import com.tindi.app.data.remote.TindiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

object TestEnv {
    fun api(baseUrl: String, store: SessionStore = InMemorySessionStore()): TindiApi = ApiFactory.create(baseUrl, store)
}

private const val DINNER = "00000000-0000-4000-8000-000000000004"
private const val TOKEN = "tok-123"

private const val RECIPE_JSON = """{"id":"r1","name":"Halwa","ingredients":[{"quantity":"250 g","item":"Paneer"}],"steps":["Blend"],"categoryIds":["$DINNER"],"sourceType":"YOUTUBE","sourceUrl":"https://youtu.be/x","createdAt":"2026-11-25T15:36:20Z","updatedAt":"2026-11-25T15:36:20Z"}"""
private const val RECIPE2_JSON = """{"id":"r2","name":"Dal","ingredients":[],"steps":["Cook"],"categoryIds":[],"sourceType":"PASTED","sourceUrl":null}"""

private fun ok(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)
private fun err(code: Int, errCode: String, fields: String? = null) = MockResponse().setResponseCode(code).setBody(
    """{"error":{"code":"$errCode","message":"server says $errCode"${if (fields != null) ""","fields":$fields""" else ""}}}""",
)

class RepositoriesTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var api: TindiApi
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private lateinit var auth: ApiAuthRepository
    private lateinit var recipes: ApiRecipeRepository
    private lateinit var categories: ApiCategoryRepository

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        store = InMemorySessionStore(StoredSession(TOKEN, "Ravi", "r@x.co"))
        api = ApiFactory.create(server.url("/api/v1").toString(), store)
        auth = ApiAuthRepository(api, store, scope)
        recipes = ApiRecipeRepository(api, store, scope)
        categories = ApiCategoryRepository(api, store, recipes, scope)
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun input() = RecipeInput(
        name = " Halwa ", ingredients = listOf(Ingredient("250 g", "Paneer")), steps = listOf("Blend"),
        categoryIds = listOf(DINNER), sourceType = SourceType.YouTube, sourceUrl = "https://youtu.be/x",
    )

    // ---------- auth ----------

    @Test fun loginStoresSessionAndLaterCallsCarryTheBearerToken() = runBlocking {
        store.clear()
        server.enqueue(ok("""{"token":"abc","user":{"id":"u1","name":"Ravi","email":"r@x.co"}}"""))
        server.enqueue(ok("""{"id":"u1","name":"Ravi","email":"r@x.co","recipeCount":3,"categoryCount":2}"""))

        assertEquals(AuthResult.Success, auth.logIn(" R@x.co ", "secret1"))
        assertEquals("abc", store.session.value?.token)

        val login = server.takeRequest()
        assertEquals("/api/v1/auth/login", login.path)
        assertNull(login.getHeader("Authorization"))
        assertTrue(login.body.readUtf8().contains(""""email":"R@x.co""""))

        assertNull(auth.refreshProfile())
        val me = server.takeRequest()
        assertEquals("/api/v1/me", me.path)
        assertEquals("Bearer abc", me.getHeader("Authorization"))
        val p = auth.profile.first()!!
        assertEquals(3, p.recipeCount)
        assertEquals(2, p.categoryCount)
    }

    @Test fun wrongPasswordShowsFormErrorAndKeepsLoggedOut() = runBlocking {
        store.clear()
        server.enqueue(err(401, "INVALID_CREDENTIALS"))
        val r = auth.logIn("r@x.co", "nope123") as AuthResult.Failure
        assertEquals("Wrong email or password.", r.errors.form)
        assertNull(store.session.value)
    }

    @Test fun emailTakenLandsOnTheEmailField() = runBlocking {
        store.clear()
        server.enqueue(err(409, "EMAIL_TAKEN", """{"email":"taken"}"""))
        val r = auth.signUp("Ravi", "r@x.co", "secret1") as AuthResult.Failure
        assertEquals("An account with this email already exists.", r.errors.email)
        assertNull(r.errors.form)
    }

    @Test fun serverFieldErrorsLandOnTheirInputs() = runBlocking {
        store.clear()
        server.enqueue(err(400, "VALIDATION_FAILED", """{"name":"Too long","password":"Too short"}"""))
        val r = auth.signUp("Ravi", "r@x.co", "secret1") as AuthResult.Failure
        assertEquals("Too long", r.errors.name)
        assertEquals("Too short", r.errors.password)
    }

    @Test fun localValidationStopsBadInputBeforeTheNetwork() = runBlocking {
        val r = auth.logIn("", "") as AuthResult.Failure
        assertNotNull(r.errors.email)
        assertEquals(0, server.requestCount)
    }

    @Test fun logoutCallsServerThenClearsTokenAndCaches() = runBlocking {
        server.enqueue(ok("[$RECIPE_JSON]"))
        recipes.refresh()
        assertEquals(1, recipes.recipes.value.size)

        server.enqueue(MockResponse().setResponseCode(204))
        auth.logOut()
        server.takeRequest() // GET /recipes
        val out = server.takeRequest()
        assertEquals("POST", out.method)
        assertEquals("/api/v1/auth/logout", out.path)
        assertEquals("Bearer $TOKEN", out.getHeader("Authorization"))
        assertNull(store.session.value)
        assertTrue(recipes.recipes.value.isEmpty())
        assertFalse(recipes.loaded.first())
        assertEquals(Categories.starter, categories.categories.value)
    }

    @Test fun logoutStillClearsWhenServerIsUnreachable() = runBlocking {
        server.shutdown()
        auth.logOut()
        assertNull(store.session.value)
    }

    @Test fun any401WithTokenEndsTheSessionAndWipesCaches() = runBlocking {
        server.enqueue(ok("[$RECIPE_JSON]"))
        recipes.refresh()
        server.enqueue(err(401, "UNAUTHORIZED"))
        val e = auth.refreshProfile()
        assertEquals("UNAUTHORIZED", e?.code)
        assertNull(store.session.value)
        assertTrue(recipes.recipes.value.isEmpty())
    }

    // ---------- recipes ----------

    @Test fun refreshLoadsAndMapsRecipes() = runBlocking {
        server.enqueue(ok("[$RECIPE_JSON,$RECIPE2_JSON]"))
        recipes.refresh()
        val list = recipes.recipes.value
        assertEquals(listOf("r1", "r2"), list.map { it.id })
        assertEquals(SourceType.YouTube, list[0].sourceType)
        assertEquals(SourceType.Pasted, list[1].sourceType)
        assertEquals(listOf(DINNER), list[0].categoryIds)
        assertEquals("250 g Paneer", list[0].ingredients[0].display)
        assertTrue(list[1].categoryIds.isEmpty())
        assertTrue(recipes.loaded.first())
        assertNull(recipes.loadError.first())
    }

    @Test fun unreachableServerGivesRetryableErrorThenRecovers() = runBlocking {
        val dead = MockWebServer().also { it.start() }
        val deadApi = ApiFactory.create(dead.url("/api/v1").toString(), store)
        dead.shutdown()
        val repo = ApiRecipeRepository(deadApi, store, scope)
        repo.refresh()
        assertFalse(repo.loaded.first())
        assertEquals("Can't reach server. Check your connection and try again.", repo.loadError.first())

        // Retry against a live server.
        server.enqueue(ok("[$RECIPE_JSON]"))
        recipes.refresh()
        assertTrue(recipes.loaded.first())
        assertNull(recipes.loadError.first())
    }

    @Test fun failedBackgroundRefreshKeepsWhatIsOnScreen() = runBlocking {
        server.enqueue(ok("[$RECIPE_JSON]"))
        recipes.refresh()
        server.enqueue(err(500, "INTERNAL_ERROR"))
        recipes.refresh()
        assertEquals(1, recipes.recipes.value.size)
        assertTrue(recipes.loaded.first())
        assertNull(recipes.loadError.first())
    }

    @Test fun createSendsServerShapeAndPrependsToCache() = runBlocking {
        server.enqueue(ok("[$RECIPE2_JSON]"))
        recipes.refresh()
        server.takeRequest()

        server.enqueue(ok(RECIPE_JSON, 201))
        val r = recipes.create(input())
        assertTrue(r is ApiResult.Ok)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/recipes", req.path)
        val body = req.body.readUtf8()
        assertTrue(body, body.contains(""""name":"Halwa""""))
        assertTrue(body, body.contains(""""sourceType":"YOUTUBE""""))
        assertTrue(body, body.contains(""""categoryIds":["$DINNER"]"""))
        assertEquals(listOf("r1", "r2"), recipes.recipes.value.map { it.id })
    }

    @Test fun createValidationErrorKeepsFieldsAndCache() = runBlocking {
        server.enqueue(err(400, "VALIDATION_FAILED", """{"ingredients[0].item":"Required"}"""))
        val e = (recipes.create(input()) as ApiResult.Fail).error
        assertEquals("Required", e.fields["ingredients[0].item"])
        assertTrue(recipes.recipes.value.isEmpty())
    }

    @Test fun updateUsesPutAndReplacesInPlace() = runBlocking {
        server.enqueue(ok("[$RECIPE_JSON,$RECIPE2_JSON]"))
        recipes.refresh()
        server.takeRequest()

        server.enqueue(ok(RECIPE_JSON.replace("Halwa", "Kheer")))
        val r = recipes.update("r1", input().copy(name = "Kheer"))
        assertTrue(r is ApiResult.Ok)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api/v1/recipes/r1", req.path)
        assertEquals(listOf("Kheer", "Dal"), recipes.recipes.value.map { it.name })
    }

    @Test fun deleteRemovesFromCacheAndNotFoundCountsAsGone() = runBlocking {
        server.enqueue(ok("[$RECIPE_JSON,$RECIPE2_JSON]"))
        recipes.refresh()
        server.takeRequest()

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(recipes.delete("r1") is ApiResult.Ok)
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(listOf("r2"), recipes.recipes.value.map { it.id })

        server.enqueue(err(404, "NOT_FOUND"))
        assertTrue(recipes.delete("r2") is ApiResult.Fail)
        assertTrue(recipes.recipes.value.isEmpty())
    }

    @Test fun getRefreshesOneRecipe() = runBlocking {
        server.enqueue(ok(RECIPE_JSON))
        val r = recipes.get("r1") as ApiResult.Ok
        assertEquals("Halwa", r.value.name)
        assertEquals("/api/v1/recipes/r1", server.takeRequest().path)
        assertEquals(1, recipes.recipes.value.size)
    }

    // ---------- categories ----------

    @Test fun categoriesStartWithStarterSetThenShowServerList() = runBlocking {
        assertEquals(6, categories.categories.value.size)
        server.enqueue(ok("""[{"id":"${Categories.starter[0].id}","name":"Breakfast","starter":true},{"id":"c1","name":"Sweets","starter":false}]"""))
        categories.refresh()
        val list = categories.categories.value
        assertEquals(listOf("Breakfast", "Sweets"), list.map { it.name })
        assertTrue(list[0].builtIn)
        assertFalse(list[1].builtIn)
    }

    @Test fun createRejectsDuplicateWithClearMessage() = runBlocking {
        server.enqueue(err(409, "CATEGORY_NAME_TAKEN", """{"name":"taken"}"""))
        assertEquals("You already have a category with this name.", categories.create("Sweets"))
    }

    @Test fun createInsertsInNameOrder() = runBlocking {
        server.enqueue(ok("""{"id":"c2","name":"Zesty","starter":false}""", 201))
        server.enqueue(ok("""{"id":"c1","name":"apple","starter":false}""", 201))
        assertNull(categories.create("Zesty"))
        assertNull(categories.create("apple"))
        val own = categories.categories.value.filterNot { it.builtIn }.map { it.name }
        assertEquals(listOf("apple", "Zesty"), own)
        assertEquals(Categories.starter, categories.categories.value.take(6))
    }

    @Test fun renamingAStarterIsRefusedWithClearMessage() = runBlocking {
        server.enqueue(err(403, "STARTER_READ_ONLY"))
        assertEquals("Starter categories cannot be renamed or deleted.", categories.rename(DINNER, "Supper"))
        assertEquals("Dinner", categories.categories.value.first { it.id == DINNER }.name)
    }

    @Test fun renameUpdatesTheList() = runBlocking {
        server.enqueue(ok("""[{"id":"c1","name":"Sweets","starter":false}]"""))
        categories.refresh()
        server.enqueue(ok("""{"id":"c1","name":"Desserts","starter":false}"""))
        assertNull(categories.rename("c1", "Desserts"))
        server.takeRequest()
        assertEquals("PATCH", server.takeRequest().method)
        assertEquals("Desserts", categories.categories.value.single().name)
    }

    @Test fun deleteCategoryThenRefreshesRecipesBecauseServerMovedThem() = runBlocking {
        server.enqueue(ok("""[{"id":"c1","name":"Sweets","starter":false}]"""))
        categories.refresh()
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(ok("[$RECIPE2_JSON]"))

        assertNull(categories.delete("c1"))
        val del = server.takeRequest()
        assertEquals("DELETE", del.method)
        assertEquals("/api/v1/categories/c1", del.path)
        assertEquals("/api/v1/recipes", server.takeRequest().path)
        assertTrue(categories.categories.value.none { it.id == "c1" })
        assertEquals(listOf("r2"), recipes.recipes.value.map { it.id })
    }

    @Test fun offlineCategoryActionsReturnCantReach() = runBlocking {
        server.shutdown()
        assertEquals("Can't reach server. Check your connection and try again.", categories.create("Sweets"))
        assertEquals("Can't reach server. Check your connection and try again.", categories.delete("c1"))
    }
}
