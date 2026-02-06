package com.example.myv2rayvpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.widget.*
import androidx.annotation.RequiresApi
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private lateinit var etAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var etUserId: EditText
    private lateinit var etSni: EditText
    private lateinit var etPath: EditText
    private lateinit var tvLogs: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // السماح بالشبكة في الخيط الرئيسي للفحص السريع (لأغراض التصحيح فقط)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(30, 30, 30, 30)
        mainLayout.setBackgroundColor(Color.parseColor("#EEEEEE"))

        // --- شاشة التشخيص (Doctor Monitor) ---
        val statusLayout = LinearLayout(this)
        statusLayout.orientation = LinearLayout.VERTICAL
        statusLayout.setPadding(20, 20, 20, 20)
        statusLayout.setBackgroundColor(Color.BLACK)
        
        val titleParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        titleParams.gravity = android.view.Gravity.CENTER_HORIZONTAL
        
        val lblStatus = TextView(this)
        lblStatus.text = "📶 REAL INTERNET CHECK"
        lblStatus.setTextColor(Color.CYAN)
        lblStatus.layoutParams = titleParams
        statusLayout.addView(lblStatus)

        tvStatus = TextView(this)
        tvStatus.text = "DISCONNECTED"
        tvStatus.textSize = 16f
        tvStatus.setTextColor(Color.RED)
        tvStatus.gravity = android.view.Gravity.CENTER
        tvStatus.setPadding(0, 10, 0, 0)
        statusLayout.addView(tvStatus)
        mainLayout.addView(statusLayout)

        // زر اللصق
        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق الكود (PASTE CONFIG)"
        btnPaste.setOnClickListener { pasteFromClipboard() }
        mainLayout.addView(btnPaste)

        // الحقول
        fun createField(label: String, default: String = ""): EditText {
            val txt = TextView(this)
            txt.text = label; txt.textSize = 12f; txt.setTextColor(Color.DKGRAY)
            mainLayout.addView(txt)
            val edt = EditText(this)
            edt.textSize = 14f; edt.setText(default); edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        etAddress = createField("Address", "")
        etPort = createField("Port", "80")
        etUserId = createField("UUID", "")
        etSni = createField("Host / SNI", "")
        etPath = createField("Path", "/")

        val spacer = TextView(this)
        spacer.height = 30
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "CONNECT & DIAGNOSE"
        btnConnect.textSize = 16f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#00574B"))
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        // السجلات
        val logLabel = TextView(this)
        logLabel.text = "Detailed Logs:"
        logLabel.setPadding(0, 20, 0, 5)
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 10f
        tvLogs.setTextColor(Color.DKGRAY)
        tvLogs.text = "Waiting for action..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
    }

    // --- نظام الفحص الحقيقي (Real HTTP Check) ---
    private val internetCheckRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                Thread {
                    val result = checkRealInternet()
                    runOnUiThread {
                        tvStatus.text = result
                        if (result.contains("SUCCESS")) {
                            tvStatus.setTextColor(Color.GREEN)
                        } else {
                            tvStatus.setTextColor(Color.RED)
                        }
                    }
                }.start()
                handler.postDelayed(this, 3000) // فحص كل 3 ثواني
            }
        }
    }

    private fun checkRealInternet(): String {
        return try {
            // محاولة فتح موقع خفيف جداً للتأكد من وجود إنترنت حقيقي
            val url = URL("http://clients3.google.com/generate_204")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "HEAD"
            
            val code = connection.responseCode
            connection.disconnect()
            
            if (code == 204) "✅ SUCCESS: Internet Access OK" else "⚠️ Connected but HTTP Error: $code"
        } catch (e: Exception) {
            "❌ NO INTERNET: ${e.message}"
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.primaryClip != null && clipboard.primaryClip!!.itemCount > 0) {
            val pasteData = clipboard.primaryClip!!.getItemAt(0).text.toString().trim()
            parseConfig(pasteData)
        }
    }

    private fun parseConfig(conf: String) {
        try {
            if (conf.startsWith("vless://")) {
                val uri = Uri.parse(conf)
                etUserId.setText(uri.userInfo)
                etAddress.setText(uri.host)
                etPort.setText(uri.port.toString())
                val sniOrHost = uri.getQueryParameter("sni") ?: uri.getQueryParameter("host") ?: ""
                etSni.setText(sniOrHost)
                etPath.setText(uri.getQueryParameter("path") ?: "/")
                Toast.makeText(this, "Config Loaded", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { }
    }

    private fun createJsonConfig(): String {
        val address = etAddress.text.toString()
        val port = etPort.text.toString().toIntOrNull() ?: 80
        val uuid = etUserId.text.toString()
        val hostHeader = etSni.text.toString()
        val path = etPath.text.toString()

        // --- التعديل الحاسم: Sniffing + Catch-All Rule ---
        // أعدنا تفعيل sniffing لأنه ضروري جداً لتوجيه الدومينات بدون geoip.dat
        return """
        {
            "log": { "loglevel": "warning" },
            "dns": {
                "servers": [ "8.8.8.8", "1.1.1.1" ]
            },
            "inbounds": [
                {
                    "port": 10808,
                    "protocol": "socks",
                    "sniffing": {
                        "enabled": true,
                        "destOverride": ["http", "tls"]
                    },
                    "settings": { "auth": "noauth" }
                }
            ],
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "vless",
                    "settings": {
                        "vnext": [
                            {
                                "address": "$address",
                                "port": $port,
                                "users": [ { "id": "$uuid", "encryption": "none" } ]
                            }
                        ]
                    },
                    "streamSettings": {
                        "network": "ws",
                        "security": "none",
                        "wsSettings": {
                            "path": "$path",
                            "headers": { "Host": "$hostHeader" }
                        }
                    }
                },
                { "tag": "direct", "protocol": "freedom", "settings": {} }
            ],
            "routing": {
                "domainStrategy": "AsIs",
                "rules": [
                    {
                        "type": "field",
                        "ip": [ "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8" ],
                        "outboundTag": "direct"
                    },
                    {
                        "type": "field",
                        "network": "tcp,udp",
                        "outboundTag": "proxy"
                    }
                ]
            }
        }
        """.trimIndent()
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1)
        } else {
            onActivityResult(1, Activity.RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val jsonConfig = createJsonConfig()
            val intent = Intent(this, MyVpnService::class.java)
            intent.action = "START_VPN"
            intent.putExtra("V2RAY_CONFIG", jsonConfig)
            startService(intent)
            
            tvLogs.text = "Connecting..."
            isRunning = true
            handler.post(internetCheckRunnable)
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra("log_message")
            tvLogs.append("\n$log")
            val scroll = tvLogs.parent as ScrollView
            scroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        unregisterReceiver(logReceiver)
    }
}
