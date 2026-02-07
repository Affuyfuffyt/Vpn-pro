package com.example.myv2rayvpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.annotation.RequiresApi

class MainActivity : Activity() {

    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
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
        title.text = "HTTP PROXY (UDP FIX)"
        title.textSize = 22f
        title.setTextColor(Color.BLACK)
        title.gravity = android.view.Gravity.CENTER
        mainLayout.addView(title)

        fun createField(label: String, hint: String): EditText {
            val txt = TextView(this)
            txt.text = label
            txt.setTextColor(Color.DKGRAY)
            mainLayout.addView(txt)
            val edt = EditText(this)
            edt.hint = hint
            edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        etIp = createField("Proxy IP", "e.g. 46.101.111.165")
        etPort = createField("Proxy Port", "e.g. 8080")
        etUser = createField("Username", "")
        etPass = createField("Password", "")

        val spacer = TextView(this)
        spacer.height = 40
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "🚀 اتصال (مع إصلاح DNS)"
        btnConnect.textSize = 18f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#43A047")) // أخضر
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        val logLabel = TextView(this)
        logLabel.text = "السجلات:"
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 12f
        tvLogs.setTextColor(Color.BLUE)
        tvLogs.text = "جاهز..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
    }

    // --- JSON الذكي (TCP للبروكسي / UDP مباشر) ---
    private fun createJsonConfig(): String {
        val ip = etIp.text.toString().trim()
        val port = etPort.text.toString().toIntOrNull() ?: 8080
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()

        val userConfig = if (user.isNotEmpty() && pass.isNotEmpty()) {
            """ "users": [ { "user": "$user", "pass": "$pass" } ] """
        } else {
            """ "users": [] """
        }

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
                    "settings": { "auth": "noauth", "udp": true }
                }
            ],
            "outbounds": [
                {
                    "tag": "proxy",
                    "protocol": "http",
                    "settings": {
                        "servers": [
                            {
                                "address": "$ip",
                                "port": $port,
                                $userConfig
                            }
                        ]
                    }
                },
                {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {}
                }
            ],
            "routing": {
                "domainStrategy": "AsIs",
                "rules": [
                    {
                        "type": "field",
                        "network": "udp",
                        "outboundTag": "direct"
                    },
                    {
                        "type": "field",
                        "port": "53",
                        "outboundTag": "direct"
                    },
                    {
                        "type": "field",
                        "network": "tcp",
                        "outboundTag": "proxy"
                    }
                ]
            },
            "dns": {
                "servers": [ "8.8.8.8", "1.1.1.1" ]
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
            tvLogs.text = "جاري الاتصال بـ $etIp (TCP Mode)..."
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
