package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    // متغير ثابت للتحكم في المكتبة حتى لا نفقد الاتصال به
    companion object {
        var coreController: CoreController? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "STOP_VPN") {
            stopV2Ray()
            return START_NOT_STICKY
        }

        // إذا كان الأمر تشغيل
        val configContent = intent?.getStringExtra("V2RAY_CONFIG")
        if (configContent != null) {
            // نوقف أي اتصال سابق أولاً
            stopV2Ray()
            
            Thread {
                startV2Ray(configContent)
            }.start()
        }
        return START_STICKY
    }

    private fun startV2Ray(config: String) {
        try {
            // إعدادات الشبكة الوهمية
            val builder = Builder()
            builder.setSession("V2Ray VPN")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            
            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                return
            }

            val fd = vpnInterface!!.fd

            // تجهيز المستمع (Callback)
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { return 0 }
                override fun shutdown(): Long { return 0 }
                override fun startup(): Long { return 0 }
            }

            // تشغيل المكتبة
            coreController = Libv2ray.newCoreController(callback)
            coreController?.startLoop(config, fd)

        } catch (e: Exception) {
            e.printStackTrace()
            stopV2Ray()
        }
    }

    private fun stopV2Ray() {
        try {
            if (coreController != null) {
                coreController?.stopLoop()
                coreController = null
            }
            if (vpnInterface != null) {
                vpnInterface?.close()
                vpnInterface = null
            }
            stopSelf() // قتل الخدمة تماماً
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopV2Ray()
    }
}
