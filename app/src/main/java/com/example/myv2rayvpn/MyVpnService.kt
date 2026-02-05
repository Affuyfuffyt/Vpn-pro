package com.example.myv2rayvpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var coreController: CoreController? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val configContent = intent?.getStringExtra("V2RAY_CONFIG")

        if (configContent != null) {
            Thread {
                startV2Ray(configContent)
            }.start()
        }
        return START_STICKY
    }

    private fun startV2Ray(config: String) {
        try {
            val builder = Builder()
            builder.setSession("MyV2RayApp")
            builder.addAddress("10.0.0.2", 24)
            builder.addRoute("0.0.0.0", 0)
            builder.setMtu(1500)
            
            vpnInterface = builder.establish()

            if (vpnInterface == null) {
                return
            }

            // التصحيح 1: الحصول على الرقم كـ Int كما يطلبه النظام
            val fd = vpnInterface!!.fd

            // تعريف المستمع (Callback) مع الدوال الناقصة
            val callback = object : CoreCallbackHandler {
                // التصحيح 2: إرجاع قيمة 0 (Long)
                 override fun onEmitStatus(p0: Long, p1: String?): Long {
                    return 0 
                }
                
                // التصحيح 3: إضافة دالة shutdown الناقصة وإرجاع 0
                override fun shutdown(): Long {
                    return 0 
                }
            }

            coreController = Libv2ray.newCoreController(callback)
            
            // التصحيح 4: تمرير fd مباشرة (لأنه Int) وحذف .toLong() التي سببت المشكلة
            coreController?.startLoop(config, fd) 

        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            coreController?.stopLoop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        vpnInterface?.close()
    }
}
