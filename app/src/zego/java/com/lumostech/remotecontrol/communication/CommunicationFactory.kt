package com.lumostech.remotecontrol.communication

import android.content.Context
import com.lumostech.communication.*
import com.lumostech.communication.zego.ZegoCommunicationSession
import com.lumostech.remotecontrol.api.NetUtils
import kotlinx.coroutines.flow.first

object CommunicationFactory {
    fun create(context: Context, listener: CommunicationListener): CommunicationSession =
        ZegoCommunicationSession(context.applicationContext, listener)

    fun credentials(): CredentialProvider = CommunicationCredentials.provider ?: CredentialProvider { room, user, _ ->
        val response = NetUtils.getZegoToken(user, room).first()
        require(response.data.isNotBlank()) { "即构凭证为空" }
        SessionCredentials("678281271", room, user, response.data)
    }
}
