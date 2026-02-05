package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.content.pm.PackageManager
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    
    companion object {
        var coreController: CoreController? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == "STOP_VPN") {
            stopV2Ray()
            return START_NOT_STICKY
        }

        val configContent = intent?.getStringExtra("V2RAY_CONFIG")
        if (configContent != null) {
            stopV2Ray()
            Thread {
                startV2Ray(configContent)
            }.start()
        }
        return START_STICKY
    }

    private fun startV2Ray(config: String) {
        try {
            sendLog("Configuring Network...")
            
            val builder = Builder()
            builder.setSession("V2Ray Pro")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            
            // --- الإضافة 1: خوادم DNS (ضروري للإنترنت) ---
            builder.addDnsServer("8.8.8.8")
            builder.addDnsServer("1.1.1.1")

            // --- الإضافة 2: منع التطبيق من خنق نفسه (Bypass Self) ---
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                sendLog("Warning: Could not bypass self.")
            }
            
            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                sendLog("Failed to establish VPN interface!")
                return
            }
            sendLog("VPN Interface Established.")

            val fd = vpnInterface!!.fd

            val callback = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long { 
                    sendLog("Status: $p1")
                    return 0 
                }
                override fun shutdown(): Long { 
                    sendLog("Core Shutdown.")
                    return 0 
                }
                override fun startup(): Long { 
                    sendLog("Core Started successfully!")
                    return 0 
                }
            }

            sendLog("Starting V2Ray Core...")
            coreController = Libv2ray.newCoreController(callback)
            coreController?.startLoop(config, fd)

        } catch (e: Exception) {
            e.printStackTrace()
            sendLog("Error: ${e.message}")
            stopV2Ray()
        }
    }

    private fun stopV2Ray() {
        try {
            coreController?.stopLoop()
            vpnInterface?.close()
            stopSelf()
            sendLog("VPN Stopped.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
