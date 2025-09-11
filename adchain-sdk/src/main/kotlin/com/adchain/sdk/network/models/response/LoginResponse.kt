package com.adchain.sdk.network.models.response

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// iOS의 LoginResponse와 완전히 동일한 구조
@JsonClass(generateAdapter = true)
data class LoginResponse(
    @Json(name = "success")
    val success: Boolean,
    
    @Json(name = "message")
    val message: String? = null,
    
    @Json(name = "user")
    val user: UserData? = null
)

@JsonClass(generateAdapter = true)
data class UserData(
    @Json(name = "userId")
    val userId: String,
    
    @Json(name = "status")
    val status: String? = null,
    
    @Json(name = "createdAt")
    val createdAt: String? = null,
    
    @Json(name = "updatedAt")
    val updatedAt: String? = null
)