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
    private lateinit var tvStatus: TextView
    private lateinit var btnConnect: Button
    
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // السماح بالشبكة للفحص
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(30, 30, 30, 30)
        mainLayout.setBackgroundColor(Color.parseColor("#E0E0E0"))

        // شاشة الحالة
        val statusLayout = LinearLayout(this)
        statusLayout.orientation = LinearLayout.VERTICAL
        statusLayout.setPadding(20, 20, 20, 20)
        statusLayout.setBackgroundColor(Color.BLACK)
        
        val lblStatus = TextView(this)
        lblStatus.text = "🌍 GLOBAL CONNECTION STATUS"
        lblStatus.setTextColor(Color.YELLOW)
        lblStatus.gravity = android.view.Gravity.CENTER
        statusLayout.addView(lblStatus)

        tvStatus = TextView(this)
        tvStatus.text = "DISCONNECTED"
        tvStatus.textSize = 18f
        tvStatus.setTextColor(Color.RED)
        tvStatus.gravity = android.view.Gravity.CENTER
        tvStatus.setPadding(0, 10, 0, 0)
        statusLayout.addView(tvStatus)
        mainLayout.addView(statusLayout)

        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق كود (VLESS WS)"
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

        etAddress = createField("IP Address", "")
        etPort = createField("Port", "80")
        etUserId = createField("UUID", "")
        etSni = createField("Host Header", "")
        etPath = createField("Path", "/")

        val spacer = TextView(this)
        spacer.height = 30
        mainLayout.addView(spacer)

        btnConnect = Button(this)
        btnConnect.text = "🔥 تشغيل (FIXED ROUTING)"
        btnConnect.textSize = 16f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#D32F2F")) // أحمر قوي
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 10f
        tvLogs.setTextColor(Color.BLACK)
        tvLogs.text = "Waiting..."
        scroller.addView(tvLogs)
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
    }

    private val internetCheckRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                Thread {
                    val result = checkRealInternet()
                    runOnUiThread {
                        tvStatus.text = result
                        if (result.contains("ONLINE")) tvStatus.setTextColor(Color.GREEN)
                        else tvStatus.setTextColor(Color.RED)
                    }
                }.start()
                handler.postDelayed(this, 2000)
            }
        }
    }

    private fun checkRealInternet(): String {
        return try {
            val url = URL("https://www.google.com")
            val connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "HEAD"
            val code = connection.responseCode
            connection.disconnect()
            if (code == 200) "✅ ONLINE" else "⚠️ Error: $code"
        } catch (e: Exception) { "❌ NO NET" }
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

    // --- التصحيح هنا: استبدال geoip:private بالأرقام ---
    private fun createJsonConfig(): String {
        val address = etAddress.text.toString()
        val port = etPort.text.toString().toIntOrNull() ?: 80
        val uuid = etUserId.text.toString()
        val hostHeader = etSni.text.toString()
        val path = etPath.text.toString()

        return """
        {
            "log": { "loglevel": "warning" },
            "inbounds": [], 
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
                        "outboundTag": "direct", 
                        "ip": [ "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8" ] 
                    },
                    { 
                        "type": "field", 
                        "outboundTag": "proxy", 
                        "network": "tcp,udp" 
                    } 
                ]
            }
        }
        """.trimIndent()
    }
    // لاحظ: لقد مسحت "geoip:private" تماماً واستبدلتها بالأرقام أعلاه.
    // هذا سيحل مشكلة "failed to load file: geoip.dat" نهائياً.

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
            
            tvLogs.text = "جاري الاتصال..."
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
