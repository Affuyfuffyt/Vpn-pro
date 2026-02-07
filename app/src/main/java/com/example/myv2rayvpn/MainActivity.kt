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
        mainLayout.setPadding(40, 40, 40, 40)
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"))

        // العنوان
        val title = TextView(this)
        title.text = "Simple V2Ray (Proxy Mode)"
        title.textSize = 22f
        title.setTextColor(Color.BLACK)
        title.gravity = android.view.Gravity.CENTER
        mainLayout.addView(title)

        // زر اللصق
        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق كود VLESS"
        btnPaste.setBackgroundColor(Color.LTGRAY)
        btnPaste.setOnClickListener { pasteFromClipboard() }
        mainLayout.addView(btnPaste)

        // الحقول
        fun createField(label: String, default: String = ""): EditText {
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

        etAddress = createField("IP Address (عنوان السيرفر)")
        etPort = createField("Port (المنفذ - تأكد منه!)", "443")
        etUserId = createField("UUID (كلمة السر)")
        etSni = createField("SNI / Host (نطاق السيرفر)")
        etPath = createField("Path (المسار)", "/")

        val spacer = TextView(this)
        spacer.height = 40
        mainLayout.addView(spacer)

        // زر التشغيل
        btnConnect = Button(this)
        btnConnect.text = "🔥 اتصال مباشر (بدون فلاتر)"
        btnConnect.textSize = 18f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#C62828")) // أحمر
        btnConnect.minHeight = 160
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        // السجلات
        val logLabel = TextView(this)
        logLabel.text = "سجل الأحداث:"
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 12f
        tvLogs.setTextColor(Color.BLUE)
        tvLogs.text = "بانتظار الإعدادات..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 500)
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
                etPort.setText(uri.port.toString()) // سينسخ البورت من الكود
                val sniOrHost = uri.getQueryParameter("sni") ?: uri.getQueryParameter("host") ?: ""
                etSni.setText(sniOrHost)
                etPath.setText(uri.getQueryParameter("path") ?: "/")
                Toast.makeText(this, "تم نسخ الكود! تأكد من الأرقام", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "كود غير صالح", Toast.LENGTH_SHORT).show()
        }
    }

    // --- JSON "الأنبوب الفارغ" ---
    // لا يوجد routing، لا يوجد dns محلي، كل شيء يذهب للسيرفر
    private fun createJsonConfig(): String {
        val address = etAddress.text.toString().trim()
        val port = etPort.text.toString().toIntOrNull() ?: 443
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
                    "settings": {
                        "auth": "noauth",
                        "udp": true
                    }
                }
            ],
            "outbounds": [
                {
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
                }
            ]
        }
        """.trimIndent()
    }
    // ملاحظة: عندما نحذف قسم routing، المحرك يرسل كل شيء لأول outbound تلقائياً.

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
            tvLogs.text = "جاري الاتصال بالسيرفر $etAddress:$etPort..."
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra("log_message")
            tvLogs.append("\n$log")
            // تمرير تلقائي للأسفل
            val scroll = tvLogs.parent as ScrollView
            scroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logReceiver)
    }
}
