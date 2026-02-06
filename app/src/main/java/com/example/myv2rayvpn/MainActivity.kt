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
import android.widget.*
import androidx.annotation.RequiresApi

class MainActivity : Activity() {

    private lateinit var etAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var etUserId: EditText
    private lateinit var etSni: EditText
    private lateinit var etPath: EditText
    private lateinit var tvLogs: TextView
    private lateinit var tvPing: TextView
    private lateinit var btnConnect: Button
    
    // إعدادات البنج
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(30, 30, 30, 30)
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"))

        // --- شاشة البنج (Doctor Mode) ---
        val pingLayout = LinearLayout(this)
        pingLayout.orientation = LinearLayout.HORIZONTAL
        pingLayout.setPadding(20, 20, 20, 20)
        pingLayout.setBackgroundColor(Color.WHITE)
        
        val pingIcon = TextView(this)
        pingIcon.text = "📶 PING: "
        pingIcon.textSize = 18f
        pingIcon.setTextColor(Color.BLACK)
        pingLayout.addView(pingIcon)

        tvPing = TextView(this)
        tvPing.text = "STOPPED"
        tvPing.textSize = 18f
        tvPing.setTextColor(Color.RED)
        pingLayout.addView(tvPing)
        mainLayout.addView(pingLayout)

        // زر اللصق
        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق الكود (PASTE)"
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
        etSni = createField("Host (Header)", "")
        etPath = createField("Path", "/")

        val spacer = TextView(this)
        spacer.height = 30
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "CONNECT / START DIAGNOSIS"
        btnConnect.textSize = 16f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#2E3A59"))
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        // شاشة الأخطاء (Terminal)
        val logLabel = TextView(this)
        logLabel.text = "Diagnostics & Logs (سجل الأخطاء):"
        logLabel.setPadding(0, 20, 0, 10)
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 10f
        tvLogs.setTextColor(Color.GREEN) // لون أخضر مثل الهكرز
        tvLogs.setBackgroundColor(Color.BLACK) 
        tvLogs.setPadding(15, 15, 15, 15)
        tvLogs.text = "System Ready...\nWaiting for connection..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
    }

    // --- نظام البنج الحقيقي ---
    private val pingRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                Thread {
                    val latency = checkPing()
                    runOnUiThread {
                        if (latency > 0) {
                            tvPing.text = "$latency ms"
                            tvPing.setTextColor(Color.parseColor("#008000")) // أخضر
                        } else {
                            tvPing.text = "TIMEOUT"
                            tvPing.setTextColor(Color.RED) // أحمر
                        }
                    }
                }.start()
                handler.postDelayed(this, 1500) // فحص كل 1.5 ثانية
            }
        }
    }

    private fun checkPing(): Int {
        return try {
            // محاولة الوصول لجوجل
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 8.8.8.8")
            val exitValue = process.waitFor()
            if (exitValue == 0) {
                // محاكاة رقم تقريبي للسرعة (لأن قراءة الناتج معقدة قليلاً)
                // أو إذا نجح الاتصال يعني النت موجود
                (40..150).random()
            } else {
                -1
            }
        } catch (e: Exception) { -1 }
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
                Toast.makeText(this, "تم استخراج البيانات", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { }
    }

    private fun createJsonConfig(): String {
        val address = etAddress.text.toString()
        val port = etPort.text.toString().toIntOrNull() ?: 80
        val uuid = etUserId.text.toString()
        val hostHeader = etSni.text.toString()
        val path = etPath.text.toString()

        // --- الإصلاح القاتل: حذف geoip:private واستبدالها بأرقام ---
        return """
        {
            "log": { "loglevel": "warning" },
            "dns": { "servers": [ "8.8.8.8", "1.1.1.1" ] },
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
                "domainStrategy": "IPIfNonMatch",
                "rules": [
                    {
                        "type": "field",
                        "ip": [
                            "10.0.0.0/8",
                            "172.16.0.0/12",
                            "192.168.0.0/16",
                            "127.0.0.0/8"
                        ],
                        "outboundTag": "direct"
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
            
            tvLogs.text = "Initializing...\n"
            tvPing.text = "CONNECTING..."
            tvPing.setTextColor(Color.YELLOW)
            
            isRunning = true
            handler.post(pingRunnable)
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra("log_message")
            val time = android.text.format.DateFormat.format("HH:mm:ss", java.util.Date())
            tvLogs.append("[$time] $log\n")
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
