package com.tindi.app

import com.tindi.app.data.ApiSourceFetcher
import com.tindi.app.data.Confidence
import com.tindi.app.data.FetchFailure
import com.tindi.app.data.FetchResult
import com.tindi.app.data.FoundIn
import com.tindi.app.data.SourceType
import com.tindi.app.data.remote.ApiError
import com.tindi.app.data.remote.ApiFactory
import com.tindi.app.data.remote.InMemorySessionStore
import com.tindi.app.data.remote.StoredSession
import com.tindi.app.data.toFetchFailure
import com.tindi.app.domain.draftFromVideo
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

class SourceFetcherTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var fetcher: ApiSourceFetcher

    private val ok = """{
      "title":"Paneer Butter Masala","channelName":"Tindi Test Kitchen",
      "thumbnailUrl":"https://i.ytimg.com/vi/abc/maxresdefault.jpg",
      "sourceUrl":"https://www.youtube.com/watch?v=dQw4w9WgXcQ","sourceType":"YOUTUBE",
      "description":"Ingredients:\n- 250 g paneer\n- 2 tbsp butter\nMethod:\n1. Melt\n2. Add",
      "comments":[{"author":"Tindi","text":"Use kasuri methi","likeCount":12,"isPinnedLikely":true},
                  {"author":"Fan","text":"Loved it","likeCount":0,"isPinnedLikely":false}],
      "suggested":{"ingredients":["250 g paneer","2 tbsp butter","salt"],"steps":["Melt the butter","Add the paneer"],
                   "confidence":"HIGH","foundIn":"DESCRIPTION"}}"""

    private fun err(status: Int, code: String) = MockResponse().setResponseCode(status).setBody(
        """{"error":{"code":"$code","message":"server text for $code"}}""",
    )

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        store = InMemorySessionStore(StoredSession("tok", "Ravi", "r@x.co"))
        fetcher = ApiSourceFetcher(ApiFactory.create(server.url("/api/v1").toString(), store))
    }

    @After fun tearDown() { runCatching { server.shutdown() } }

    private fun failure(r: FetchResult) = (r as FetchResult.Failed).reason

    @Test fun successSendsTheLinkWithTheBearerTokenAndMapsEverything() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(ok))
        val r = fetcher.fetch("  https://youtu.be/dQw4w9WgXcQ?si=x ") as FetchResult.Success

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v1/source/fetch", req.path)
        assertEquals("Bearer tok", req.getHeader("Authorization"))
        assertTrue(req.body.readUtf8().contains(""""url":"https://youtu.be/dQw4w9WgXcQ?si=x""""))

        val s = r.source
        assertEquals("Paneer Butter Masala", s.title)
        assertEquals("Tindi Test Kitchen", s.channel)
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", s.thumbnailUrl)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", s.url)
        assertEquals(2, s.comments.size)
        assertTrue(s.comments[0].isPinnedLikely)
        assertEquals(12, s.comments[0].likeCount)
        assertEquals(Confidence.High, s.confidence)
        assertEquals(FoundIn.Description, s.foundIn)
        assertEquals(listOf("Melt the butter", "Add the paneer"), s.suggestedSteps)
    }

    @Test fun draftIsPrefilledFromTheSuggestionAndKeepsTheVideoForTheHelperPanel() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(ok))
        val d = draftFromVideo((fetcher.fetch("https://youtu.be/dQw4w9WgXcQ") as FetchResult.Success).source)
        assertEquals(SourceType.YouTube, d.sourceType)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", d.sourceUrl)
        assertEquals("Paneer Butter Masala", d.title)
        assertEquals("250 g", d.ingredients[0].quantity)
        assertEquals("paneer", d.ingredients[0].item)
        assertEquals("", d.ingredients[2].quantity) // "salt"
        assertEquals("salt", d.ingredients[2].item)
        assertEquals(2, d.steps.size)
        assertNull(d.sourceText)
        assertNotNull(d.video)
        assertEquals(2, d.video!!.comments.size)
        assertTrue(d.video!!.description.startsWith("Ingredients:"))
    }

    @Test fun emptySuggestionLeavesTheListsEmptyForTheUserToFill() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"title":"Vlog","channelName":"C","thumbnailUrl":null,"sourceUrl":"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
               "sourceType":"YOUTUBE","description":"hi","comments":[],
               "suggested":{"ingredients":[],"steps":[],"confidence":"NONE","foundIn":"NONE"}}""",
        ))
        val src = (fetcher.fetch("https://youtu.be/dQw4w9WgXcQ") as FetchResult.Success).source
        val d = draftFromVideo(src)
        assertTrue(d.ingredients.isEmpty())
        assertTrue(d.steps.isEmpty())
        assertEquals(Confidence.None, src.confidence)
        assertEquals(FoundIn.None, src.foundIn)
        assertNull(src.thumbnailUrl)
    }

    @Test fun nonYouTubeLinksNeverTouchTheNetwork() = runBlocking {
        for (url in listOf("https://www.instagram.com/reel/abc/", "https://vimeo.com/1", "not a link", "", "https://evil.com/youtube.com/watch")) {
            assertEquals(url, FetchResult.NotYouTube, fetcher.fetch(url))
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun serverRejectingTheLinkIsTreatedAsNotYouTube() = runBlocking {
        server.enqueue(err(400, "VALIDATION_FAILED"))
        assertEquals(FetchResult.NotYouTube, fetcher.fetch("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"))
    }

    @Test fun notFoundOrPrivate() = runBlocking {
        server.enqueue(err(404, "NOT_FOUND"))
        val f = failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(FetchFailure.NotFound, f)
        assertEquals("Video not found or private", f.message)
        assertFalse(f.canRetry)
    }

    @Test fun quotaExceeded() = runBlocking {
        server.enqueue(err(429, "SOURCE_QUOTA_EXCEEDED"))
        val f = failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(FetchFailure.QuotaExceeded, f)
        assertEquals("Daily fetch limit reached, try again tomorrow or paste the text", f.message)
    }

    @Test fun unavailableAndNotConfiguredShareOneMessage() = runBlocking {
        server.enqueue(err(502, "SOURCE_UNAVAILABLE"))
        server.enqueue(err(503, "SOURCE_NOT_CONFIGURED"))
        val a = failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ"))
        val b = failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(FetchFailure.Unavailable, a)
        assertEquals(FetchFailure.Unavailable, b)
        assertEquals("Fetching isn't available right now, paste the text instead", a.message)
    }

    @Test fun unreachableServerSaysCantReach() = runBlocking {
        server.shutdown()
        val f = failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(FetchFailure.Network, f)
        assertEquals("Can't reach server", f.message)
        assertTrue(f.canRetry)
    }

    @Test fun anyOtherServerProblemIsHandledWithoutCrashing() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>boom</html>"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        assertEquals(FetchFailure.Unavailable, failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ")))
        assertEquals(FetchFailure.Other, failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ")))
    }

    @Test fun expiredSessionEndsTheSessionLikeEverywhereElse() = runBlocking {
        server.enqueue(err(401, "UNAUTHORIZED"))
        failure(fetcher.fetch("https://youtu.be/dQw4w9WgXcQ"))
        assertNull(store.session.value)
    }

    @Test fun errorCodeMapping() {
        fun map(status: Int, code: String?) = ApiError.parse(
            status, if (code == null) null else """{"error":{"code":"$code","message":"x"}}""",
        ).toFetchFailure()
        assertEquals(FetchFailure.NotFound, map(404, "NOT_FOUND"))
        assertEquals(FetchFailure.QuotaExceeded, map(429, "SOURCE_QUOTA_EXCEEDED"))
        assertEquals(FetchFailure.Unavailable, map(502, "SOURCE_UNAVAILABLE"))
        assertEquals(FetchFailure.Unavailable, map(503, "SOURCE_NOT_CONFIGURED"))
        assertEquals(FetchFailure.Network, ApiError.network().toFetchFailure())
        assertNull(map(400, "VALIDATION_FAILED"))
        assertEquals(FetchFailure.Other, map(418, null))
    }

    @Test fun youTubeLinkCheckMatchesWhatTheServerAccepts() {
        for (ok in listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "youtu.be/dQw4w9WgXcQ", "https://m.youtube.com/shorts/dQw4w9WgXcQ")) {
            assertTrue(ok, ApiSourceFetcher.isYouTubeUrl(ok))
        }
        for (bad in listOf("https://instagram.com/reel/x", "https://notyoutube.com/watch?v=1", "hello")) {
            assertFalse(bad, ApiSourceFetcher.isYouTubeUrl(bad))
        }
    }
}
