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
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MainActivity : Activity() {

    private lateinit var etAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var etUserId: EditText
    private lateinit var etSni: EditText
    private lateinit var etPath: EditText
    private lateinit var tvLogs: TextView
    private lateinit var btnConnect: Button
    
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(30, 30, 30, 30)
        mainLayout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "V2RAY FINAL FIX"
        title.textSize = 20f
        title.setTextColor(Color.BLACK)
        title.gravity = android.view.Gravity.CENTER
        mainLayout.addView(title)

        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق الإعدادات (PASTE)"
        btnPaste.setOnClickListener { pasteFromClipboard() }
        mainLayout.addView(btnPaste)

        fun createField(label: String, default: String = ""): EditText {
            val txt = TextView(this)
            txt.text = label; txt.textSize = 12f; txt.setTextColor(Color.DKGRAY)
            mainLayout.addView(txt)
            val edt = EditText(this)
            edt.textSize = 14f; edt.setText(default); edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        etAddress = createField("IP / Address", "")
        etPort = createField("Port", "80")
        etUserId = createField("UUID", "")
        etSni = createField("SNI / Host", "")
        etPath = createField("Path", "/")

        val spacer = TextView(this)
        spacer.height = 30
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "🚀 اتصال (بدون أخطاء)"
        btnConnect.textSize = 16f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#1A237E")) 
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        val logLabel = TextView(this)
        logLabel.text = "Logs:"
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 10f
        tvLogs.setTextColor(Color.DKGRAY)
        tvLogs.text = "جاهز..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
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
                Toast.makeText(this, "تم الإعداد", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { }
    }

    private fun createJsonConfig(): String {
        val address = etAddress.text.toString()
        val port = etPort.text.toString().toIntOrNull() ?: 80
        val uuid = etUserId.text.toString()
        val hostHeader = etSni.text.toString()
        val path = etPath.text.toString()

        // --- هنا الإصلاح: حذف geoip واستبداله بأرقام ---
        return """
        {
            "log": { "loglevel": "warning" },
            "inbounds": [
                {
                    "port": 10808,
                    "protocol": "socks",
                    "sniffing": {
                        "enabled": true,
                        "destOverride": ["http", "tls"]
                    },
                    "settings": {
                        "auth": "noauth",
                        "udp": true
                    }
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
                    },
                    "mux": { "enabled": false, "concurrency": -1 }
                },
                { "tag": "direct", "protocol": "freedom", "settings": {} }
            ],
            "dns": {
                "servers": [ "8.8.8.8", "1.1.1.1" ]
            },
            "routing": {
                "domainStrategy": "AsIs",
                "rules": [
                    { 
                        "type": "field", 
                        "outboundTag": "direct", 
                        "ip": [ "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8" ] 
                    },
                    { 
                        "type": "field", 
                        "outboundTag": "proxy", 
                        "port": "0-65535" 
                    } 
                ]
            }
        }
        """.trimIndent()
        // لاحظ في قسم rules: لا يوجد كلمة geoip نهائياً.
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
            tvLogs.text = "تم الاتصال.. (تم إزالة geoip)"
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
        unregisterReceiver(logReceiver)
    }
}
