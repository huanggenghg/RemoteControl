package com.lumostech.autoclick

import org.json.JSONArray
import org.json.JSONObject

data class TaskPreferenceSnapshot(val transactionId: String, val previous: Map<String, Any>, val current: Map<String, Any>) {
    fun encode(): String = JSONObject().apply {
        require(transactionId.isNotBlank())
        put("version", 1)
        put("transactionId", transactionId)
        put("previous", encodeValues(previous))
        put("current", encodeValues(current))
    }.toString()

    companion object {
        fun decode(value: String): TaskPreferenceSnapshot? = runCatching {
            val json = JSONObject(value)
            require(json.getInt("version") == 1)
            TaskPreferenceSnapshot(json.getString("transactionId").also { require(it.isNotBlank()) },
                decodeValues(json.getJSONObject("previous")), decodeValues(json.getJSONObject("current")))
        }.getOrNull()

        private fun encodeValues(values: Map<String, Any>): JSONObject = JSONObject().apply {
            copyPreferenceValues(values).forEach { (key, value) ->
                val type = when (value) {
                    is String -> "string"
                    is Int -> "int"
                    is Long -> "long"
                    is Float -> "float"
                    is Boolean -> "boolean"
                    is Set<*> -> "set"
                    else -> error("Unsupported preference type")
                }
                put(key, JSONObject().put("type", type).put("value",
                    if (value is Set<*>) JSONArray(value.map { it as String }.sorted()) else value))
            }
        }

        private fun decodeValues(json: JSONObject): Map<String, Any> = json.keys().asSequence().associateWith { key ->
            val entry = json.getJSONObject(key)
            when (entry.getString("type")) {
                "string" -> entry.getString("value")
                "int" -> entry.getInt("value")
                "long" -> entry.getLong("value")
                "float" -> entry.getDouble("value").toFloat().also { require(it.isFinite()) }
                "boolean" -> entry.getBoolean("value")
                "set" -> entry.getJSONArray("value").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
                else -> error("Unsupported preference type")
            }
        }
    }
}

internal fun copyPreferenceValues(values: Map<String, *>): Map<String, Any> = values.mapValues { (_, value) ->
    when (value) {
        is String, is Int, is Long, is Boolean -> value
        is Float -> value.also { require(it.isFinite()) }
        is Set<*> -> value.map { require(it is String); it }.toSet()
        else -> error("Unsupported preference value")
    }
}
