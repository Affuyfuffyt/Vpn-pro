package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    // تعريف المتحكم الجديد بالمكتبة
    private var coreController: libv2ray.CoreController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val configContent = intent?.getStringExtra("V2RAY_CONFIG")

        if (configContent != null) {
            // تشغيل الـ VPN في خيط منفصل لتجنب تعليق التطبيق
            Thread {
                startV2Ray(configContent)
            }.start()
        }
        return START_STICKY
    }

    private fun startV2Ray(config: String) {
        try {
            // 1. إعداد نفق الـ VPN
            val builder = Builder()
            builder.setSession("MyV2RayApp")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            
            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                return
            }

            // 2. الحصول على رقم الملف (File Descriptor) لتمريره للمكتبة
            val fd = vpnInterface!!.fd

            // 3. تجهيز "مستمع" (Callback) - هذا مطلوب في النسخة الجديدة
            val callback = object : CoreCallbackHandler {
                 override fun onEmitStatus(p0: Long, p1: String?) {
                    // يمكن إضافة كود هنا لمراقبة الحالة، سنتركه فارغاً حالياً
                }
            }

            // 4. تشغيل القلب (Core) بالطريقة الجديدة
            coreController = Libv2ray.newCoreController(callback)
            coreController?.startLoop(config, fd.toLong()) // تمرير الكود ورقم النفق

        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // إيقاف الاتصال بالطريقة الجديدة
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface?.close()
    }
}
