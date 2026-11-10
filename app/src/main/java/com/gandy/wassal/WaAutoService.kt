package com.gandy.wassal

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar

/**
 * وضع "أوتوماتيك كامل": الخدمة بتفتح شات كل عميل وتدوس زرار الإرسال بنفسها،
 * وبعدين تستنى وقت عشوائي وتروح للي بعده لحد ما الطابور يخلص أو توصل للحد اليومي.
 * الخدمة مش بتعمل أي حاجة غير وهي "شغالة" بأمر من صاحب المحل.
 */
class WaAutoService : AccessibilityService() {

    companion object {
        @Volatile var instance: WaAutoService? = null
        var running = false
            private set
        var status: String = "متوقف"
            private set
        /** بتتنادى على الـ Main thread كل ما الحالة تتغير (شاشة الإرسال بتسمعها) */
        var listener: (() -> Unit)? = null

        private const val SEND_TIMEOUT_MS = 25_000L

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, WaAutoService::class.java).flattenToString()
            return flat.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var current: QueueItem? = null
    private var waitingForSend = false
    private lateinit var prefs: Prefs
    private lateinit var db: Db

    override fun onServiceConnected() {
        instance = this
        prefs = Prefs(this)
        db = Db.get(this)
    }

    override fun onDestroy() {
        stopQueue("الخدمة اتقفلت")
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    // ---------------- التحكم ----------------

    fun startQueue() {
        if (running) return
        if (!License.isValid(this)) { setStatus("التطبيق مش متفعّل"); return }
        running = true
        setStatus("بدأ الإرسال")
        h.post { next() }
    }

    fun stopQueue(reason: String = "متوقف") {
        running = false
        waitingForSend = false
        current = null
        h.removeCallbacksAndMessages(null)
        setStatus(reason)
    }

    private fun next() {
        if (!running) return
        if (prefs.limitReached) {
            scheduleResumeTomorrow()
            stopQueue("وصلت للحد اليومي (${prefs.dailyLimit}). هيكمل بكرة الساعة ${prefs.resumeHour}")
            return
        }
        val item = db.nextPending()
        if (item == null) { stopQueue("الطابور خلص ✔"); return }

        val pkg = prefs.waPackage
        if (!Wa.isInstalled(this, pkg)) { stopQueue("واتساب المختار مش متسطّب"); return }

        current = item
        waitingForSend = true
        setStatus("بيبعت لـ ${item.customerName}")
        try {
            startActivity(Wa.buildIntent(this, item, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            fail(item, "تعذّر فتح واتساب")
            return
        }
        // لو زرار الإرسال ماظهرش (الرقم مش على واتساب مثلًا) نعتبرها فشل ونكمل
        h.postDelayed({
            if (waitingForSend && current?.id == item.id) fail(item, "مفيش رد من واتساب")
        }, SEND_TIMEOUT_MS)
    }

    private fun fail(item: QueueItem, why: String) {
        waitingForSend = false
        db.mark(item.id, Db.FAILED)
        setStatus("فشل: ${item.customerName} – $why")
        backToApp()
        h.postDelayed({ next() }, 4000)
    }

    private fun onSentClicked(item: QueueItem) {
        waitingForSend = false
        db.mark(item.id, Db.SENT)
        prefs.incSent()
        val delay = prefs.randomDelayMs()
        setStatus("اتبعت لـ ${item.customerName}. اللي بعده بعد ${delay / 1000} ث")
        h.postDelayed({ backToApp() }, 2500)
        h.postDelayed({ next() }, delay)
    }

    private fun backToApp() {
        performGlobalAction(GLOBAL_ACTION_BACK)
        h.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 700)
    }

    // ---------------- قراءة شاشة واتساب ----------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!running || !waitingForSend) return
        val item = current ?: return
        val pkg = prefs.waPackage
        if (event?.packageName?.toString() != pkg) return
        val root = rootInActiveWindow ?: return

        val btn = findSendButton(root, pkg) ?: return
        h.postDelayed({
            if (!waitingForSend || current?.id != item.id) return@postDelayed
            if (clickNode(btn)) onSentClicked(item)
        }, 800) // تأخير بسيط كأن واحد بيدوس بإيده
    }

    private fun findSendButton(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        // 1) بالـ ID (شاشة الشات وشاشة إرسال الصورة)
        for (id in listOf("$pkg:id/send", "$pkg:id/send_btn", "$pkg:id/send_button")) {
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        // 2) بالوصف لو واتساب غيّر الـ IDs
        for (label in listOf("Send", "إرسال", "ارسال")) {
            root.findAccessibilityNodeInfosByText(label)?.firstOrNull {
                it.isVisibleToUser && (it.contentDescription?.toString()?.equals(label, true) == true)
            }?.let { return it }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    // ---------------- مساعدات ----------------

    private fun setStatus(s: String) {
        status = s
        h.post { listener?.invoke() }
    }

    private fun scheduleResumeTomorrow() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, prefs.resumeHour); set(Calendar.MINUTE, (0..20).random()); set(Calendar.SECOND, 0)
        }
        val pi = PendingIntent.getBroadcast(
            this, 1, Intent(this, ResumeReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }
}

/** بيصحّى الإرسال تاني يوم لو الطابور لسه فيه رسايل */
class ResumeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (Prefs(ctx).mode == Prefs.MODE_AUTO && Db.get(ctx).pendingCount() > 0) {
            WaAutoService.instance?.startQueue()
        }
    }
}
