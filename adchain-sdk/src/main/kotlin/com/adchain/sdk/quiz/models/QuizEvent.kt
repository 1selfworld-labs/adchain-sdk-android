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
    val description: String? = null,  // iOS와 동일하게 nullable로 변경
    @Json(name = "imageUrl")  // snake_case에서 camelCase로 변경
    val imageUrl: String,
    @Json(name = "landingUrl")  // snake_case에서 camelCase로 변경
    val landingUrl: String,
    @Json(name = "point")
    val point: String,
    @Json(name = "status")
    val status: String? = null,  // iOS와 동일하게 추가
    @Json(name = "completed")
    val completed: Boolean? = null  // iOS와 동일하게 추가
)