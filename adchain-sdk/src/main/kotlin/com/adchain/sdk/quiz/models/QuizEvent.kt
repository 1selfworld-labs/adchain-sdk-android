package com.adchain.sdk.quiz.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class QuizEvent(
    @Json(name = "id")
    val id: String,
    @Json(name = "title")
    val title: String,
    @Json(name = "description")
    val description: String,
    @Json(name = "point")
    val point: String,
    @Json(name = "image_url")
    val imageUrl: String,
    @Json(name = "landing_url")
    val landingUrl: String,
    @Json(name = "metadata")
    val metadata: Map<String, Any>? = null
)