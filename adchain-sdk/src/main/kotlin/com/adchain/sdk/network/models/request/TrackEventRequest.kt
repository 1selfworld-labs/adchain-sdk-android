package com.adchain.sdk.network.models.request

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TrackEventRequest(
    @Json(name = "name")
    val name: String,  // Event name like 'ad_impression', 'ad_click', etc.
    
    @Json(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),  // Unix timestamp in milliseconds
    
    @Json(name = "session_id")
    val sessionId: String,  // Unique session identifier from SDK
    
    @Json(name = "user_id")
    val userId: String? = null,  // Optional user identifier if available
    
    @Json(name = "device_id")
    val deviceId: String,  // Unique device identifier
    
    @Json(name = "advertising_id")
    val advertisingId: String? = null,  // GAID (Google Advertising ID) for Android
    
    @Json(name = "os")
    val os: String = "Android",  // Operating system
    
    @Json(name = "os_version")
    val osVersion: String,  // OS version (e.g., "11.0", "12.0")
    
    @Json(name = "parameters")
    val parameters: Map<String, Any>? = null  // Additional event-specific data
)