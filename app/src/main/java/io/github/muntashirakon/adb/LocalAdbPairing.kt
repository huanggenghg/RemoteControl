// SPDX-License-Identifier: Apache-2.0
package io.github.muntashirakon.adb

import org.conscrypt.Conscrypt
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/** Uses LibADB's authentication primitives with a socket the caller can cancel. */
internal object LocalAdbPairing {
    const val HOST = "127.0.0.1"
    const val DEVICE_NAME = "RemoteControl"
    private const val PAIRING_VERSION = 1
    private const val SPAKE2_MESSAGE = 0
    private const val PEER_INFO = 1
    private const val DEVICE_GUID_TYPE: Byte = 1
    private const val PEER_INFO_BYTES = 8_192

    fun pair(socket: Socket, port: Int, code: String, key: KeyStore.PrivateKeyEntry): Boolean {
        socket.connect(InetSocketAddress(HOST, port), 2_000)
        val ssl = synchronized(this) { SslUtils.getSslContext(KeyPair(key.privateKey, key.certificate)) }
        val tls = ssl.socketFactory.createSocket(socket, HOST, port, true) as SSLSocket
        tls.use {
            tls.soTimeout = 8_000
            tls.startHandshake()
            val exportedKey = Conscrypt.exportKeyingMaterial(
                tls, PairingConnectionCtx.EXPORTED_KEY_LABEL, null, PairingConnectionCtx.EXPORT_KEY_SIZE
            ) ?: throw IOException("ADB pairing TLS key material unavailable")
            val password = code.toByteArray(Charsets.UTF_8) + exportedKey
            exportedKey.fill(0)
            val auth = try {
                PairingAuthCtx.createAlice(password) ?: throw IOException("Could not initialize ADB pairing")
            } finally {
                password.fill(0)
            }
            try {
                val input = DataInputStream(tls.inputStream)
                val output = DataOutputStream(tls.outputStream)
                val peerMessage = exchange(input, output, SPAKE2_MESSAGE, auth.msg)
                if (!auth.initCipher(peerMessage)) return false

                // The first byte identifies an ADB public key, followed by a zero-padded key record.
                val localPeer = ByteArray(PEER_INFO_BYTES)
                val publicKey = AndroidPubkey.encodeWithName(key.certificate.publicKey as RSAPublicKey, DEVICE_NAME)
                publicKey.copyInto(localPeer, destinationOffset = 1)
                val encrypted = auth.encrypt(localPeer) ?: throw IOException("Could not encrypt ADB peer info")
                val remotePeer = auth.decrypt(exchange(input, output, PEER_INFO, encrypted)) ?: return false
                return remotePeer.size == PEER_INFO_BYTES && remotePeer[0] == DEVICE_GUID_TYPE
            } finally {
                auth.destroy()
            }
        }
    }

    private fun exchange(input: DataInputStream, output: DataOutputStream, type: Int, payload: ByteArray): ByteArray {
        output.writeByte(PAIRING_VERSION)
        output.writeByte(type)
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()

        val version = input.readUnsignedByte()
        val responseType = input.readUnsignedByte()
        val size = input.readInt()
        if (version != PAIRING_VERSION || responseType != type || size !in 1..(2 * PEER_INFO_BYTES)) {
            throw IOException("Invalid ADB pairing response")
        }
        return ByteArray(size).also { input.readFully(it) }
    }
}
