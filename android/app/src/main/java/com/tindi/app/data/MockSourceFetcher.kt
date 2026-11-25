package com.tindi.app.data

import kotlinx.coroutines.delay

/**
 * MOCK. No network. Replace with a class that calls the YouTube Data API
 * (videos.list for the snippet, commentThreads.list for the pinned and top comments).
 */
class MockSourceFetcher : SourceFetcher {
    override suspend fun fetch(url: String): FetchResult {
        val link = url.trim()
        if (link.contains("error", ignoreCase = true)) {
            delay(900)
            return FetchResult.Failed
        }
        if (!isYouTubeUrl(link)) return FetchResult.NotYouTube
        delay(900)
        return FetchResult.Success(
            VideoSource(
                url = link,
                title = "Restaurant Style Paneer Butter Masala",
                channel = "Tindi Test Kitchen",
                description = "Creamy, rich paneer butter masala that tastes like your favourite " +
                    "restaurant. Serve it with naan or jeera rice. Do not forget to like, share and " +
                    "subscribe for more recipes every week!",
                pinnedComment = "INGREDIENTS\n" +
                    "250 g paneer, cubed\n2 tbsp butter\n3 tomatoes, pureed\n1 onion, chopped\n" +
                    "1/2 cup cream\n1 tsp garam masala\n\n" +
                    "STEPS\n" +
                    "1. Melt 2 tbsp butter in a pan.\n" +
                    "2. Add the onion and cook until golden.\n" +
                    "3. Pour in the tomato puree and simmer for 8 minutes.\n" +
                    "4. Stir in the cream and garam masala.\n" +
                    "5. Add the paneer and cook for 3 minutes.",
                topComments = listOf(
                    "Made this last night and the whole family loved it. Thank you!",
                    "Tip: soak the paneer in warm water for 10 minutes so it stays soft.",
                ),
            ),
        )
    }

    companion object {
        private val youTube = Regex(
            "^(https?://)?((www|m|music)\\.)?(youtube\\.com|youtu\\.be)/\\S+$",
            RegexOption.IGNORE_CASE,
        )

        fun isYouTubeUrl(url: String): Boolean = youTube.matches(url.trim())
    }
}
