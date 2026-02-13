package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    companion object {
        var coreController: CoreController? = null
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_VPN") {
            val config = intent.getStringExtra("V2RAY_CONFIG")
            if (config != null) {
                stopV2Ray()
                // نبدأ العملية في مسار خلفي
                executor.execute { startV2RayProcess(config) }
            }
        }
        return START_STICKY
    }

    private fun startV2RayProcess(config: String) {
        try {
            // 1. إظهار حالة البدء
            updateStatus("Initializing Core...", false)

            // 2. إعداد واجهة الـ VPN
            val builder = Builder()
            builder.setSession("V2Ray Service")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            builder.addDnsServer("8.8.8.8")
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                reportError("Failed to create VPN Interface")
                return
            }

            // 3. تشغيل المحرك (LibV2Ray)
            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { return 0 }
                override fun shutdown(): Long { return 0 }
                override fun startup(): Long { return 0 }
            }
            coreController = Libv2ray.newCoreController(callback)
            coreController?.startLoop(config, vpnInterface!!.fd)

            // 4. مرحلة التحقق الحقيقي (The Real Test)
            updateStatus("Authenticating with Server...", false)
            
            // ننتظر قليلاً ليعمل المحرك
            Thread.sleep(1000) 
            
            // نفحص الاتصال الحقيقي
            if (checkRealConnection()) {
                updateStatus("Connected ✅", true)
            } else {
                reportError("Handshake Failed! Check IP/UUID")
                stopV2Ray()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            reportError("Error: ${e.message}")
            stopV2Ray()
        }
    }

    // --- دالة الفحص الحقيقي (السر هنا) ---
    private fun checkRealConnection(): Boolean {
        updateStatus("Waiting for reply...", false)
        try {
            // نحاول الاتصال بجوجل عبر البروكسي المحلي (10808) الذي أنشأه V2Ray
            // إذا كان إعداد السيرفر خطأ، هذا الاتصال سيفشل فوراً
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
            val url = URL("https://www.google.com")
            val connection = url.openConnection(proxy) as HttpURLConnection
            
            connection.connectTimeout = 5000 // مهلة 5 ثواني
            connection.readTimeout = 5000
            connection.requestMethod = "HEAD" // طلب خفيف

            val code = connection.responseCode
            connection.disconnect()

            // إذا رجع كود 200 (OK) أو أي رد حقيقي، فالاتصال ناجح
            return code > 0
        } catch (e: Exception) {
            // فشل الاتصال يعني أن السيرفر لا يرد أو الإعدادات خاطئة
            return false
        }
    }

    private fun stopV2Ray() {
        try {
            coreController?.stopLoop()
            vpnInterface?.close()
        } catch (e: Exception) { }
    }
    
    // دوال مساعدة لإرسال التحديثات للواجهة
    private fun updateStatus(msg: String, isSuccess: Boolean) {
        val intent = Intent("VPN_STATUS_UPDATE")
        intent.putExtra("status_message", msg)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun reportError(error: String) {
        val intent = Intent("VPN_ERROR_REPORT")
        intent.putExtra("error_message", error)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopV2Ray()
        executor.shutdownNow()
    }
}
