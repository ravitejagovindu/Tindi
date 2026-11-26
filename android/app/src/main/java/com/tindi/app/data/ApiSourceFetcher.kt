package com.tindi.app.data

import com.tindi.app.data.remote.ApiError
import com.tindi.app.data.remote.ApiResult
import com.tindi.app.data.remote.FetchRequest
import com.tindi.app.data.remote.FetchResponseDto
import com.tindi.app.data.remote.TindiApi
import com.tindi.app.data.remote.apiCall

/** Maps a server error from POST /source/fetch onto what the user is told. Null means "not a YouTube link". */
fun ApiError.toFetchFailure(): FetchFailure? = when {
    isNetwork -> FetchFailure.Network
    code == "VALIDATION_FAILED" -> null
    code == "NOT_FOUND" -> FetchFailure.NotFound
    code == "SOURCE_QUOTA_EXCEEDED" -> FetchFailure.QuotaExceeded
    code == "SOURCE_UNAVAILABLE" || code == "SOURCE_NOT_CONFIGURED" -> FetchFailure.Unavailable
    kind == ApiError.Kind.Server -> FetchFailure.Unavailable
    else -> FetchFailure.Other
}

internal fun FetchResponseDto.toVideoSource() = VideoSource(
    url = sourceUrl,
    title = title,
    channel = channelName,
    thumbnailUrl = thumbnailUrl,
    description = description,
    comments = comments.map { SourceComment(it.author, it.text, it.likeCount, it.isPinnedLikely) },
    suggestedIngredients = suggested.ingredients,
    suggestedSteps = suggested.steps,
    confidence = when (suggested.confidence.uppercase()) {
        "HIGH" -> Confidence.High
        "LOW" -> Confidence.Low
        else -> Confidence.None
    },
    foundIn = when (suggested.foundIn.uppercase()) {
        "DESCRIPTION" -> FoundIn.Description
        "COMMENT" -> FoundIn.Comment
        else -> FoundIn.None
    },
)

/** Asks the Tindi server (which uses the official YouTube Data API). Never throws. */
class ApiSourceFetcher(private val api: TindiApi) : SourceFetcher {
    override suspend fun fetch(url: String): FetchResult {
        val link = url.trim()
        // Obvious non-YouTube links are answered here, without a round trip.
        if (!isYouTubeUrl(link)) return FetchResult.NotYouTube
        return when (val r = apiCall { api.fetchSource(FetchRequest(link)) }) {
            is ApiResult.Ok -> FetchResult.Success(r.value.toVideoSource())
            is ApiResult.Fail -> r.error.toFetchFailure()?.let { FetchResult.Failed(it) } ?: FetchResult.NotYouTube
        }
    }

    companion object {
        // The server decides for real (watch, youtu.be, shorts); this only catches links that are clearly elsewhere.
        private val youTube = Regex(
            "^(https?://)?((www|m)\\.)?(youtube\\.com|youtu\\.be)/\\S+$",
            RegexOption.IGNORE_CASE,
        )

        fun isYouTubeUrl(url: String): Boolean = youTube.matches(url.trim())
    }
}
