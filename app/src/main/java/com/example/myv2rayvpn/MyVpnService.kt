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
            val builder = Builder()
            builder.setSession("HTTP Proxy VPN")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            builder.addDnsServer("8.8.8.8") // مهم جداً للـ HTTP
            
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                sendLog("فشل إنشاء واجهة الـ VPN")
                return
            }
            
            val fd = vpnInterface!!.fd

            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { 
                    return 0 
                }
                override fun shutdown(): Long { return 0 }
                override fun startup(): Long { 
                    sendLog("تم تشغيل المحرك (HTTP Mode)")
                    return 0 
                }
            }

            coreController = Libv2ray.newCoreController(callback)
            coreController?.startLoop(config, fd)

        } catch (e: Exception) {
            e.printStackTrace()
            sendLog("خطأ: ${e.message}")
            stopV2Ray()
        }
    }

    private fun stopV2Ray() {
        try {
            coreController?.stopLoop()
            vpnInterface?.close()
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
