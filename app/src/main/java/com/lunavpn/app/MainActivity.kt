package com.lunavpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var pendingMode = ""

    private val vpnPermLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) {
                if (pendingMode.isNotEmpty()) startVpn(pendingMode)
            } else {
                Toast.makeText(this, "Permissão VPN negada", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Botões dos 4 modos
        findViewById<Button>(R.id.btnMode1).setOnClickListener { prepareVpn("DIRECT") }
        findViewById<Button>(R.id.btnMode2).setOnClickListener { prepareVpn("PROXY") }
        findViewById<Button>(R.id.btnMode3).setOnClickListener { prepareVpn("SSL") }
        findViewById<Button>(R.id.btnMode4).setOnClickListener { prepareVpn("SSL_PROXY") }

        // Botão de desconectar
        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            startService(Intent(this, LunaVpnService::class.java).apply {
                action = LunaVpnService.ACTION_DISCONNECT
            })
        }
    }

    private fun prepareVpn(mode: String) {
        pendingMode = mode
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermLauncher.launch(intent)
        } else {
            startVpn(mode)
        }
    }

    private fun startVpn(mode: String) {
        // Puxa os dados automaticamente do Config.kt
        startForegroundService(Intent(this, LunaVpnService::class.java).apply {
            action = LunaVpnService.ACTION_CONNECT
            putExtra("host", Config.SSH_HOST)
            putExtra("port", Config.SSH_PORT)
            putExtra("user", Config.SSH_USER)
            putExtra("privateKey", Config.SSH_PRIVATE_KEY) // Envia a chave SSH
            putExtra("mode", mode) // Envia qual modo foi escolhido
        })
        Toast.makeText(this, "Conectando modo: $mode", Toast.LENGTH_SHORT).show()
    }
}
