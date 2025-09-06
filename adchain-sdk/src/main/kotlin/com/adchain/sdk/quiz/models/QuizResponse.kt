package com.adchain.sdk.quiz.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuizResponse(
    @Json(name = "success")
    val success: Boolean? = null,
    @Json(name = "events")
    val events: List<QuizEvent> = emptyList(),
    @Json(name = "message")
    val message: String? = null
)