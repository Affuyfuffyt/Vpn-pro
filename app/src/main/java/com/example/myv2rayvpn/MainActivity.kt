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

    // تعريف حقول الواجهة
    private lateinit var etAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var etUserId: EditText
    private lateinit var etSni: EditText
    private lateinit var etPath: EditText
    private lateinit var tvLogs: TextView
    private lateinit var btnConnect: Button
    
    // متغير لحفظ الكود الكامل
    private var fullConfigString: String = ""

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 1. بناء الواجهة (تشبه DarkTunnel) ---
        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.setPadding(30, 30, 30, 30)
        mainLayout.setBackgroundColor(Color.WHITE)

        // شريط علوي (زر اللصق)
        val toolbar = LinearLayout(this)
        toolbar.orientation = LinearLayout.HORIZONTAL
        toolbar.gravity = Gravity.END
        val btnPaste = Button(this)
        btnPaste.text = "📋 لصق الكود (Paste)"
        btnPaste.setBackgroundColor(Color.parseColor("#EEEEEE"))
        btnPaste.setOnClickListener { pasteFromClipboard() }
        toolbar.addView(btnPaste)
        mainLayout.addView(toolbar)

        // دالة مساعدة لإنشاء الحقول بسرعة
        fun createField(label: String): EditText {
            val txt = TextView(this)
            txt.text = label
            txt.textSize = 12f
            txt.setTextColor(Color.GRAY)
            mainLayout.addView(txt)
            
            val edt = EditText(this)
            edt.textSize = 16f
            edt.setSingleLine()
            mainLayout.addView(edt)
            return edt
        }

        // إنشاء الحقول مثل الصورة
        etAddress = createField("Address / Host")
        etPort = createField("Port")
        etUserId = createField("UUID / Password")
        etSni = createField("SNI / Server Name")
        etPath = createField("Path")

        // مساحة فارغة
        val spacer = TextView(this)
        spacer.height = 50
        mainLayout.addView(spacer)

        // زر الاتصال الكبير
        btnConnect = Button(this)
        btnConnect.text = "CONNECT"
        btnConnect.textSize = 18f
        btnConnect.setTextColor(Color.WHITE)
        btnConnect.setBackgroundColor(Color.parseColor("#2E3A59")) // لون كحلي مثل الصورة
        btnConnect.minHeight = 150
        btnConnect.setOnClickListener { startVpn() }
        mainLayout.addView(btnConnect)

        // منطقة السجلات (Logs) مثل أسفل الصورة
        val logLabel = TextView(this)
        logLabel.text = "Connection Logs:"
        logLabel.setPadding(0, 40, 0, 10)
        mainLayout.addView(logLabel)

        val scroller = ScrollView(this)
        tvLogs = TextView(this)
        tvLogs.textSize = 12f
        tvLogs.setTextColor(Color.DKGRAY)
        tvLogs.text = "Ready to connect..."
        scroller.addView(tvLogs)
        // تحديد ارتفاع السجلات
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400)
        scroller.layoutParams = params
        mainLayout.addView(scroller)

        setContentView(mainLayout)

        // تسجيل مستقبل للسجلات (عشان نشوف شنو ديصير)
        registerReceiver(logReceiver, IntentFilter("VPN_LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED)
    }

    // --- 2. دالة اللصق والتحليل (Parsing) ---
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.primaryClip != null && clipboard.primaryClip!!.itemCount > 0) {
            val pasteData = clipboard.primaryClip!!.getItemAt(0).text.toString().trim()
            fullConfigString = pasteData // نحفظ الكود الأصلي
            parseConfig(pasteData)
            Toast.makeText(this, "تم لصق وتحليل الكود!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "الحافظة فارغة!", Toast.LENGTH_SHORT).show()
        }
    }

    // دالة ذكية لتحليل كود Vless
    private fun parseConfig(conf: String) {
        try {
            if (conf.startsWith("vless://")) {
                val uri = Uri.parse(conf)
                etUserId.setText(uri.userInfo) // UUID
                etAddress.setText(uri.host)    // IP
                etPort.setText(uri.port.toString()) // Port
                etSni.setText(uri.getQueryParameter("sni")) // SNI
                etPath.setText(uri.getQueryParameter("path")) // Path
            } else {
                tvLogs.append("\n⚠️ نوع الكود غير مدعوم حالياً في العرض، لكن سنحاول الاتصال به.")
            }
        } catch (e: Exception) {
            tvLogs.append("\n❌ خطأ في تحليل الكود: ${e.message}")
        }
    }

    // --- 3. تشغيل الـ VPN ---
    private fun startVpn() {
        if (fullConfigString.isEmpty()) {
            // إذا ضغط المستخدم اتصال بدون لصق، نحاول تجميع الكود من الحقول
            Toast.makeText(this, "يرجى لصق كود Vless أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1)
        } else {
            onActivityResult(1, Activity.RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, MyVpnService::class.java)
            intent.action = "START_VPN"
            intent.putExtra("V2RAY_CONFIG", fullConfigString)
            startService(intent)
            tvLogs.text = "Connecting to ${etAddress.text}..."
        }
    }

    // مستقبل الرسائل من الخدمة (لعرض السجلات)
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
