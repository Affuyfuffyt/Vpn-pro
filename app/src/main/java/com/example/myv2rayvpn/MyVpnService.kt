package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
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
                Thread { startV2Ray(config) }.start()
            }
        }
        return START_STICKY
    }

    private fun startV2Ray(config: String) {
        try {
            sendLog("🚀 Starting VPN Builder...")
            
            val builder = Builder()
            builder.setSession("V2Ray Diagnostic")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1280)
            builder.addDnsServer("8.8.8.8")
            
            // محاولة استثناء التطبيق نفسه لمنع التكرار (Loop)
            try {
                builder.addDisallowedApplication(packageName)
                sendLog("✅ App excluded from VPN (Loop prevention)")
            } catch (e: Exception) {
                sendLog("⚠️ Could not exclude app: ${e.message}")
            }
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                sendLog("❌ ERROR: Failed to create VPN Interface!")
                return
            }
            sendLog("✅ VPN Interface Created (FD: ${vpnInterface!!.fd})")

            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { 
                    // هنا سنرى رسائل Xray الحقيقية
                    sendLog("CORE: $p1") 
                    return 0 
                }
                override fun shutdown(): Long { sendLog("🔻 Core Shutdown"); return 0 }
                override fun startup(): Long { sendLog("✅ Core Started!"); return 0 }
            }

            sendLog("⚙️ Initializing LibV2Ray...")
            coreController = Libv2ray.newCoreController(callback)
            
            sendLog("🔄 Sending config to Core...")
            coreController?.startLoop(config, vpnInterface!!.fd)

        } catch (e: Exception) {
            sendLog("❌ CRITICAL ERROR: ${e.message}")
            e.printStackTrace()
            stopV2Ray()
        }
    }

    private fun stopV2Ray() {
        try {
            coreController?.stopLoop()
            vpnInterface?.close()
            sendLog("🛑 VPN Service Stopped")
        } catch (e: Exception) { }
    }
    
    private fun sendLog(msg: String) {
        val intent = Intent("VPN_LOG_UPDATE")
        intent.putExtra("log_message", msg)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopV2Ray()
    }
}
