package com.adchain.sdk.network.models.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// 서버 LoginDto 구조에 맞춤
@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "userId")
    val userId: String,
    
    @Json(name = "gender")
    val gender: String? = null,  // M, F, O 또는 null
    
    @Json(name = "birthYear")
    val birthYear: String? = null,  // birthYear를 string으로 (e.g., "1990")
    
    @Json(name = "loginInfo")
    val loginInfo: LoginInfo,
    
    @Json(name = "deviceInfo")
    val deviceInfo: DeviceInfo
)

// iOS의 LoginInfo와 완전히 동일한 구조
@JsonClass(generateAdapter = true)
data class LoginInfo(
    @Json(name = "name")
    val name: String,
    
    @Json(name = "sdkVersion")
    val sdkVersion: String,
    
    @Json(name = "timestamp")
    val timestamp: String,
    
    @Json(name = "sessionId")
    val sessionId: String,
    
    @Json(name = "userId")
    val userId: String?,
    
    @Json(name = "deviceId")
    val deviceId: String,
    
    @Json(name = "ifa")
    val ifa: String?,
    
    @Json(name = "platform")
    val platform: String,
    
    @Json(name = "osVersion")
    val osVersion: String,
    
    @Json(name = "parameters")
    val parameters: Map<String, String>? = null
)

// DeviceInfo는 이미 ValidateAppRequest에 정의되어 있으므로 재사용