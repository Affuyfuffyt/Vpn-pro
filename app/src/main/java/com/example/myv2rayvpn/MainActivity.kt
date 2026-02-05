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
import android.view.Gravity
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

        // زر اللصق
        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق الكود (PASTE)"
        btnPaste.setBackgroundColor(Color.parseColor("#EEEEEE"))
        btnPaste.setOnClickListener { pasteFromClipboard() }
        mainLayout.addView(btnPaste)

        fun createField(label: String, default: String = ""): EditText {
            val txt = TextView(this)
            txt.text = label
            txt.textSize = 12f
            txt.setTextColor(Color.GRAY)
            mainLayout.addView(txt)
            
            val edt = EditText(this)
            edt.textSize = 16f
            edt.setText(default)
            edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        etAddress = createField("Address / Host")
        etPort = createField("Port", "443")
        etUserId = createField("UUID / Password")
        etSni = createField("SNI / Server Name")
        etPath = createField("Path", "/")

        val spacer = TextView(this)
        spacer.height = 50
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "CONNECT"
        btnConnect.textSize = 18f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#2E3A59")) 
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        val logLabel = TextView(this)
        logLabel.text = "Connection Logs:"
        logLabel.setPadding(0, 40, 0, 10)
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 12f
        tvLogs.setTextColor(Color.DKGRAY)
        tvLogs.text = "Ready..."
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
                etSni.setText(uri.getQueryParameter("sni") ?: "")
                etPath.setText(uri.getQueryParameter("path") ?: "/")
                Toast.makeText(this, "تم استخراج بيانات VLESS", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "يرجى نسخ كود Vless صحيح", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في قراءة الكود", Toast.LENGTH_SHORT).show()
        }
    }

    // --- التعديل الجذري: إضافة مخرج Freedom وقواعد التوجيه ---
    private fun createJsonConfig(): String {
        val address = etAddress.text.toString()
        val port = etPort.text.toString().toIntOrNull() ?: 443
        val uuid = etUserId.text.toString()
        val sni = etSni.text.toString()
        val path = etPath.text.toString()

        // هذا الـ JSON هو السر: يحتوي على مخرجين (Proxy و Direct)
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
                                "users": [
                                    { "id": "$uuid", "encryption": "none" }
                                ]
                            }
                        ]
                    },
                    "streamSettings": {
                        "network": "ws",
                        "security": "none",
                        "wsSettings": {
                            "path": "$path",
                            "headers": { "Host": "$sni" }
                        }
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
                        "ip": ["geoip:private"],
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
            tvLogs.text = "Connecting to ${etAddress.text}..."
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val log = intent?.getStringExtra("log_message")
            tvLogs.append("\n$log")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logReceiver)
    }
}
