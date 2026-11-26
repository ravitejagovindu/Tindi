package com.tindi.app

import com.tindi.app.data.Ingredient
import com.tindi.app.data.RecipeInput
import com.tindi.app.data.SourceType
import com.tindi.app.data.StepDetail
import com.tindi.app.data.StructureResult
import com.tindi.app.data.FetchResult
import com.tindi.app.data.ApiSourceFetcher
import com.tindi.app.data.ApiTextStructurer
import com.tindi.app.data.remote.ApiFactory
import com.tindi.app.data.remote.ApiJson
import com.tindi.app.data.remote.InMemorySessionStore
import com.tindi.app.data.remote.RecipeDto
import com.tindi.app.data.remote.StoredSession
import com.tindi.app.data.remote.toDomain
import com.tindi.app.data.remote.toRequest
import com.tindi.app.domain.StepBasis
import com.tindi.app.domain.draftFromPaste
import com.tindi.app.domain.draftFromStructured
import com.tindi.app.domain.draftFromVideo
import com.tindi.app.data.ApiCategoryRepository
import com.tindi.app.data.ApiRecipeRepository
import com.tindi.app.data.RecipeDraft
import com.tindi.app.ui.screens.ReviewViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StepDetailsSaveTest {
    private val ingredients = listOf(Ingredient("2", "tomatoes"), Ingredient("1 tbsp", "butter"), Ingredient("", "salt"))
    private val steps = listOf("Blend the tomatoes.", "Melt the butter.", "Serve hot.")
    private val details = listOf(
        StepDetail("Blend", listOf(0), "BLEND"),
        StepDetail("Melt", listOf(1, 2), "SAUTE"),
        StepDetail("Serve", emptyList(), "SERVE"),
    )
    private val basis = StepBasis.of(ingredients, steps, details)!!

    @Test fun untouchedListsPassTheLabelsThrough() {
        assertEquals(details, basis.detailsFor(ingredients, steps))
        assertEquals(details, basis.detailsFor(ingredients, steps.map { " $it " }))
    }

    @Test fun addingRemovingOrReorderingStepsDropsThem() {
        assertNull(basis.detailsFor(ingredients, steps + "Eat."))
        assertNull(basis.detailsFor(ingredients, steps.drop(1)))
        assertNull(basis.detailsFor(ingredients, steps.reversed()))
        assertNull(basis.detailsFor(ingredients, listOf(steps[0], "Melt the ghee.", steps[2])))
        assertNull(basis.detailsFor(ingredients, emptyList()))
    }

    @Test fun stepsPutBackAsTheyWereKeepThem() {
        val removedAndReadded = steps.dropLast(1) + steps.last()
        assertEquals(details, basis.detailsFor(ingredients, removedAndReadded))
    }

    @Test fun removingAnIngredientRenumbersTheIndexes() {
        val kept = basis.detailsFor(ingredients.drop(1), steps)!!
        assertEquals(listOf(emptyList(), listOf(0, 1), emptyList()), kept.map { it.ingredientIndexes })
        assertEquals(listOf("Blend", "Melt", "Serve"), kept.map { it.name })
        assertEquals(listOf("BLEND", "SAUTE", "SERVE"), kept.map { it.illustration })
    }

    @Test fun addedIngredientsMoveNothingAndReorderedOnesAreFollowed() {
        assertEquals(details, basis.detailsFor(ingredients + Ingredient("", "cream"), steps))
        val moved = basis.detailsFor(listOf(ingredients[2], ingredients[0], ingredients[1]), steps)!!
        assertEquals(listOf(listOf(1), listOf(2, 0), emptyList()), moved.map { it.ingredientIndexes })
    }

    @Test fun aChangedQuantityKeepsTheLink() {
        val changed = listOf(Ingredient("3", "tomatoes")) + ingredients.drop(1)
        assertEquals(details, basis.detailsFor(changed, steps))
    }

    @Test fun twoRowsForOneItemStayApart() {
        val doubled = listOf(Ingredient("1 cup", "water"), Ingredient("2 cups", "water"))
        val b = StepBasis.of(doubled, listOf("A", "B"), listOf(StepDetail("A", listOf(0)), StepDetail("B", listOf(1))))!!
        assertEquals(listOf(listOf(0), listOf(1)), b.detailsFor(doubled, listOf("A", "B"))!!.map { it.ingredientIndexes })
        assertEquals(
            listOf(emptyList(), listOf(0)),
            b.detailsFor(doubled.drop(1), listOf("A", "B"))!!.map { it.ingredientIndexes },
        )
    }

    @Test fun replacedIngredientsLoseTheirLinksButStaySafeToSend() {
        val out = basis.detailsFor(listOf(Ingredient("", "paneer")), steps)!!
        assertTrue(out.all { it.ingredientIndexes.isEmpty() })
        assertEquals(3, out.size)
    }

    @Test fun noLabelsOrMisalignedLabelsMeanNothingToSend() {
        assertNull(StepBasis.of(ingredients, steps, null))
        assertNull(StepBasis.of(ingredients, steps, details.drop(1)))
        assertNull(StepBasis.of(ingredients, emptyList(), emptyList()))
    }
}

