package com.adchain.sdk.network

import com.adchain.sdk.network.models.request.LoginRequest
import com.adchain.sdk.network.models.request.TrackEventRequest
import com.adchain.sdk.network.models.request.ValidateAppRequest
import com.adchain.sdk.network.models.response.BannerInfoResponse
import com.adchain.sdk.network.models.response.LoginResponse
import com.adchain.sdk.network.models.response.ValidateAppResponse
import com.adchain.sdk.quiz.models.QuizResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    
    @GET("v1/api/sdk/validate")
    suspend fun validateApp(): Response<ValidateAppResponse>
    
    @POST("v1/api/sdk/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>
    
    @POST("v1/api/sdk/event")  // 변경: v1/api/analytics/event -> v1/api/sdk/event
    suspend fun trackEvent(
        @Body request: TrackEventRequest
    ): Response<Unit>
    
    // Mission endpoints
    @GET("v1/api/mission")
    suspend fun getMissions(
        @Query("userId") userId: String? = null,
        @Query("platform") platform: String? = null,
        @Query("ifa") ifa: String? = null
    ): Response<com.adchain.sdk.mission.MissionResponse>
    
    // Quiz endpoints
    @GET("v1/api/quiz")
    suspend fun getQuizEvents(
        @Query("userId") userId: String? = null,
        @Query("platform") platform: String? = null,
        @Query("ifa") ifa: String? = null
    ): Response<QuizResponse>
    
    // Banner endpoints
    @GET("v1/api/sdk/banner")
    suspend fun getBannerInfo(
        @Query("userId") userId: String,
        @Query("placementId") placementId: String,
        @Query("platform") platform: String
    ): Response<BannerInfoResponse>
}