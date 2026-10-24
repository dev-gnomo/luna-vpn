package com.lunavpn.app

import com.jcraft.jsch.ChannelDirectTCPIP
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class Socks5Server(private val ssh: SshTunnel) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(): Int {
        val ss = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        scope.launch {
            while (!ss.isClosed) {
                runCatching {
                    val client = ss.accept()
                    scope.launch { handle(client) }
                }
            }
        }
        return ss.localPort
    }

    private fun handle(client: Socket) = runCatching {
        client.use { c ->
            val input = DataInputStream(c.getInputStream())
            val output = c.getOutputStream()

            // Handshake
            val ver = input.readUnsignedByte()
            if (ver != 5) return@use
            val nMethods = input.readUnsignedByte()
            repeat(nMethods) { input.readUnsignedByte() }
            output.write(byteArrayOf(0x05, 0x00)) // sem auth

            // Request
            val ver2 = input.readUnsignedByte()
            if (ver2 != 5) return@use
            val cmd = input.readUnsignedByte() // 0x01 = CONNECT
            input.readUnsignedByte() // reserved
            val atyp = input.readUnsignedByte()

            val host = when (atyp) {
                0x01 -> InetAddress.getByAddress(ByteArray(4).also { input.readFully(it) }).hostAddress
                0x03 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(Charsets.UTF_8)
                0x04 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
                else -> return@use
            }
            val port = input.readUnsignedShort()

            if (cmd != 0x01) return@use

            val channel = ssh.openChannel(host, port) as ChannelDirectTCPIP
            channel.setInputStream(null)
            channel.setOutputStream(null)
            channel.connect(10_000)

            // Resposta de sucesso
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            // Bidirecional
            val chIn = channel.inputStream
            val chOut = channel.outputStream
            val t1 = Thread { pipe(c.getInputStream(), chOut) }
            val t2 = Thread { pipe(chIn, c.getOutputStream()) }
            t1.start(); t2.start()
            t1.join(); t2.join()
            runCatching { channel.disconnect() }
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) = runCatching {
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
            output.flush()
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        scope.cancel()
    }
}
