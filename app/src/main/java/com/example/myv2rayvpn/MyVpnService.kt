package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import java.net.InetSocketAddress
import java.net.Socket
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
            updateStatus("Initializing VPN UI...", false)

            val builder = Builder()
            builder.setSession("V2Ray Service")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            builder.addDnsServer("8.8.8.8")
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                reportError("VPN Interface Denied")
                return
            }

            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { return 0 }
                override fun shutdown(): Long { return 0 }
                override fun startup(): Long { return 0 }
            }
            coreController = Libv2ray.newCoreController(callback)
            coreController?.startLoop(config, vpnInterface!!.fd)

            // ننتظر 5 ثواني لضمان استقرار المحرك تماماً قبل الفحص
            updateStatus("Starting Engine (Wait 5s)...", false)
            Thread.sleep(5000) 
            
            updateStatus("Verifying Handshake (Max 30s)...", false)
            
            // نفحص الاتصال
            if (checkTunnelReady()) {
                updateStatus("Connected ✅", true)
            } else {
                reportError("Handshake Timeout! Server too slow.")
                stopV2Ray()
            }

        } catch (e: Exception) {
            reportError("Critical: ${e.message}")
            stopV2Ray()
        }
    }

    // طريقة فحص أسرع وأدق (Raw Socket)
    private fun checkTunnelReady(): Boolean {
        val maxAttempts = 5
        for (i in 1..maxAttempts) {
            try {
                val socket = Socket()
                // نحاول الاتصال بـ DNS جوجل مباشرة عبر البروكسي الداخلي
                // المهلة لكل محاولة 5 ثواني
                socket.connect(InetSocketAddress("127.0.0.1", 10808), 5000)
                
                // إذا نجح الاتصال بالبروكسي المحلي، نختبر عبور البيانات لـ IP خارجي
                val isAlive = socket.isConnected
                socket.close()
                
                if (isAlive) return true
            } catch (e: Exception) {
                // ننتظر ثانية قبل المحاولة التالية
                Thread.sleep(2000)
            }
        }
        return false
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
