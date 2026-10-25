package com.lunavpn.app

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

// Fábrica de Sockets customizada para injetar a payload
class PayloadSocketFactory(private val payload: String) : SocketFactory {
    override fun createSocket(host: String, port: Int): Socket {
        val socket = Socket(host, port)
        if (payload.isNotEmpty()) {
            val out = socket.getOutputStream()
            // Substitui os marcadores [crlf] por quebras de linha reais
            val formattedPayload = payload.replace("[crlf]", "\r\n").replace("[lf]", "\n")
            out.write(formattedPayload.toByteArray())
            out.flush()
        }
        return socket
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
}

class SshTunnel {
    private var session: Session? = null

    fun connect(host: String, port: Int, user: String, privateKey: String, payload: String): Session {
        val jsch = JSch()
        jsch.addIdentity("luna_key", privateKey.toByteArray(), null, null)
        
        val s = jsch.getSession(user, host, port)
        s.setConfig("PreferredAuthentications", "publickey")
        s.setConfig("StrictHostKeyChecking", "no")
        
        // AQUI ESTÁ A MÁGICA: Injetamos a payload antes de conectar no SSH
        s.setSocketFactory(PayloadSocketFactory(payload))
        
        s.connect(15_000)
        session = s
        return s
    }

    fun openChannel(destHost: String, destPort: Int) =
        (session?.openChannel("direct-tcpip") as? com.jcraft.jsch.ChannelDirectTCPIP)?.apply {
            setHost(destHost)
            setPort(destPort)
        }

    fun disconnect() {
        runCatching { session?.disconnect() }
        session = null
    }
}
