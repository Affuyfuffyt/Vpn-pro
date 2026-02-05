package com.example.myv2rayvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.view.Gravity

class MainActivity : Activity() {

    private lateinit var configInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. تصميم الواجهة برمجياً
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(50, 50, 50, 50)

        // 2. مربع إدخال الكود (Vless/Vmess)
        configInput = EditText(this)
        configInput.hint = "الصق كود الـ vless أو vmess هنا"
        configInput.minLines = 3
        layout.addView(configInput)

        // 3. زر التشغيل
        val startButton = Button(this)
        startButton.text = "تشغيل VPN"
        startButton.setOnClickListener {
            val config = configInput.text.toString()
            if (config.isEmpty()) {
                Toast.makeText(this, "يرجى لصق الكود أولاً", Toast.LENGTH_SHORT).show()
            } else {
                // طلب إذن النظام
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    startActivityForResult(intent, 1)
                } else {
                    onActivityResult(1, Activity.RESULT_OK, null)
                }
            }
        }
        layout.addView(startButton)

        // 4. زر الإيقاف
        val stopButton = Button(this)
        stopButton.text = "إيقاف VPN"
        stopButton.setOnClickListener {
            val intent = Intent(this, MyVpnService::class.java)
            intent.action = "STOP_VPN" // أمر خاص للإيقاف
            startService(intent)
            Toast.makeText(this, "تم طلب الإيقاف", Toast.LENGTH_SHORT).show()
        }
        layout.addView(stopButton)

        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            // تشغيل الخدمة وإرسال الكود لها
            val intent = Intent(this, MyVpnService::class.java)
            intent.action = "START_VPN"
            intent.putExtra("V2RAY_CONFIG", configInput.text.toString())
            startService(intent)
            Toast.makeText(this, "جاري الاتصال...", Toast.LENGTH_SHORT).show()
        }
    }
}
