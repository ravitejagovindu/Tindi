package com.tindi.app

import com.tindi.app.data.Recipe
import com.tindi.app.data.RecipeInput
import com.tindi.app.data.SourceType
import com.tindi.app.data.remote.RecipeDto
import com.tindi.app.data.remote.toDomain
import com.tindi.app.data.remote.toRequest
import com.tindi.app.domain.thumbnail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShareAndThumbnailTest {
    private fun recipe(type: SourceType, url: String?, thumb: String? = null) =
        Recipe("1", "R", emptyList(), emptyList(), emptyList(), type, url, thumb)

    @Test fun savedThumbnailWins() {
        val r = recipe(SourceType.YouTube, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://i.ytimg.com/vi/x/maxresdefault.jpg")
        assertEquals("https://i.ytimg.com/vi/x/maxresdefault.jpg", r.thumbnail)
    }

    @Test fun oldYouTubeRecipesGetOneBuiltFromTheVideoId() {
        for (u in listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ?si=a",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ", "https://m.youtube.com/watch?feature=x&v=dQw4w9WgXcQ",
        )) assertEquals(u, "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", recipe(SourceType.YouTube, u).thumbnail)
    }

    @Test fun pastedRecipesAndNonHttpsHaveNoThumbnail() {
        assertNull(recipe(SourceType.Pasted, null).thumbnail)
        assertNull(recipe(SourceType.Pasted, "https://youtu.be/dQw4w9WgXcQ").thumbnail)
        assertNull(recipe(SourceType.YouTube, "https://example.com/video").thumbnail)
        assertNull(recipe(SourceType.Pasted, null, "http://insecure/x.jpg").thumbnail)
    }

    @Test fun thumbnailTravelsBothWays() {
        val dto = RecipeDto(id = "1", name = "R", sourceType = "YOUTUBE", sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
            thumbnailUrl = "https://i.ytimg.com/vi/a/hq.jpg")
        assertEquals("https://i.ytimg.com/vi/a/hq.jpg", dto.toDomain().thumbnailUrl)
        val req = RecipeInput("R", emptyList(), emptyList(), emptyList(), SourceType.YouTube, "u", "https://i.ytimg.com/vi/a/hq.jpg").toRequest()
        assertEquals("https://i.ytimg.com/vi/a/hq.jpg", req.thumbnailUrl)
        assertNull(RecipeInput("R", emptyList(), emptyList(), emptyList(), SourceType.Pasted, null, " ").toRequest().thumbnailUrl)
    }

    @Test fun sharedYouTubeTextBecomesALink() {
        val s = SharedContent()
        s.offer("Best dal tadka https://youtu.be/dQw4w9WgXcQ?si=abc")
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc", s.item.value?.link)
        assertNull(s.item.value?.text)
        s.offer("https://www.youtube.com/watch?v=dQw4w9WgXcQ).")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", s.item.value?.link)
    }

    @Test fun sharedPlainTextGoesToThePasteBoxAndEmptyIsIgnored() {
        val s = SharedContent()
        s.offer("Ingredients\n2 eggs")
        assertNull(s.item.value?.link)
        assertEquals("Ingredients\n2 eggs", s.item.value?.text)
        s.clear()
        assertNull(s.item.value)
        s.offer("   ")
        s.offer(null)
        assertNull(s.item.value)
        assertNotNull(SharedContent().item)
    }
}
