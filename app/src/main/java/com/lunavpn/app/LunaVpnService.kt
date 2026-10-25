package com.lunavpn.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import hev.sockstun.TProxyService // <--- IMPORTANTE
import kotlinx.coroutines.*
import java.io.File // <--- IMPORTANTE

class LunaVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var ssh: SshTunnel? = null
    private var socks: Socks5Server? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val TUN_IPV4 = "10.8.0.2"
    private val TUN_MTU = 1500

    companion object {
        const val ACTION_CONNECT = "com.lunavpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.lunavpn.app.DISCONNECT"
        private const val CHANNEL_ID = "lunavpn"
        private const val NOTIF_ID = 1001
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                createChannel()
                startForeground(NOTIF_ID, buildNotification("Conectando…"))
                val host = intent.getStringExtra("host") ?: return START_NOT_STICKY
                val port = intent.getIntExtra("port", 22)
                val user = intent.getStringExtra("user") ?: return START_NOT_STICKY
                val privateKey = intent.getStringExtra("privateKey") ?: return START_NOT_STICKY
                val mode = intent.getStringExtra("mode") ?: "DIRECT"
                val payload = intent.getStringExtra("payload") ?: ""
                
                scope.launch { connect(host, port, user, privateKey, mode, payload) }
            }
            ACTION_DISCONNECT -> {
                disconnect()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun connect(host: String, port: Int, user: String, privateKey: String, mode: String, payload: String) {
        try {
            val sshTunnel = SshTunnel()
            sshTunnel.connect(host, port, user, privateKey, payload)
            ssh = sshTunnel

            val socksServer = Socks5Server(sshTunnel)
            val socksPort = socksServer.start()
            socks = socksServer

            val builder = Builder()
                .setSession("LunaVPN")
                .setMtu(TUN_MTU)
                .addAddress(TUN_IPV4, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(packageName)
            
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

            vpnInterface = builder.establish() ?: throw IllegalStateException("Falha ao criar TUN")

            // Cria o arquivo YAML com as configurações da TUN e do SOCKS5
            val configFile = File(cacheDir, "hev-config.yml")
            configFile.writeText("""
                tunnel:
                  mtu: $TUN_MTU
                  ipv4: '$TUN_IPV4'
                socks5:
                  port: $socksPort
                  address: '127.0.0.1'
                  udp: 'udp'
            """.trimIndent())

            // Inicia o tun2socks passando o caminho do YAML e o descritor de arquivo da TUN
            TProxyService.TProxyStartService(configFile.absolutePath, vpnInterface!!.fd)

            updateNotification("Conectado • Modo: $mode")
        } catch (e: Exception) {
            updateNotification("Erro: ${e.message}")
            disconnect()
            stopSelf()
        }
    }

    private fun disconnect() {
        runCatching { TProxyService.TProxyStopService() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        socks?.stop(); socks = null
        ssh?.disconnect(); ssh = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() { disconnect(); super.onRevoke() }
    override fun onDestroy() { disconnect(); scope.cancel(); super.onDestroy() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Luna VPN", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Luna VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }
}
