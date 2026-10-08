package com.lumostech.remotecontrol.protocol

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

object RemoteCommandCodec {
    private val gson = Gson()
    private val actions = setOf("click", "scrollUp", "scrollDown", "scrollLeft", "scrollRight",
        "softInput", "back", "home", "recents")

    fun encode(command: RemoteCommand): String {
        val json = gson.toJsonTree(command).asJsonObject
        if (command.action == "softInput") {
            json.remove("inputText")
            // Bound JSON expansion even for quotes, control characters and HTML-sensitive text.
            json.addProperty("inputTextBase64", command.inputText.toByteArray(Charsets.UTF_8).toByteString().base64())
        }
        val encoded = gson.toJson(json)
        require(decode(encoded) == command) { "Invalid remote command" }
        return encoded
    }

    fun decode(encoded: String): RemoteCommand? = try {
        val json = JsonParser.parseString(encoded).asJsonObject
        val action = json.get("action")?.asString
        val inputText = decodeInput(json)
        if (action !in actions || inputText == null) null else {
            val command = RemoteCommand(action!!,
                json.get("x")?.asFloat ?: 0f, json.get("y")?.asFloat ?: 0f,
                json.get("distance")?.asFloat ?: 200f, json.get("duration")?.asLong ?: 300L,
                inputText)
            when {
                action == "click" && (!json.has("x") || !json.has("y") ||
                    !command.x.isFinite() || !command.y.isFinite() ||
                    command.x !in 0f..1f || command.y !in 0f..1f) -> null
                action.startsWith("scroll") && (!command.distance.isFinite() ||
                    command.distance !in 1f..10000f || command.duration !in 1L..10000L) -> null
                action == "softInput" && ((!json.has("inputText") && !json.has("inputTextBase64")) ||
                    command.inputText.toByteArray(Charsets.UTF_8).size > 4096) -> null
                else -> command
            }
        }
    } catch (_: Exception) { null }

    private fun decodeInput(json: JsonObject): String? {
        if (!json.has("inputTextBase64")) return json.get("inputText")?.asString ?: ""
        if (json.has("inputText")) return null
        val encoded = json.get("inputTextBase64").asString
        val bytes = encoded.decodeBase64()?.toByteArray() ?: return null
        if (bytes.size > 4096 || bytes.toByteString().base64() != encoded) return null
        val decoded = bytes.toString(Charsets.UTF_8)
        return decoded.takeIf { it.toByteArray(Charsets.UTF_8).contentEquals(bytes) }
    }
}
