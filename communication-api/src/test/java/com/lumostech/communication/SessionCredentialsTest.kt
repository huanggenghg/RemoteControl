package com.lumostech.communication

import org.junit.Assert.*
import org.junit.Test

class SessionCredentialsTest {
    @Test fun credentialsNeverExposeTokenInDiagnostics() {
        val credentials = SessionCredentials("app", "room", "user", "secret-token")
        assertFalse(credentials.toString().contains("secret-token"))
        assertTrue(credentials.toString().contains("room"))
    }
}
