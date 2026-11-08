package com.lumostech.remotecontrol.communication

import android.content.Context
import com.lumostech.communication.*
import com.lumostech.communication.zego.ZegoCommunicationSession
import com.lumostech.remotecontrol.BuildConfig
import com.lumostech.remotecontrol.api.ZegoTokenClient

object CommunicationFactory {
    private val fallback = object : CredentialProvider {
        private val client by lazy { ZegoTokenClient.create(BuildConfig.ZEGO_TOKEN_ENDPOINT) }
        override suspend fun fetch(roomId: String, userId: String, role: SessionRole): SessionCredentials =
            client.fetch(roomId, userId, role)
    }
    fun create(context: Context, listener: CommunicationListener): CommunicationSession =
        ZegoCommunicationSession(context.applicationContext, listener)
    fun credentials(): CredentialProvider = CommunicationCredentials.provider ?: fallback
}
