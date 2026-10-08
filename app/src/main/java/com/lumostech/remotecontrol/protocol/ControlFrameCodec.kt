package com.lumostech.remotecontrol.protocol

import com.google.gson.Gson
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.util.UUID

class ControlFrameCodec(private val nowMs: () -> Long) {
    private data class Frame(val id: String, val index: Int, val count: Int, val data: String)
    private data class Pending(val deadline: Long, val parts: Array<ByteArray?>)
    private val pending = linkedMapOf<Pair<String, String>, Pending>()
    private val gson = Gson()

    fun encode(payload: ByteArray): List<ByteArray> {
        require(payload.isNotEmpty() && payload.size <= MAX_MESSAGE_BYTES)
        val parts = payload.asList().chunked(384)
        val id = UUID.randomUUID().toString()
        return parts.mapIndexed { index, part ->
            gson.toJson(Frame(id, index, parts.size, part.toByteArray().toByteString().base64()))
                .toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_FRAME_BYTES) }
        }
    }

    @Synchronized fun receive(peerId: String, bytes: ByteArray): ByteArray? {
        pending.entries.removeAll { it.value.deadline <= nowMs() }
        if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) return null
        return try {
            val frame = gson.fromJson(bytes.toString(Charsets.UTF_8), Frame::class.java)
            if (frame.id.length !in 1..64 || frame.count !in 1..24 || frame.index !in 0 until frame.count) return null
            val data = frame.data.decodeBase64()?.toByteArray() ?: return null
            if (data.size !in 1..384) return null
            val key = peerId to frame.id
            val assembly = pending[key] ?: run {
                if (pending.size >= 4) return null
                Pending(nowMs() + 2000, arrayOfNulls(frame.count)).also { pending[key] = it }
            }
            if (assembly.parts.size != frame.count) { pending.remove(key); return null }
            val existing = assembly.parts[frame.index]
            if (existing != null && !existing.contentEquals(data)) { pending.remove(key); return null }
            assembly.parts[frame.index] = data
            if (assembly.parts.any { it == null }) return null
            pending.remove(key)
            val length = assembly.parts.sumOf { it!!.size }
            if (length > MAX_MESSAGE_BYTES) return null
            ByteArray(length).also { result ->
                var offset = 0
                assembly.parts.forEach { part -> part!!.copyInto(result, offset); offset += part.size }
            }
        } catch (_: Exception) { null }
    }

    @Synchronized fun clear() = pending.clear()

    companion object {
        const val MAX_FRAME_BYTES = 900
        const val MAX_MESSAGE_BYTES = 8192
    }
}
