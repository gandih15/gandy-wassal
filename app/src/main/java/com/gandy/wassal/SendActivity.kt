package com.gandy.wassal

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * شاشة الإرسال:
 *  - "نص أوتوماتيك": بنفتح شات العميل، صاحب المحل يدوس إرسال ويرجع، والتطبيق يسجّل ويفتح اللي بعده.
 *  - "أوتوماتيك كامل": WaAutoService بتكمّل لوحدها.
 */
class SendActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var db: Db
    private val h = Handler(Looper.getMainLooper())

    private lateinit var ring: CircularProgressIndicator
    private lateinit var percent: TextView
    private lateinit var counts: TextView
    private lateinit var statusTv: TextView
    private lateinit var listBox: LinearLayout

    private var semiRunning = false
    private var semiCurrent: QueueItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        db = Db.get(this)
        build()
        if (intent.getBooleanExtra("autostart", false)) h.postDelayed({ start() }, 700)
    }

    private fun build() {
        val auto = prefs.mode == Prefs.MODE_AUTO
        val body = Ui.screen(this, "الإرسال",
            if (auto) "أوتوماتيك كامل" else "نص أوتوماتيك · دوس إرسال في واتساب وارجع",
            Ui.BRAND, action = "مسح الطابور" to { confirmClear() })

        // ----- دايرة التقدّم -----
        val top = Ui.card(this)
        val ringBox = FrameLayout(this)
        ring = CircularProgressIndicator(this).apply {
            indicatorSize = Ui.dp(this@SendActivity, 92)
            trackThickness = Ui.dp(this@SendActivity, 9)
            setIndicatorColor(Ui.BRAND)
            trackColor = Ui.soft(this@SendActivity, Ui.BRAND)
            trackCornerRadius = Ui.dp(this@SendActivity, 5)
            max = 100
        }
        percent = TextView(this).apply {
            textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Ui.BRAND); gravity = Gravity.CENTER
        }
        ringBox.addView(ring, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        ringBox.addView(percent, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        top.addView(ringBox, LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)).apply { marginEnd = Ui.dp(this@SendActivity, 16) })

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        counts = Ui.title(this, "", 19f)
        statusTv = Ui.sub(this, "", 13f)
        info.addView(counts); info.addView(statusTv)
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(top)

        body.addView(Ui.row(this,
            Ui.primary(this, "▶  ابدأ / كمّل") { start() },
            Ui.secondary(this, "⏸  إيقاف", Ui.RED) { stop() }
        ))
        if (!auto) body.addView(Ui.secondary(this, "تخطّي العميل الجاي", Ui.AMBER) { skipNext() })
        else if (!WaAutoService.isEnabled(this)) body.addView(Ui.note(this, "خدمة الإرسال التلقائي مش متفعّلة. دوس ابدأ وهتفتحلك الإعدادات.", Ui.RED))

        body.addView(Ui.section(this, "السجل"))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(listBox)
    }

    override fun onResume() {
        super.onResume()
        WaAutoService.listener = { refresh() }
        // رجع من واتساب في وضع "نص أوتوماتيك" → اتبعتت، وننقل للي بعده
        val cur = semiCurrent
        if (semiRunning && cur != null) {
            db.mark(cur.id, Db.SENT)
            prefs.incSent()
            semiCurrent = null
            h.postDelayed({ openNextSemi() }, 1200)
        }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        WaAutoService.listener = null
    }

    private fun start() {
        if (db.pendingCount() == 0) { toast("الطابور فاضي"); return }
        if (!Wa.isInstalled(this, prefs.waPackage)) { toast("واتساب المختار في الإعدادات مش متسطّب"); return }
        if (prefs.mode == Prefs.MODE_AUTO) {
            val svc = WaAutoService.instance
            if (svc == null || !WaAutoService.isEnabled(this)) {
                AlertDialog.Builder(this).setTitle("خدمة الإرسال مش متفعّلة")
                    .setMessage("فعّل \"غاندي وصّل – الإرسال التلقائي\" من إعدادات الوصول، وارجع دوس ابدأ.")
                    .setPositiveButton("افتح الإعدادات") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .setNegativeButton("إلغاء", null).show()
                return
            }
            svc.startQueue()
        } else {
            semiRunning = true
            openNextSemi()
        }
        refresh()
    }

    private fun stop() {
        semiRunning = false
        semiCurrent = null
        h.removeCallbacksAndMessages(null)
        WaAutoService.instance?.stopQueue()
        refresh()
    }

    private fun openNextSemi() {
        if (!semiRunning) return
        if (prefs.limitReached) { semiRunning = false; toast("وصلت للحد اليومي (${prefs.dailyLimit})"); refresh(); return }
        val item = db.nextPending()
        if (item == null) { semiRunning = false; toast("الطابور خلص ✔"); refresh(); return }
        semiCurrent = item
        try {
            startActivity(Wa.buildIntent(this, item, prefs.waPackage))
        } catch (e: Exception) {
            db.mark(item.id, Db.FAILED); semiCurrent = null
            h.postDelayed({ openNextSemi() }, 800)
        }
    }

    private fun skipNext() {
        val cur = semiCurrent ?: db.nextPending() ?: return
        db.mark(cur.id, Db.FAILED)
        semiCurrent = null
        refresh()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this).setMessage("هتتمسح كل الرسايل اللي لسه متبعتتش. متأكد؟")
            .setPositiveButton("امسح") { _, _ -> stop(); db.clearPending(); refresh() }
            .setNegativeButton("لأ", null).show()
    }

    private fun refresh() {
        val items = db.recentQueue(80)
        val pending = db.pendingCount()
        val sentToday = prefs.sentToday
        val doneInView = items.count { it.status != Db.PENDING }
        val pct = if (doneInView + pending == 0) 0 else doneInView * 100 / (doneInView + pending)
        ring.setProgressCompat(pct, true)
        percent.text = "$pct%"
        counts.text = "باقي $pending رسالة"

        val state = when {
            prefs.mode == Prefs.MODE_AUTO -> WaAutoService.status
            semiRunning -> "شغّال"
            else -> "متوقف"
        }
        statusTv.text = "الحالة: $state\nاتبعت النهارده $sentToday من ${prefs.dailyLimit}"

        listBox.removeAllViews()
        if (items.isEmpty()) listBox.addView(Ui.empty(this, "📭", "مفيش رسايل", "ابدأ حملة من الرئيسية"))
        items.forEach { q ->
            val badge = when (q.status) {
                Db.SENT -> "اتبعت" to Ui.GREEN
                Db.FAILED -> "فشل" to Ui.RED
                else -> "في الطابور" to Ui.BLUE
            }
            listBox.addView(Ui.listRow(this, q.customerName, badge.second, q.customerName, q.phone, badge))
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
