package com.lumostech.communication.agora

import org.junit.Assert.*
import org.junit.Test

class AgoraIdentityTest {
    @Test fun keepsUnsignedUidBitPatternAndPublicIdentity() {
        assertEquals(-1, AgoraIdentity.uid("4294967295"))
        assertEquals("4294967295", AgoraIdentity.peerId(-1))
    }
    @Test fun rejectsZeroOverflowAndNonCanonicalIdentity() {
        listOf("0", "4294967296", "-1", "name", "01", " 1").forEach { value ->
            assertTrue(runCatching { AgoraIdentity.uid(value) }.isFailure)
        }
    }
}
