package com.lumostech.remotecontrol.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface GetZegoTokenService {
    @POST
    suspend fun getZegoToken(@Url endpoint: String, @Body request: ZegoTokenRequest): Response<ZegoToken>
}
