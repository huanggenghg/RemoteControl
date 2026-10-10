package com.lumostech.autoclick

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

interface RecoveryJournalStorage {
    fun read(): TaskPreferenceSnapshot?
    fun write(snapshot: TaskPreferenceSnapshot): Boolean
    fun clear(): Boolean
}
class AtomicRecoveryJournalStorage(private val file: File) : RecoveryJournalStorage {
    private val atomic = AtomicFile(file)
    private val lock = locks.getOrPut(file.absolutePath) { Any() }

    override fun read(): TaskPreferenceSnapshot? = synchronized(lock) {
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        val contents = try { atomic.readFully().toString(Charsets.UTF_8) }
        catch (failure: Exception) { throw RecoveryStorageException(failure) }
        TaskPreferenceSnapshot.decode(contents) ?: throw RecoveryStorageException()
    }

    override fun write(snapshot: TaskPreferenceSnapshot): Boolean = synchronized(lock) {
        writeCheckedAtomic(atomic, snapshot.encode())
    }

    override fun clear(): Boolean = synchronized(lock) {
        atomic.delete()
        listOf(file, File(file.path + ".new"), File(file.path + ".bak")).none { it.exists() }
    }

    companion object { private val locks = ConcurrentHashMap<String, Any>() }
}

class RecoveryStorageException(cause: Throwable? = null) : IOException("保存状态未确认，原任务与恢复记录已保留", cause)

/** AtomicFile can log a failed rename instead of throwing; sync and read back explicitly. */
internal fun writeCheckedAtomic(file: AtomicFile, contents: String): Boolean {
    var stream: FileOutputStream? = null
    return try {
        stream = file.startWrite()
        stream.write(contents.toByteArray(Charsets.UTF_8))
        stream.fd.sync()
        file.finishWrite(stream)
        stream = null
        file.readFully().toString(Charsets.UTF_8) == contents
    } catch (failure: Exception) {
        stream?.let { runCatching { file.failWrite(it) } }
        false
    }
}
