package com.lumostech.remotecontrol.api

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.lumostech.communication.SessionCredentials
import com.lumostech.communication.SessionRole
import com.lumostech.remotecontrol.communication.CredentialProvider
import com.lumostech.remotecontrol.communication.CredentialRequestException
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ZegoTokenClient internal constructor(
    private val api: GetZegoTokenService,
    private val endpoint: HttpUrl,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 }
) : CredentialProvider {
    override suspend fun fetch(roomId: String, userId: String, role: SessionRole): SessionCredentials =
        withTimeoutOrNull(12_000) { request(roomId, userId) }
            ?: throw problem("凭证请求超时，请稍后重试", "timeout")

    private suspend fun request(room: String, user: String): SessionCredentials {
        for (attempt in 0..1) {
            try {
                val response = api.getZegoToken(endpoint.toString(), ZegoTokenRequest(user, room))
                if (!response.isSuccessful) {
                    val code = errorCode(response)
                    if (code != null) throw business(code)
                    if (response.code() in setOf(502, 503, 504) && attempt == 0) {
                        delay(250); continue
                    }
                    throw problem("凭证服务暂时不可用，请稍后重试", "http_${response.code()}")
                }
                return checked(response.body(), room, user)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: JsonParseException) {
                throw problem("凭证服务返回格式异常", "invalid_response")
            } catch (_: MalformedJsonException) {
                throw problem("凭证服务返回格式异常", "invalid_response")
            } catch (_: EOFException) {
                throw problem("凭证服务返回格式异常", "invalid_response")
            } catch (_: IOException) {
                if (attempt == 0) { delay(250); continue }
                throw problem("无法连接凭证服务，请检查网络后重试", "network")
            }
        }
        throw problem("无法连接凭证服务，请检查网络后重试", "network")
    }
    private fun checked(body: ZegoToken?, room: String, user: String): SessionCredentials {
        val checkedBody = body ?: throw problem("凭证服务返回不完整", "invalid_response")
        val code = checkedBody.error?.code ?: throw problem("凭证服务返回不完整", "invalid_response")
        if (code != "SUCCESS" && code != "0") throw business(code)
        val appId = checkedBody.appId?.toLongOrNull()
        val token = checkedBody.data
        val expiry = checkedBody.expiresAt
        if (appId == null || appId !in 1..4_294_967_295L || token.isNullOrBlank() ||
            !token.startsWith("04") || expiry == null || expiry <= 0) {
            throw problem("凭证服务返回不完整", "invalid_response")
        }
        if (checkedBody.roomId != room || checkedBody.userId != user) {
            throw problem("凭证身份与请求不一致", "identity_changed")
        }
        if (expiry <= nowSeconds()) throw problem("凭证服务返回的凭证已过期", "expired")
        return SessionCredentials(appId.toString(), room, user, token)
    }
    private fun errorCode(response: Response<ZegoToken>): String? {
        val body = response.errorBody() ?: return null
        return body.use {
            try { Gson().fromJson(it.charStream(), ZegoToken::class.java)?.error?.code }
            catch (_: IOException) { null }
            catch (_: JsonParseException) { null }
        }
    }
    private fun business(code: String) = when (code) {
        "NOT_CONFIGURED" -> problem("即构凭证服务尚未配置", "not_configured")
        "INVALID_REQUEST" -> problem("凭证请求参数无效", "invalid_request")
        "SIGNING_FAILED" -> problem("凭证签发失败，请稍后重试", "signing_failed")
        else -> problem("凭证服务拒绝了请求", "business_error")
    }
    private fun problem(message: String, code: String) = CredentialRequestException(message, code)

    companion object {
        fun create(endpoint: String): ZegoTokenClient {
            val url = endpoint.toHttpUrlOrNull()
            if (url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty() ||
                url.query != null || url.fragment != null) {
                throw CredentialRequestException("即构凭证接口地址无效，请检查配置", "endpoint_config")
            }
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
            val api = Retrofit.Builder().baseUrl(url.resolve("/")!!).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build()
                .create(GetZegoTokenService::class.java)
            return ZegoTokenClient(api, url)
        }
    }
}
