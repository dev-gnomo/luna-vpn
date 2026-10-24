package com.lunavpn.app

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

/**
 * Cliente SSH que abre um canal "direct-tcpip" para cada conexão SOCKS.
 * O Socks5Server usa este objeto para encaminhar tráfego.
 */
class SshTunnel {
    private var session: Session? = null

    fun connect(host: String, port: Int, user: String, password: String): Session {
        val jsch = JSch()
        val s = jsch.getSession(user, host, port)
        s.setPassword(password)
        // Em produção use known_hosts. Aqui simplificamos para o protótipo:
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        s.connect(15_000)
        session = s
        return s
    }

    fun openChannel(destHost: String, destPort: Int) =
        session?.openChannel("direct-tcpip", destHost, destPort)

    fun disconnect() {
        runCatching { session?.disconnect() }
        session = null
    }
}
