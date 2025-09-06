package com.adchain.sdk.network

import com.adchain.sdk.network.models.request.TrackEventRequest
import com.adchain.sdk.network.models.request.ValidateAppRequest
import com.adchain.sdk.network.models.response.ValidateAppResponse
import com.adchain.sdk.quiz.models.QuizResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    
    @POST("v1/api/sdk/validate")
    suspend fun validateApp(
        @Body request: ValidateAppRequest
    ): Response<ValidateAppResponse>
    
    @POST("v1/api/sdk/event")  // 변경: v1/api/analytics/event -> v1/api/sdk/event
    suspend fun trackEvent(
        @Body request: TrackEventRequest
    ): Response<Unit>
    
    // Mission endpoints
    @GET("v1/api/mission")
    suspend fun getMissions(): Response<com.adchain.sdk.mission.MissionResponse>
    
    // Quiz endpoints
    @GET("v1/api/quiz")
    suspend fun getQuizEvents(): Response<QuizResponse>
}