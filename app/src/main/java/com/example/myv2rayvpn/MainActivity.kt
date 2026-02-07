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
        title.text = "VLESS (DNS FIX)"
        title.textSize = 22f
        title.setTextColor(Color.BLACK)
        title.gravity = android.view.Gravity.CENTER
        mainLayout.addView(title)

        // حقول الإدخال
        fun createField(label: String, default: String): EditText {
            val txt = TextView(this)
            txt.text = label
            txt.setTextColor(Color.DKGRAY)
            mainLayout.addView(txt)
            val edt = EditText(this)
            edt.setText(default)
            edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        etAddress = createField("IP Address", "")
        etPort = createField("Port", "80")
        etUserId = createField("UUID", "")
        etSni = createField("Host / SNI", "")
        etPath = createField("Path", "/")

        val spacer = TextView(this)
        spacer.height = 40
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "🔥 اتصال (VLESS + DNS Bypass)"
        btnConnect.textSize = 18f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#1565C0")) // أزرق
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        val logLabel = TextView(this)
        logLabel.text = "السجلات:"
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 12f
        tvLogs.setTextColor(Color.DKGRAY)
        tvLogs.text = "جاهز..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
    }

    // --- هذا هو الـ JSON السحري ---
    private fun createJsonConfig(): String {
        val address = etAddress.text.toString().trim()
        val port = etPort.text.toString().toIntOrNull() ?: 80
        val uuid = etUserId.text.toString().trim()
        val hostHeader = etSni.text.toString().trim()
        val path = etPath.text.toString().trim()

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
                    "mux": { "enabled": false }
                },
                {
                    "tag": "direct",
                    "protocol": "freedom",
                    "settings": {}
                }
            ],
            "dns": {
                "servers": [ "8.8.8.8", "1.1.1.1" ]
            },
            "routing": {
                "domainStrategy": "AsIs",
                "rules": [
                    {
                        "type": "field",
                        "port": "53",
                        "outboundTag": "direct"
                    },
                    {
                        "type": "field",
                        "protocol": ["dns"],
                        "outboundTag": "direct"
                    },
                    {
                        "type": "field",
                        "port": "0-65535",
                        "outboundTag": "proxy"
                    }
                ]
            }
        }
        """.trimIndent()
    }
    // الشرح: القواعد في routing تقول:
    // 1. أي شيء على بورت 53 (DNS) -> اذهب direct (للإنترنت العادي) عشان نعرف اسم الموقع.
    // 2. أي شيء آخر -> اذهب proxy (للسيرفر) عشان نفتح الموقع.
    // هذا يحل مشكلة "متصل بدون نت".

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
            tvLogs.text = "جاري الاتصال بـ $etAddress..."
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
