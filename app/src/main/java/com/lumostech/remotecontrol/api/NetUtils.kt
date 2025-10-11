package com.lumostech.remotecontrol.api

import android.util.Log
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object NetUtils {
    private const val HOST_URL = "http://dachitech.xyz:8088"
    private var getZegoTokenService: GetZegoTokenService

    init {
        val client = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(HOST_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
        getZegoTokenService = retrofit.create(GetZegoTokenService::class.java)
    }

    fun getZegoToken(
        userId: String,
        loginRoomId: String
    ) : Flow<ZegoToken> = flow {
        val params = JSONObject().put("userId", userId).put("loginRoomId", loginRoomId)
        val zegoToken = getZegoTokenService.getZegoToken(buildRequestBody(params))
        emit(zegoToken)
    }.catch { e-> Log.e("NET", e.message ?: "exception unknown.") }

    private fun buildRequestBody(params: JSONObject): RequestBody? =
        RequestBody.create("application/json".toMediaTypeOrNull(), params.toString())
}