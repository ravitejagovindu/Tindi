package com.tindi.app

import androidx.lifecycle.viewModelScope
import com.tindi.app.data.ApiTextStructurer
import com.tindi.app.data.Confidence
import com.tindi.app.data.FetchResult
import com.tindi.app.data.Method
import com.tindi.app.data.RecipeDraft
import com.tindi.app.data.SourceFetcher
import com.tindi.app.data.SourceType
import com.tindi.app.data.StructureResult
import com.tindi.app.data.TextStructurer
import com.tindi.app.data.remote.ApiFactory
import com.tindi.app.data.remote.InMemorySessionStore
import com.tindi.app.data.remote.StoredSession
import com.tindi.app.domain.draftFromPaste
import com.tindi.app.ui.screens.AddPhase
import com.tindi.app.ui.screens.AddRecipeViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Pasted text: sorted by the server when possible, by the phone otherwise. */
@OptIn(ExperimentalCoroutinesApi::class)
class PasteFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var structurer: TextStructurer

    private val text = "Ingredients:\n- 2 eggs\n- salt\nMethod:\n- Beat the eggs.\n- Fry them."

    private val drafts = mutableListOf<RecipeDraft>()
    private var opened = 0

    private val noFetch = object : SourceFetcher {
        override suspend fun fetch(url: String): FetchResult = error("not used")
    }

    private fun viewModel(s: TextStructurer = structurer) = AddRecipeViewModel(noFetch, s) { drafts += it }

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().also { it.start() }
        store = InMemorySessionStore(StoredSession("tok", "Ravi", "r@x.co"))
        structurer = ApiTextStructurer(ApiFactory.create(server.url("/api/v1").toString(), store))
    }

    @After fun tearDown() {
        runCatching { server.shutdown() }
        Dispatchers.resetMain()
    }

    /** Pastes [pasted], taps "Use this text" and waits until the screen is no longer loading. */
    private fun paste(pasted: String = text): AddRecipeViewModel = runBlocking {
        val vm = viewModel()
        vm.pasteInstead()
        vm.setPasted(pasted)
        vm.usePasted { opened++ }
        // Wait for the whole job: the screen leaves Loading just before the draft is handed over.
        withTimeout(20_000) { vm.viewModelScope.coroutineContext.job.children.toList().forEach { it.join() } }
        assertEquals(AddPhase.Ready, vm.state.value.phase)
        vm
    }

    private val localDraft get() = draftFromPaste(text)

    @Test fun serverSuggestionBecomesTheDraft() {
        server.enqueue(MockResponse().setBody(
            """{"suggested":{"ingredients":["2 cups rice","salt"],"steps":["Wash the rice."],"confidence":"LOW",
               "foundIn":"PASTED","method":"LLM","warnings":["Check step 1: it is worded differently from the source text."]}}""",
        ))
        paste("  $text \n")

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/source/structure", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertEquals("""{"text":"${text.replace("\n", "\\n")}"}""", req.body.readUtf8())

        val d = drafts.single()
        assertEquals(1, opened)
        assertEquals(SourceType.Pasted, d.sourceType)
        assertNull(d.sourceUrl)
        assertEquals(text, d.sourceText)
        assertEquals("2 cups", d.ingredients[0].quantity)
        assertEquals("rice", d.ingredients[0].item)
        assertEquals(listOf("Wash the rice."), d.steps)
        assertEquals(Method.Llm, d.suggestion!!.method)
        assertEquals(Confidence.Low, d.suggestion!!.confidence)
        assertEquals(1, d.suggestion!!.warnings.size)
    }

    @Test fun noRecipeFoundStaysEmptyAndIsNotResplitOnThePhone() {
        server.enqueue(MockResponse().setBody(
            """{"suggested":{"ingredients":[],"steps":[],"confidence":"NONE","foundIn":"NONE","method":"LLM","warnings":[]}}""",
        ))
        paste()
        val d = drafts.single()
        assertEquals(0, d.ingredients.size)
        assertEquals(0, d.steps.size)
        assertEquals(Confidence.None, d.suggestion!!.confidence)
        assertEquals(text, d.sourceText)
    }

    private fun assertFellBack() {
        assertEquals(listOf(localDraft), drafts)
        assertNull(drafts.single().suggestion) // so Review shows no banner
        assertEquals(listOf("Beat the eggs.", "Fry them."), drafts.single().steps)
        assertEquals(1, opened)
    }

    @Test fun backendOffFallsBackToTheLocalSplitter() {
        server.shutdown()
        paste()
        assertFellBack()
    }

    @Test fun droppedConnectionFallsBack() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        paste()
        assertFellBack()
    }

    private fun fallsBackOn(response: MockResponse) {
        server.enqueue(response)
        paste()
        assertFellBack()
    }

    @Test fun serverErrorFallsBack() = fallsBackOn(MockResponse().setResponseCode(500).setBody("<html>boom</html>"))

    @Test fun olderServerWithoutTheEndpointFallsBack() = fallsBackOn(
        MockResponse().setResponseCode(404).setBody("""{"error":{"code":"NOT_FOUND","message":"x"}}"""),
    )

    @Test fun rejectedTextFallsBack() = fallsBackOn(
        MockResponse().setResponseCode(400).setBody("""{"error":{"code":"VALIDATION_FAILED","message":"x"}}"""),
    )

    @Test fun unreadableAnswerFallsBack() = fallsBackOn(MockResponse().setBody("not json"))

    @Test fun answerWithoutASuggestionFallsBack() = fallsBackOn(MockResponse().setBody("{}"))

    @Test fun expiredSessionEndsTheSessionAndMakesNoDraft() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"UNAUTHORIZED","message":"x"}}"""))
        paste()
        assertNull(store.session.value)
        assertEquals(0, drafts.size)
        assertEquals(0, opened)
    }

    @Test fun blankTextAsksForTextAndCallsNothing() {
        val vm = viewModel()
        vm.pasteInstead()
        vm.setPasted("  \n ")
        vm.usePasted { opened++ }
        assertNotNull(vm.state.value.pastedError)
        assertEquals(0, server.requestCount)
        assertEquals(0, drafts.size)
    }

    /** Answers only when the test says so. */
    private class Held : TextStructurer {
        val answer = CompletableDeferred<StructureResult>()
        var calls = 0
        override suspend fun structure(text: String): StructureResult {
            calls++
            return answer.await()
        }
    }

    private fun loading(held: Held) = viewModel(held).also {
        it.pasteInstead()
        it.setPasted(text)
        it.usePasted { opened++ }
        assertEquals(AddPhase.Loading, it.state.value.phase)
    }

    @Test fun showsLoadingAndIgnoresASecondTap() {
        val held = Held()
        val vm = loading(held)
        vm.usePasted { opened++ }
        assertEquals(1, held.calls)
        held.answer.complete(StructureResult.Unavailable)
        assertEquals(AddPhase.Ready, vm.state.value.phase)
        assertEquals(1, opened)
    }

    @Test fun cancelKeepsTheTextAndMakesNoDraft() {
        val held = Held()
        val vm = loading(held)
        vm.cancel()
        held.answer.complete(StructureResult.Unavailable) // arrives too late to matter
        assertEquals(AddPhase.Ready, vm.state.value.phase)
        assertEquals(text, vm.state.value.pasted)
        assertEquals(true, vm.state.value.pasteMode)
        assertEquals(0, drafts.size)
        assertEquals(0, opened)
    }

    @Test fun leavingThePasteBoxCancelsTheCall() {
        val held = Held()
        val vm = loading(held)
        vm.backToLink()
        held.answer.complete(StructureResult.Unavailable)
        assertEquals(AddPhase.Ready, vm.state.value.phase)
        assertEquals(0, drafts.size)
    }

    @Test fun cancelStopsARealRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val vm = viewModel()
        vm.pasteInstead()
        vm.setPasted(text)
        vm.usePasted { opened++ }
        assertNotNull(server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS))
        vm.cancel()
        assertEquals(AddPhase.Ready, vm.state.value.phase)
        assertEquals(0, drafts.size)
        assertEquals(0, opened)
    }
}
