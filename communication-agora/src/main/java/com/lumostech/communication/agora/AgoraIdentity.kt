package com.lumostech.communication.agora

internal object AgoraIdentity {
    fun uid(userId: String): Int {
        require(userId.matches(Regex("[1-9][0-9]{0,9}"))) { "Agora requires a numeric, nonzero UID" }
        val uid = userId.toLong()
        require(uid <= 0xFFFF_FFFFL) { "Agora UID exceeds unsigned 32-bit range" }
        return uid.toInt()
    }
    fun peerId(uid: Int): String = (uid.toLong() and 0xFFFF_FFFFL).toString()
}
