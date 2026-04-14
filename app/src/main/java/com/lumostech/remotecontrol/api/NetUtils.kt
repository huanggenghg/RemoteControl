package com.lumostech.remotecontrol.api

import com.lumostech.remotecontrol.utils.Logger
import com.lumostech.remotecontrol.utils.retryIO
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object NetUtils {
    private const val TAG = "NetUtils"
    private const val HOST_URL = "http://dachitech.xyz:8088"
    private var getZegoTokenService: GetZegoTokenService

    init {
        val httpLoggingInterceptor = HttpLoggingInterceptor(HttpLogger()).apply {
            setLevel(
                HttpLoggingInterceptor.Level.BASIC
            )
        }
        val client = OkHttpClient.Builder().addInterceptor(httpLoggingInterceptor).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(HOST_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(CoroutineCallAdapterFactory())
            .build()
        getZegoTokenService = retrofit.create(GetZegoTokenService::class.java)
        Logger.i(TAG, "Retrofit 初始化完成 - $HOST_URL")
    }

    fun getZegoToken(
        userId: String,
        loginRoomId: String
    ): Flow<ZegoToken> = flow {
        Logger.d(TAG, "请求 ZegoToken - userId: $userId, roomId: $loginRoomId")
        
        // 使用 retryIO 包装网络请求
        val zegoToken = retryIO(maxRetries = 3, delayMillis = 1000) {
            getZegoTokenService.getZegoToken(buildRequestBody(userId, loginRoomId))
        }.getOrThrow()
        
        Logger.d(TAG, "ZegoToken 获取成功")
        emit(zegoToken)
    }.catch { e ->
        Logger.e(TAG, "获取 ZegoToken 失败", e)
        throw e // 重新抛出，让调用者处理
    }

    private fun buildRequestBody(userId: String, loginRoomId: String): RequestBody {
        val params = JSONObject()
            .put("userId", userId)
            .put("loginRoomId", loginRoomId)
        Logger.v(TAG, "请求参数: $params")
        return RequestBody.create("application/json".toMediaTypeOrNull(), params.toString())
    }
}