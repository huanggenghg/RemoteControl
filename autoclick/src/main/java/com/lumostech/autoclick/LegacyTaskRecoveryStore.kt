package com.lumostech.autoclick

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LegacyTaskRecoveryStore(file: File) {
    constructor(context: Context) : this(File(context.applicationContext.filesDir, "legacy-task-recovery-session.json"))
    private val atomic = AtomicFile(file)
    private val lock = locks.getOrPut(file.absolutePath) { Any() }
    fun load(): LegacyTaskRecoverySession? = synchronized(lock) {
        if (!atomic.baseFile.exists() && !File(atomic.baseFile.path + ".bak").exists()) return null
        try {
            LegacyTaskRecoverySession.decode(atomic.readFully().toString(Charsets.UTF_8))
                ?: throw RecoveryStorageException()
        } catch (failure: Exception) { throw RecoveryStorageException(failure) }
    }
    fun save(session: LegacyTaskRecoverySession): Boolean = synchronized(lock) { writeCheckedAtomic(atomic, session.encode()) }
    fun clear(): Boolean = synchronized(lock) {
        atomic.delete()
        listOf("", ".new", ".bak").none { File(atomic.baseFile.path + it).exists() }
    }
    companion object { private val locks = ConcurrentHashMap<String, Any>() }
}
