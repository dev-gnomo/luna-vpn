package com.lunavpn.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.concurrent.thread

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

        // Pede permissão para ler o chip do celular (necessário para MCC/MNC)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_PHONE_STATE), 101)

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
        // 1. Pega o código da operadora (MCC + MNC)
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val networkOperator = telephonyManager.networkOperator // Ex: "72405"
        
        val mcc = if (networkOperator.length >= 3) networkOperator.substring(0, 3) else "000"
        val mnc = if (networkOperator.length > 3) networkOperator.substring(3) else "00"

        Toast.makeText(this, "Buscando payload para: $mcc-$mnc", Toast.LENGTH_SHORT).show()

        // 2. Faz a chamada HTTP para o seu Backend (em uma thread separada)
        thread {
            try {
                val client = OkHttpClient()
                
                // ⚠️ COLOQUE A URL DO SEU BACKEND AQUI (Ex: http://192.168.1.10:3000/api/payloads/get?mcc=$mcc&mnc=$mnc)
                val url = "http://77.112.117.49:3000/api/payloads/get?mcc=$mcc&mnc=$mnc" 
                
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val jsonResponse = JSONObject(response.body?.string() ?: "{}")
                
                // Pega a payload que o backend retornou
                val payload = jsonResponse.optString("payload", "")

                // 3. Inicia a VPN passando os dados do Config e a Payload
                runOnUiThread {
                    startForegroundService(Intent(this, LunaVpnService::class.java).apply {
                        action = LunaVpnService.ACTION_CONNECT
                        putExtra("host", Config.SSH_HOST)
                        putExtra("port", Config.SSH_PORT)
                        putExtra("user", Config.SSH_USER)
                        putExtra("privateKey", Config.SSH_PRIVATE_KEY)
                        putExtra("mode", mode)
                        putExtra("payload", payload) // <--- Envia a payload para o serviço
                    })
                    Toast.makeText(this, "Conectando modo: $mode", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Erro ao buscar payload: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
