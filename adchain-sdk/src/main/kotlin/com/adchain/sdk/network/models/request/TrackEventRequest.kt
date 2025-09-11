package com.adchain.sdk.network.models.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TrackEventRequest(
    @Json(name = "name")
    val name: String,  // Event name like 'ad_impression', 'ad_click', etc.
    
    @Json(name = "sdkVersion")
    val sdkVersion: String,  // SDK version
    
    @Json(name = "timestamp")
    val timestamp: String = System.currentTimeMillis().toString(),  // Unix timestamp in milliseconds as string
    
    @Json(name = "sessionId")
    val sessionId: String,  // Unique session identifier from SDK
    
    @Json(name = "userId")
    val userId: String? = null,  // Optional user identifier if available
    
    @Json(name = "deviceId")
    val deviceId: String,  // Unique device identifier
    
    @Json(name = "ifa")
    val ifa: String? = null,  // GAID (Google Advertising ID) for Android
    
    @Json(name = "platform")
    val platform: String = "Android",  // Operating system
    
    @Json(name = "osVersion")
    val osVersion: String,  // OS version (e.g., "11.0", "12.0")
    
    @Json(name = "parameters")
    val parameters: Map<String, String>? = null  // Additional event-specific data
)