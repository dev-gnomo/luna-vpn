package com.lunavpn.app

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

class SshTunnel {
    private var session: Session? = null

    // Agora recebemos a chave privada em vez da senha
    fun connect(host: String, port: Int, user: String, privateKey: String): Session {
        val jsch = JSch()
        
        // Adiciona a chave privada na memória do JSch
        jsch.addIdentity("luna_key", privateKey.toByteArray(), null, null)
        
        val s = jsch.getSession(user, host, port)
        
        // Força o uso de chave pública (ignora senha)
        s.setConfig("PreferredAuthentications", "publickey")
        s.setConfig("StrictHostKeyChecking", "no")
        
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
