package com.example.myv2rayvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تصميم الشاشة برمجياً (زر واحد فقط)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = android.view.Gravity.CENTER

        val button = Button(this)
        button.text = "تشغيل VPN"
        button.setOnClickListener {
            // طلب إذن الـ VPN من النظام
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, 1)
            } else {
                onActivityResult(1, Activity.RESULT_OK, null)
            }
        }

        layout.addView(button)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            // تشغيل الخدمة عند الموافقة
            val intent = Intent(this, MyVpnService::class.java)
            // ملاحظة: هنا سنضع كود الكونق لاحقاً، حالياً سنرسل نصاً تجريبياً
            intent.putExtra("V2RAY_CONFIG", "vless://...") 
            startService(intent)
            Toast.makeText(this, "تم إرسال أمر التشغيل", Toast.LENGTH_SHORT).show()
        }
    }
}
