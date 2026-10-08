package com.lumostech.remotecontrol.communication

import com.lumostech.communication.SessionCredentials
import com.lumostech.communication.SessionRole

fun interface CredentialProvider {
    suspend fun fetch(roomId: String, userId: String, role: SessionRole): SessionCredentials
}

/** Install an application-owned credential source before starting a session. Never persist tokens here. */
object CommunicationCredentials {
    @Volatile var provider: CredentialProvider? = null
}
