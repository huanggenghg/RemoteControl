package com.lumostech.remotecontrol.api

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST


interface GetZegoTokenService {
    @POST("/getZegoToken")
    suspend fun getZegoToken(@Body body: RequestBody?): ZegoToken
}