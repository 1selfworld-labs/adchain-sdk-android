package com.adchain.sdk.quiz.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuizResponse(
    @Json(name = "success")
    val success: Boolean? = null,

    @Json(name = "titleText")
    val titleText: String? = null,

    @Json(name = "completedImageUrl")
    val completedImageUrl: String? = null,

    @Json(name = "completedImageWidth")
    val completedImageWidth: Int? = null,

    @Json(name = "completedImageHeight")
    val completedImageHeight: Int? = null,

    @Json(name = "events")
    val events: List<QuizEvent> = emptyList(),

    @Json(name = "message")
    val message: String? = null
)