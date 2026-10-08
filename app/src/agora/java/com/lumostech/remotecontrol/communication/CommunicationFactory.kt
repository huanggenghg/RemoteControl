package com.lumostech.remotecontrol.communication

import android.content.Context
import com.lumostech.communication.*
import com.lumostech.communication.agora.AgoraCommunicationSession

object CommunicationFactory {
    fun create(context: Context, listener: CommunicationListener): CommunicationSession =
        AgoraCommunicationSession(context.applicationContext, listener)

    fun credentials(): CredentialProvider = CredentialProvider { room, user, role ->
        val provider = CommunicationCredentials.provider
            ?: throw MissingCredentialsException()
        provider.fetch(room, user, role)
    }
}
