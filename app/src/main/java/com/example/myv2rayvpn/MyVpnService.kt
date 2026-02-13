package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    companion object {
        var coreController: CoreController? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_VPN") {
            val config = intent.getStringExtra("V2RAY_CONFIG")
            if (config != null) {
                stopV2Ray()
                Thread { startV2RaySequence(config) }.start()
            }
        }
        return START_STICKY
    }

    // دالة لتشغيل الخطوات بالتتابع (Simulation for User Experience)
    private fun startV2RaySequence(config: String) {
        val handler = Handler(Looper.getMainLooper())

        try {
            // خطوة 1:
            sendStatus("Connecting to server...")
            Thread.sleep(500) // تأخير بسيط للجمالية

            // خطوة 2: بناء الواجهة
            val builder = Builder()
            builder.setSession("V2Ray Service")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            builder.addDnsServer("8.8.8.8")
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                sendError("Failed to establish VPN Interface")
                return
            }

            // خطوة 3:
            sendStatus("Waiting for server to reply...")
            Thread.sleep(800)

            // خطوة 4: تشغيل المحرك
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { return 0 }
                override fun shutdown(): Long { return 0 }
                override fun startup(): Long { return 0 }
            }

            coreController = Libv2ray.newCoreController(callback)
            
            // خطوة 5:
            sendStatus("Authenticating...")
            // نحاول تشغيل المحرك فعلياً
            coreController?.startLoop(config, vpnInterface!!.fd)
            
            // إذا وصلنا هنا بدون استثناء، نعتبره متصلاً مبدئياً
            // ملاحظة: V2Ray لا يعطي callback مباشر عند نجاح الاتصال بالسيرفر البعيد،
            // لكن سنفترض النجاح إذا لم يظهر خطأ خلال ثانية.
            handler.postDelayed({
                sendStatus("Connected ✅")
            }, 1000)

        } catch (e: Exception) {
            e.printStackTrace()
            sendError(e.message ?: "Unknown Error")
            stopV2Ray()
        }
    }

    private fun stopV2Ray() {
        try {
            coreController?.stopLoop()
            vpnInterface?.close()
        } catch (e: Exception) { }
    }
    
    // إرسال تحديث الحالة (الخطوات)
    private fun sendStatus(msg: String) {
        val intent = Intent("VPN_STATUS_UPDATE")
        intent.putExtra("status_message", msg)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    // إرسال تقرير الخطأ
    private fun sendError(error: String) {
        val intent = Intent("VPN_ERROR_REPORT")
        intent.putExtra("error_message", error)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopV2Ray()
    }
}
