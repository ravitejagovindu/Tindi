package com.tindi.app.data

import com.tindi.app.data.remote.ApiError
import com.tindi.app.data.remote.ApiResult
import com.tindi.app.data.remote.StructureRequest
import com.tindi.app.data.remote.TindiApi
import com.tindi.app.data.remote.apiCall

/** Asks the Tindi server to sort pasted text. Never throws. */
class ApiTextStructurer(private val api: TindiApi) : TextStructurer {
    override suspend fun structure(text: String): StructureResult =
        when (val r = apiCall { api.structureText(StructureRequest(text)) }) {
            is ApiResult.Ok -> StructureResult.Success(r.value.suggested.toSuggestion())
            is ApiResult.Fail ->
                if (r.error.kind == ApiError.Kind.Unauthorized) StructureResult.SignedOut
                else StructureResult.Unavailable
        }
}
