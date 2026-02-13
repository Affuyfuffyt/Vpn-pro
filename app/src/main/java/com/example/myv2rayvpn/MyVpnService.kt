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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_VPN") {
            val config = intent.getStringExtra("V2RAY_CONFIG")
            if (config != null) {
                stopV2Ray()
                executor.execute { startV2RayProcess(config) }
            }
        }
        return START_STICKY
    }

    private fun startV2RayProcess(config: String) {
        try {
            updateStatus("Initializing...", false)

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

            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { return 0 }
                override fun shutdown(): Long { return 0 }
                override fun startup(): Long { return 0 }
            }
            coreController = Libv2ray.newCoreController(callback)
            coreController?.startLoop(config, vpnInterface!!.fd)

            updateStatus("Authenticating (Wait 15s)...", false)
            
            // ننتظر تشغيل المحرك
            Thread.sleep(2000) 
            
            // نفحص الاتصال
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

    private fun checkRealConnection(): Boolean {
        try {
            // نستخدم بروكسي SOCKS المحلي
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
            
            // نستخدم رابطاً خفيفاً جداً وبدون HTTPS لتسريع العملية
            val url = URL("http://www.gstatic.com/generate_204") 
            val connection = url.openConnection(proxy) as HttpURLConnection
            
            // رفعنا الوقت لـ 15 ثانية (لأن DarkTunnel أخذ 7 ثواني)
            connection.connectTimeout = 15000 
            connection.readTimeout = 15000
            connection.requestMethod = "HEAD"

            val code = connection.responseCode
            connection.disconnect()

            // كود 204 يعني نجاح تام بدون محتوى
            return code == 204 || code == 200
        } catch (e: Exception) {
            return false
        }
    }

    private fun stopV2Ray() {
        try {
            coreController?.stopLoop()
            vpnInterface?.close()
        } catch (e: Exception) { }
    }
    
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
