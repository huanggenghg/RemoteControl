package com.lumostech.autoclick

import android.content.SharedPreferences

/** Retains Android's memory mutations but reports a durable commit failure. */
internal class FailingCommitPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
    override fun edit(): SharedPreferences.Editor {
        val editor = delegate.edit()
        return object : SharedPreferences.Editor by editor {
            override fun putString(key: String?, value: String?) = apply { editor.putString(key, value) }
            override fun putLong(key: String?, value: Long) = apply { editor.putLong(key, value) }
            override fun putBoolean(key: String?, value: Boolean) = apply { editor.putBoolean(key, value) }
            override fun putInt(key: String?, value: Int) = apply { editor.putInt(key, value) }
            override fun putFloat(key: String?, value: Float) = apply { editor.putFloat(key, value) }
            override fun putStringSet(key: String?, values: Set<String>?) = apply { editor.putStringSet(key, values) }
            override fun remove(key: String?) = apply { editor.remove(key) }
            override fun clear() = apply { editor.clear() }
            override fun commit(): Boolean { editor.commit(); return false }
        }
    }
}
