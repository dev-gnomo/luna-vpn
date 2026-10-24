package com.lunavpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var hostInput: EditText
    private lateinit var portInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private var pendingConnect = false

    private val vpnPermLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK && pendingConnect) startVpn()
            else Toast.makeText(this, "Permissão VPN negada", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hostInput = findViewById(R.id.hostInput)
        portInput = findViewById(R.id.portInput)
        userInput = findViewById(R.id.userInput)
        passInput = findViewById(R.id.passInput)

        findViewById<Button>(R.id.connectBtn).setOnClickListener {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                pendingConnect = true
                vpnPermLauncher.launch(intent)
            } else {
                startVpn()
            }
        }

        findViewById<Button>(R.id.disconnectBtn).setOnClickListener {
            startService(Intent(this, LunaVpnService::class.java).apply {
                action = LunaVpnService.ACTION_DISCONNECT
            })
        }
    }

    private fun startVpn() {
        val host = hostInput.text.toString().trim()
        val port = portInput.text.toString().toIntOrNull() ?: 22
        val user = userInput.text.toString().trim()
        val pass = passInput.text.toString()
        
        if (host.isBlank() || user.isBlank()) {
            Toast.makeText(this, "Preencha host e usuário", Toast.LENGTH_SHORT).show()
            return
        }
        
        startForegroundService(Intent(this, LunaVpnService::class.java).apply {
            action = LunaVpnService.ACTION_CONNECT
            putExtra("host", host)
            putExtra("port", port)
            putExtra("user", user)
            putExtra("pass", pass)
        })
    }
}
