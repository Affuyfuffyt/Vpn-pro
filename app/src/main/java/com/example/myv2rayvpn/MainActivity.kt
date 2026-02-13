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
import android.view.Gravity
import android.widget.*
import androidx.annotation.RequiresApi

class MainActivity : Activity() {

    private lateinit var etAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var etUserId: EditText
    private lateinit var etSni: EditText
    private lateinit var etPath: EditText
    
    // متغيرات العرض الجديدة
    private lateinit var tvStatus: TextView // لعرض الخطوات (Connecting...)
    private lateinit var tvError: TextView  // لعرض الأخطاء (Error: ...)
    private lateinit var btnConnect: Button
    private lateinit var progressBar: ProgressBar

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(40, 40, 40, 40)
        mainLayout.setBackgroundColor(Color.WHITE)

        // --- منطقة الحالة (Status Area) ---
        val statusContainer = LinearLayout(this)
        statusContainer.orientation = LinearLayout.VERTICAL
        statusContainer.setPadding(20, 40, 20, 40)
        statusContainer.setBackgroundColor(Color.parseColor("#F5F5F5"))
        statusContainer.gravity = Gravity.CENTER

        // دائرة التحميل
        progressBar = ProgressBar(this)
        progressBar.visibility = ProgressBar.INVISIBLE
        statusContainer.addView(progressBar)

        // نص الخطوات (Notations)
        tvStatus = TextView(this)
        tvStatus.text = "Ready to Connect"
        tvStatus.textSize = 18f
        tvStatus.setTextColor(Color.parseColor("#1565C0")) // أزرق
        tvStatus.gravity = Gravity.CENTER
        tvStatus.setPadding(0, 20, 0, 0)
        statusContainer.addView(tvStatus)

        // نص الأخطاء (Errors)
        tvError = TextView(this)
        tvError.text = ""
        tvError.textSize = 14f
        tvError.setTextColor(Color.RED) // أحمر
        tvError.gravity = Gravity.CENTER
        tvError.setPadding(0, 10, 0, 0)
        statusContainer.addView(tvError)

        mainLayout.addView(statusContainer)

        // زر اللصق
        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق كود VLESS"
        btnPaste.setOnClickListener { pasteFromClipboard() }
        mainLayout.addView(btnPaste)

        // الحقول
        fun createField(label: String, default: String): EditText {
            val txt = TextView(this)
            txt.text = label; txt.textSize = 12f; txt.setTextColor(Color.DKGRAY)
            mainLayout.addView(txt)
            val edt = EditText(this)
            edt.setText(default); edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        etAddress = createField("IP Address", "")
        etPort = createField("Port", "81") // الطلب: بورت 81 افتراضياً
        etUserId = createField("UUID", "")
        etSni = createField("Host / SNI", "")
        etPath = createField("Path (WS)", "/")

        val spacer = TextView(this)
        spacer.height = 30
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "START CONNECTION"
        btnConnect.textSize = 16f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#2E7D32"))
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        setContentView(mainLayout)

        // تسجيل مستقبل الرسائل من الخدمة
        val filter = IntentFilter()
        filter.addAction("VPN_STATUS_UPDATE") // لتحديث الخطوات
        filter.addAction("VPN_ERROR_REPORT")  // لتحديث الأخطاء
        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
                Toast.makeText(this, "تم نسخ الإعدادات", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { }
    }

    // --- تكوين VLESS WS NoTLS (Port 81) ---
    private fun createJsonConfig(): String {
        val address = etAddress.text.toString().trim()
        val port = etPort.text.toString().toIntOrNull() ?: 81
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
                { "tag": "direct", "protocol": "freedom", "settings": {} }
            ],
            "dns": {
                "servers": [ "8.8.8.8", "1.1.1.1" ]
            },
            "routing": {
                "domainStrategy": "AsIs",
                "rules": [
                    { "type": "field", "outboundTag": "proxy", "port": "0-65535" } 
                ]
            }
        }
        """.trimIndent()
    }

    private fun startVpn() {
        // تنظيف الواجهة قبل البدء
        tvError.text = ""
        tvStatus.text = "Initializing..."
        progressBar.visibility = ProgressBar.VISIBLE

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
        }
    }

    // --- المستقبل الذي يعرض الخطوات والأخطاء ---
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "VPN_STATUS_UPDATE" -> {
                    val step = intent.getStringExtra("status_message")
                    tvStatus.text = step
                    
                    // تغيير لون النص حسب الحالة
                    if (step?.contains("Connected") == true) {
                        tvStatus.setTextColor(Color.parseColor("#2E7D32")) // أخضر عند النجاح
                        progressBar.visibility = ProgressBar.INVISIBLE
                    } else {
                        tvStatus.setTextColor(Color.parseColor("#1565C0")) // أزرق أثناء المحاولة
                    }
                }
                "VPN_ERROR_REPORT" -> {
                    val error = intent.getStringExtra("error_message")
                    tvError.text = "❌ Error: $error"
                    tvStatus.text = "Connection Failed"
                    tvStatus.setTextColor(Color.RED)
                    progressBar.visibility = ProgressBar.INVISIBLE
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusReceiver)
    }
}
