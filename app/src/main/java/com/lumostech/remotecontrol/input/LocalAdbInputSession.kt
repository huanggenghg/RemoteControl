package com.lumostech.remotecontrol.input

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.lumostech.communication.ScreenGeometry
import com.lumostech.remotecontrol.protocol.RemoteCommand
import com.lumostech.remotecontrol.utils.Logger
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.LocalAdbPairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

/** Owns a single connection and serializes input for one screen sharing session. */
internal class LocalAdbInputSession {
    private data class PendingInput(val command: AdbInputCommand, val canExecute: () -> Boolean)

    private val inputs = Channel<PendingInput>(capacity = 4)

    fun submit(command: RemoteCommand, screen: ScreenGeometry, canExecute: () -> Boolean): Boolean {
        val input = AdbInputCommand.from(command, screen) ?: return false
        return inputs.trySend(PendingInput(input, canExecute)).isSuccess
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun run(port: Int, onConnected: () -> Unit) {
        val connection = withContext(Dispatchers.IO) { newConnection() }
        try {
            val shell = openShell(connection, port)
            if (runShell(shell, "id -u") !in setOf("2000", "0")) {
                throw IOException("Local ADB did not provide a shell identity")
            }
            onConnected()
            while (true) {
                val input = select<PendingInput?> {
                    inputs.onReceive { it }
                    onTimeout(1_000L) { null }
                }
                if (input == null) {
                    if (!withContext(Dispatchers.IO) { connection.isConnected && !shell.isClosed }) {
                        throw IOException("Local ADB disconnected")
                    }
                } else if (runShell(shell, input.command.shell, input.command.durationMs, input.canExecute).isNotEmpty()) {
                    throw IOException("Unexpected local ADB input output")
                }
            }
        } finally {
            inputs.cancel()
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    connection.disconnect()
                } catch (failure: IOException) {
                    Logger.w(TAG, "Local ADB disconnect failed", failure)
                }
            }
        }
    }

    private suspend fun openShell(connection: AbsAdbConnectionManager, port: Int): AdbShell {
        // LibADB can miss a fast OPEN acknowledgement. Retry only before submitting any commands.
        repeat(2) {
            if (!runInterruptible(Dispatchers.IO) { connection.connect(port) }) {
                throw IOException("Local ADB connection timed out")
            }
            val stream = withTimeoutOrNull(2_000L) {
                runInterruptible(Dispatchers.IO) { connection.openStream("shell,v2,raw:sh") }
            }
            if (stream != null) return AdbShell(stream)
            withContext(Dispatchers.IO) { connection.disconnect() }
        }
        throw IOException("Local ADB shell did not open")
    }

    private suspend fun runShell(
        shell: AdbShell,
        command: String,
        durationMs: Long = 0,
        canExecute: () -> Boolean = { true }
    ): String = withTimeoutOrNull(durationMs + 4_000L) {
        runInterruptible(Dispatchers.IO) {
            if (canExecute()) shell.execute(command) else ""
        }
    } ?: throw IOException("Local ADB command timed out")

    private class AdbShell(private val stream: AdbStream) {
        private val input = DataInputStream(stream.openInputStream())
        private val output = stream.openOutputStream()
        val isClosed: Boolean get() = stream.isClosed

        fun execute(command: String): String {
            // Shell v2 uses a one-byte stream ID and a little-endian payload length.
            val script = "$command 2>&1\nprintf '\\n__rc_exit=%d\\n' \$?\n".toByteArray(Charsets.UTF_8)
            val packet = ByteBuffer.allocate(5 + script.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(0.toByte()).putInt(script.size).put(script).array()
            output.write(packet)
            val response = ByteArrayOutputStream()
            var remaining = 8_192
            while (true) {
                val type = input.readUnsignedByte()
                val size = Integer.reverseBytes(input.readInt())
                if (size !in 0..remaining) throw IOException("Invalid local ADB shell response size")
                remaining -= size
                val bytes = ByteArray(size)
                input.readFully(bytes)
                when (type) {
                    1 -> {
                        response.write(bytes)
                        val text = response.toString(Charsets.UTF_8.name())
                        val end = text.lastIndexOf(EXIT_MARKER)
                        if (end < 0 || !text.endsWith('\n')) continue
                        if (text.substring(end + EXIT_MARKER.length, text.lastIndex) != "0") {
                            throw IOException("Local ADB shell command failed")
                        }
                        return text.substring(0, end).trim()
                    }
                    2 -> if (size > 0) throw IOException("Local ADB shell command failed")
                    3 -> throw IOException("Local ADB shell exited")
                    else -> throw IOException("Invalid local ADB shell response")
                }
            }
        }
    }

    companion object {
        private const val TAG = "LocalAdb"
        private const val EXIT_MARKER = "\n__rc_exit="
        private const val KEY_ALIAS = "remote-control-local-adb-v2"
        private val keyLock = Any()

        val supported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        suspend fun pair(port: Int, code: String): Boolean = withTimeoutOrNull(10_000L) {
            withContext(Dispatchers.IO) {
                val key = loadKey()
                suspendCancellableCoroutine { attempt ->
                    val socket = Socket()
                    attempt.invokeOnCancellation { runCatching { socket.close() } }
                    attempt.resumeWith(runCatching { socket.use { LocalAdbPairing.pair(it, port, code, key) } })
                }
            }
        } ?: false

        private fun newConnection(): AbsAdbConnectionManager {
            val key = loadKey()
            return object : AbsAdbConnectionManager() {
                override fun getPrivateKey() = key.privateKey
                override fun getCertificate() = key.certificate
                override fun getDeviceName() = LocalAdbPairing.DEVICE_NAME
            }.apply {
                setHostAddress(LocalAdbPairing.HOST)
                setApi(Build.VERSION.SDK_INT)
                setTimeout(4, TimeUnit.SECONDS)
                setThrowOnUnauthorised(true)
            }
        }

        private fun loadKey(): KeyStore.PrivateKeyEntry = synchronized(keyLock) {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!store.containsAlias(KEY_ALIAS)) {
                KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
                    initialize(
                        KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                            .setKeySize(2048)
                            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                                KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                                KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                            // Conscrypt signs with an opaque Keystore key through a raw RSA operation.
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setCertificateSubject(X500Principal("CN=RemoteControl"))
                            .build()
                    )
                }.generateKeyPair()
            }
            store.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        }
    }
}
