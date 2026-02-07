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
        mainLayout.setPadding(40, 40, 40, 40)
        mainLayout.setBackgroundColor(Color.WHITE)

        val title = TextView(this)
        title.text = "HTTP PROXY MODE"
        title.textSize = 24f
        title.setTextColor(Color.parseColor("#000000"))
        title.gravity = android.view.Gravity.CENTER
        mainLayout.addView(title)

        fun createField(label: String, hint: String): EditText {
            val tv = TextView(this)
            tv.text = label
            tv.setTextColor(Color.DKGRAY)
            mainLayout.addView(tv)
            val edt = EditText(this)
            edt.hint = hint
            edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        // الحقول الأربعة المطلوبة
        etIp = createField("Proxy IP (عنوان السيرفر)", "مثال: 192.168.1.1")
        etPort = createField("Proxy Port (المنفذ)", "مثال: 8080")
        etUser = createField("Username (اختياري)", "اسم المستخدم")
        etPass = createField("Password (اختياري)", "كلمة السر")

        val spacer = TextView(this)
        spacer.height = 50
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "🔗 اتصال (HTTP)"
        btnConnect.textSize = 18f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#FF5722")) // برتقالي
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        val logLabel = TextView(this)
        logLabel.text = "Socks/Http Logs:"
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 12f
        tvLogs.setTextColor(Color.BLUE)
        tvLogs.text = "جاهز للاتصال..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
    }

    private fun createJsonConfig(): String {
        val ip = etIp.text.toString().trim()
        val port = etPort.text.toString().toIntOrNull() ?: 8080
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString().trim()

        // بناء إعدادات المستخدم (إذا وجد)
        val userSection = if (user.isNotEmpty() && pass.isNotEmpty()) {
            """ "users": [ { "user": "$user", "pass": "$pass" } ] """
        } else {
            """ "users": [] """
        }

        // هذا كود JSON خاص ببروتوكول HTTP فقط
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
                                $userSection
                            }
                        ]
                    }
                },
                { "tag": "direct", "protocol": "freedom", "settings": {} }
            ],
            "routing": {
                "domainStrategy": "AsIs",
                "rules": [
                    { "type": "field", "outboundTag": "proxy", "port": "0-65535" }
                ]
            },
            "dns": { "servers": ["8.8.8.8", "1.1.1.1"] }
        }
        """.trimIndent()
    }
    // لاحظ: protocol: "http" بدلاً من "vless"

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
            tvLogs.text = "جاري الاتصال بـ ${etIp.text}..."
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
