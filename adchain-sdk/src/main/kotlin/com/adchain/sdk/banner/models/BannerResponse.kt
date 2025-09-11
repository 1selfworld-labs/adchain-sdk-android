package com.adchain.sdk.banner.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// 서버 응답과 일치하는 구조로 변경 - data 래핑 제거
@JsonClass(generateAdapter = true)
data class BannerResponse(
    @Json(name = "success")
    val success: Boolean? = null,
    
    @Json(name = "imageUrl")
    val imageUrl: String? = null,
    
    @Json(name = "titleText")
    val titleText: String? = null,
    
    @Json(name = "linkUrl")
    val linkUrl: String? = null,
    
    @Json(name = "message")
    val message: String? = null
)