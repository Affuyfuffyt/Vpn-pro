import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import libv2ray.Libv2ray // استدعاء مكتبة Go

class MyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val configContent = intent?.getStringExtra("V2RAY_CONFIG") // استلام كود السيرفر

        if (configContent != null) {
            startV2Ray(configContent)
        }
        return START_STICKY
    }

    private fun startV2Ray(config: String) {
        // 1. إعداد نفق الـ VPN (Builder)
        val builder = Builder()
        builder.setSession("MyV2RayApp")
        builder.addAddress("10.0.0.2", 24) // عنوان وهمي داخلي
        builder.addRoute("0.0.0.0", 0) // توجيه كل الترافيك عبر الـ VPN
        
        // 2. إنشاء الواجهة
        vpnInterface = builder.establish()

        // 3. تشغيل قلب V2Ray (Go Core)
        // ملاحظة: هنا يتم استدعاء دالة Go لتشغيل البروكسي وتمرير الـ FileDescriptor
        // هذا مثال تبسيطي، التنفيذ الفعلي يحتاج LibV2ray.startV2Ray
        Thread {
            Libv2ray.startV2Ray(config) 
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        Libv2ray.stopV2Ray() // إيقاف الاتصال
        vpnInterface?.close()
    }
}
