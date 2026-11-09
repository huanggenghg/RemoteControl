package com.lumostech.remotecontrol.api

import com.google.gson.Gson
import com.lumostech.communication.SessionRole
import com.lumostech.remotecontrol.communication.CredentialRequestException
import java.net.InetAddress
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ZegoTokenClientTest {
    private val loopbackHost = "127.0.0.1"
    private lateinit var server: MockWebServer
    @Before fun setUp() {
        server = MockWebServer()
        server.start(InetAddress.getByName(loopbackHost), 0)
    }
    @After fun tearDown() { server.shutdown() }
    private fun response() = ZegoToken("04" + UUID.randomUUID(), ZegoError("SUCCESS", ""),
        "42", "123456", "test-user", System.currentTimeMillis() / 1000 + 600)
    private fun client(api: GetZegoTokenService? = null): ZegoTokenClient {
        val endpoint = server.url("/remotecontrol/getZegoToken").newBuilder().host(loopbackHost).build()
        return ZegoTokenClient(api ?: ZegoTokenClient.createService(endpoint), endpoint)
    }
    private suspend fun failure(client: ZegoTokenClient): CredentialRequestException {
        try { client.fetch("123456", "test-user", SessionRole.CONTROLLER) }
        catch (failure: CredentialRequestException) { return failure }
        throw AssertionError("expected credential failure")
    }
    @Test fun fullEndpointAndRequestIdentityArePreserved() = runBlocking {
        val expected = response()
        server.enqueue(MockResponse().setBody(Gson().toJson(expected)))
        val credential = client().fetch("123456", "test-user", SessionRole.CONTROLLER)
        val sent = server.takeRequest()
        assertEquals("/remotecontrol/getZegoToken", sent.path)
        val body = Gson().fromJson(sent.body.readUtf8(), ZegoTokenRequest::class.java)
        assertEquals("test-user", body.userId); assertEquals("123456", body.loginRoomId)
        assertEquals("42", credential.appId); assertEquals(expected.data, credential.token)
        assertFalse(expected.toString().contains(expected.data!!))
    }
    @Test fun redirectResponseDoesNotResendTokenRequest() = runBlocking {
        MockWebServer().use { destination ->
            destination.start()
            destination.enqueue(MockResponse().setBody(Gson().toJson(response())))
            server.enqueue(MockResponse().setResponseCode(307)
                .addHeader("Location", destination.url("/redirected").toString()))

            assertEquals("http_307", failure(client()).diagnosticCode)
            assertEquals(1, server.requestCount)
            assertEquals(0, destination.requestCount)
        }
    }
    @Test fun numericSuccessCodeIsAccepted() = runBlocking {
        server.enqueue(MockResponse().setBody(Gson().toJson(response().copy(error = ZegoError("0", "")))))
        assertEquals("42", client().fetch("123456", "test-user", SessionRole.CONTROLLER).appId)
    }
    @Test fun malformedJsonDoesNotRetry() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>invalid</html>"))
        assertEquals("invalid_response", failure(client()).diagnosticCode)
        assertEquals(1, server.requestCount)
    }
    @Test fun businessFailureDoesNotRetryOrExposeServerMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody(
            "{\"data\":\"\",\"error\":{\"code\":\"NOT_CONFIGURED\",\"message\":\"do-not-expose\"}}"))
        val error = failure(client())
        assertEquals("not_configured", error.diagnosticCode)
        assertFalse(error.userMessage.contains("do-not-expose"))
        assertEquals(1, server.requestCount)
    }
    @Test fun invalidAndChangedOrExpiredCredentialsAreRejected() = runBlocking {
        val good = response()
        for ((body, code) in listOf(
            good.copy(appId = null) to "invalid_response",
            good.copy(appId = "4294967296") to "invalid_response",
            good.copy(error = null) to "invalid_response",
            good.copy(expiresAt = null) to "invalid_response",
            good.copy(data = null) to "invalid_response",
            good.copy(userId = "another-user") to "identity_changed",
            good.copy(roomId = "654321") to "identity_changed",
            good.copy(expiresAt = 1) to "expired")) {
            server.enqueue(MockResponse().setBody(Gson().toJson(body)))
            assertEquals(code, failure(client()).diagnosticCode)
        }
    }
    @Test fun transientGatewayFailureRetriesOnce() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("gateway unavailable"))
        server.enqueue(MockResponse().setBody(Gson().toJson(response())))
        assertEquals("42", client().fetch("123456", "test-user", SessionRole.CONTROLLER).appId)
        assertEquals(2, server.requestCount)
    }
    @Test fun timeoutBecomesUserErrorWhileParentCancellationPropagates() = runTest {
        var requests = 0
        val api = object : GetZegoTokenService {
            override suspend fun getZegoToken(endpoint: String, request: ZegoTokenRequest): Response<ZegoToken> {
                requests++; awaitCancellation()
            }
        }
        assertEquals("timeout", failure(client(api)).diagnosticCode)
        assertEquals(1, requests)
        val job = launch { client(api).fetch("123456", "test-user", SessionRole.CONTROLLER) }
        yield(); job.cancelAndJoin(); assertTrue(job.isCancelled)
        assertEquals(2, requests)
    }
    @Test fun productionEndpointRejectsCredentialsQueryAndCleartext() {
        for (url in listOf("http://opub.cn/remotecontrol/getZegoToken",
            "https://opub.cn/remotecontrol/getZegoToken#fragment",
            "https://user:password@opub.cn/remotecontrol/getZegoToken",
            "https://opub.cn/remotecontrol/getZegoToken?key=value")) {
            try { ZegoTokenClient.create(url); fail("endpoint should be rejected") }
            catch (error: CredentialRequestException) { assertEquals("endpoint_config", error.diagnosticCode) }
        }
    }
    @Test fun networkFailureStopsAfterTwoAttempts() = runTest {
        var calls = 0
        val api = object : GetZegoTokenService {
            override suspend fun getZegoToken(endpoint: String, request: ZegoTokenRequest): Response<ZegoToken> {
                calls++
                throw java.io.IOException("do-not-expose")
            }
        }
        val error = failure(client(api))
        assertEquals("network", error.diagnosticCode)
        assertEquals(2, calls)
        assertFalse(error.toString().contains("do-not-expose"))
    }
}