/** stepDetails through the API models, from the server's JSON and back. */
@OptIn(ExperimentalCoroutinesApi::class)
class StepDetailsApiTest {
    private lateinit var server: MockWebServer
    private val store = InMemorySessionStore(StoredSession("tok", "Ravi", "r@x.co"))

    private val suggested = """{"ingredients":["2 tomatoes","1 tbsp butter"],"steps":["Blend.","Melt."],
        "stepDetails":[{"name":"Blend","ingredientIndexes":[0],"illustration":"BLEND"},
                       {"name":"Melt","ingredientIndexes":[1],"illustration":"SOMETHING_NEW"}],
        "confidence":"HIGH","foundIn":"PASTED","method":"LLM","warnings":[]}"""
    private val expected = listOf(StepDetail("Blend", listOf(0), "BLEND"), StepDetail("Melt", listOf(1), "SOMETHING_NEW"))

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().also { it.start() }
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
        Dispatchers.resetMain()
    }

    private fun api() = ApiFactory.create(server.url("/api/v1").toString(), store)

    @Test fun pastedTextCarriesTheLabelsIntoTheDraft() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"suggested":$suggested}"""))
        val result = ApiTextStructurer(api()).structure("text") as StructureResult.Success
        val draft = draftFromStructured("text", result.suggestion)
        assertEquals(expected, draft.stepDetails)
        assertEquals(2, draft.ingredients.size)
    }

    @Test fun videoFetchCarriesTheLabelsIntoTheDraft() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"title":"T","channelName":"C","sourceUrl":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","suggested":$suggested}""",
        ))
        val result = ApiSourceFetcher(api()).fetch("https://youtu.be/dQw4w9WgXcQ") as FetchResult.Success
        assertEquals(expected, draftFromVideo(result.source).stepDetails)
    }

    @Test fun nullOrMissingLabelsAreNormal() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"suggested":{"ingredients":[],"steps":["a"],"stepDetails":null}}"""))
        server.enqueue(MockResponse().setBody("""{"suggested":{"ingredients":[],"steps":["a"]}}"""))
        repeat(2) {
            val result = ApiTextStructurer(api()).structure("a") as StructureResult.Success
            assertNull(result.suggestion.stepDetails)
        }
        assertNull(draftFromPaste("Method:\n- Stir.").stepDetails)
    }

    @Test fun savedRecipesReadWithAndWithoutLabels() {
        val with = ApiJson.decodeFromString<RecipeDto>(
            """{"id":"1","name":"R","steps":["a"],"stepDetails":[{"name":"A","ingredientIndexes":[],"illustration":"MIX"}]}""",
        ).toDomain()
        assertEquals(listOf(StepDetail("A", emptyList(), "MIX")), with.stepDetails)
        assertNull(ApiJson.decodeFromString<RecipeDto>("""{"id":"1","name":"R","stepDetails":null}""").toDomain().stepDetails)
        assertNull(ApiJson.decodeFromString<RecipeDto>("""{"id":"1","name":"R"}""").toDomain().stepDetails)
    }

    private fun input(details: List<StepDetail>?) = RecipeInput(
        "R", listOf(Ingredient("", "salt")), listOf("a"), emptyList(), SourceType.Pasted, null, stepDetails = details,
    )

    private fun sent(details: List<StepDetail>?) =
        ApiJson.parseToJsonElement(ApiJson.encodeToString(input(details).toRequest())).jsonObject["stepDetails"]

    @Test fun theRequestSendsTheLabelsOrNull() {
        val array = sent(listOf(StepDetail("A", listOf(0), "MIX"))) as JsonArray
        assertEquals(
            """{"name":"A","ingredientIndexes":[0],"illustration":"MIX"}""", array.jsonArray.single().toString(),
        )
        assertEquals(JsonNull, sent(null)) // the server reads null like a missing field
    }

    private val saved = """{"id":"r1","name":"R","ingredients":[{"quantity":"","item":"salt"}],"steps":["a","b"],
        "stepDetails":[{"name":"A","ingredientIndexes":[0],"illustration":"MIX"},
                       {"name":"B","ingredientIndexes":[],"illustration":"SERVE"}],
        "sourceType":"PASTED"}"""

    private fun draftWithLabels() = RecipeDraft(
        SourceType.Pasted, null, title = "R", ingredients = listOf(Ingredient("", "salt")), steps = listOf("a", "b"),
        stepDetails = listOf(StepDetail("A", listOf(0), "MIX"), StepDetail("B", emptyList(), "SERVE")),
    )

    /**
     * Drives the Review screen's ViewModel, from a new draft (editId null) or a saved recipe, and returns the
     * stepDetails it sends on Save.
     */
    private fun review(editId: String?, change: ReviewViewModel.() -> Unit): String = runBlocking {
        val api = api()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val recipes = ApiRecipeRepository(api, store, scope)
        val categories = ApiCategoryRepository(api, store, recipes, scope)
        val drafts = DraftHolder()
        if (editId == null) drafts.set(draftWithLabels()) else server.enqueue(MockResponse().setBody(saved))

        val vm = ReviewViewModel(recipes, categories, drafts, editId)
        withTimeout(20_000) { vm.state.first { it.ready } }
        if (editId != null) server.takeRequest() // the load
        vm.change()

        server.enqueue(MockResponse().setResponseCode(if (editId == null) 201 else 200).setBody(saved))
        val done = CompletableDeferred<Unit>()
        vm.save { done.complete(Unit) }
        withTimeout(20_000) { done.await() }
        val request = server.takeRequest()
        assertEquals(if (editId == null) "POST" else "PUT", request.method)
        ApiJson.parseToJsonElement(request.body.readUtf8()).jsonObject["stepDetails"].toString()
    }

    private val kept = """[{"name":"A","ingredientIndexes":[0],"illustration":"MIX"},""" +
        """{"name":"B","ingredientIndexes":[],"illustration":"SERVE"}]"""

    @Test fun newRecipeSavesTheSuggestedLabels() = assertEquals(kept, review(null) {})

    @Test fun newRecipeWithARemovedStepSavesNone() = assertEquals("null", review(null) { removeStep(0) })

    @Test fun newRecipeWithAnAddedStepSavesNone() = assertEquals("null", review(null) { setStepText("c"); addStep() })

    @Test fun newRecipeWithReorderedStepsSavesNone() = assertEquals("null", review(null) { useAsSteps("b\na") })

    @Test fun newRecipeWithARemovedIngredientSavesRenumberedLabels() =
        assertEquals(kept.replace("[0]", "[]"), review(null) { removeIngredient(0) })

    @Test fun editingKeepsTheLabelsWhileTheStepsAreUnchanged() = assertEquals(kept, review("r1") { setName("Renamed") })

    @Test fun editingTheStepsSendsNone() = assertEquals("null", review("r1") { removeStep(1) })
}
