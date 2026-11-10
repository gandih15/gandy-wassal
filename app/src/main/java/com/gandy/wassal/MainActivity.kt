package com.gandy.wassal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** الشاشة الرئيسية بأربع تبويبات: الرئيسية، الصفحات، العملاء، الإعدادات */
class MainActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var nav: BottomNavigationView
    private lateinit var db: Db
    private lateinit var prefs: Prefs
    private var tab = R.id.nav_home
    private var customerFilter = ""

    private val contactsPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) importContacts() else toast("محتاج صلاحية جهات الاتصال")
    }
    private val pickCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importCsv(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db.get(this)
        prefs = Prefs(this)
        tab = savedInstanceState?.getInt("tab") ?: R.id.nav_home

        val p = Ui.pal(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(p.bg)
        }
        container = FrameLayout(this)
        root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))

        nav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.bottom_nav)
            setBackgroundColor(p.surface)
            val tint = ContextCompat.getColorStateList(this@MainActivity, R.color.nav_item)
            itemIconTintList = tint
            itemTextColor = tint
            itemActiveIndicatorColor = ColorStateList.valueOf(Ui.soft(this@MainActivity, Ui.BRAND))
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            elevation = Ui.dp(this@MainActivity, 8).toFloat()
            setOnItemSelectedListener { tab = it.itemId; render(); true }
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        window.statusBarColor = Ui.BRAND
        nav.selectedItemId = tab
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        if (!License.isValid(this)) {
            startActivity(Intent(this, LicenseActivity::class.java)); finish(); return
        }
        render()
    }

    /** بيبني محتوى التبويب الحالي */
    private fun render() {
        container.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        scroll.addView(page)
        container.addView(scroll)
        when (tab) {
            R.id.nav_pages -> pagesTab(page)
            R.id.nav_customers -> customersTab(page)
            R.id.nav_settings -> settingsTab(page)
            else -> homeTab(page)
        }
    }

    // ==================================================================
    // الرئيسية
    // ==================================================================
    private fun homeTab(page: LinearLayout) {
        val shop = prefs.shopName.ifBlank { "أهلاً بيك" }
        page.addView(Ui.header(this, "غاندي وصّل", shop, Ui.BRAND, null, extraBottom = Ui.dp(this, 26)))
        val body = Ui.column(this)
        // نطلّع المربعات لفوق شوية عشان تركب على الهيدر
        page.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = -Ui.dp(this@MainActivity, 36) })

        body.addView(Ui.stats(this, listOf(
            "${db.customerCount()}" to "عميل",
            "${db.pages().size}" to "صفحة",
            "${prefs.sentToday}" to "اتبعت النهارده",
            "${db.successRate()}%" to "نسبة النجاح"
        )))

        val pending = db.pendingCount()
        if (pending > 0) {
            body.addView(Ui.card(this, onClick = { startActivity(Intent(this, SendActivity::class.java)) }).apply {
                background = Ui.rounded(Ui.soft(this@MainActivity, Ui.BLUE), Ui.dp(this@MainActivity, 16).toFloat())
                addView(Ui.titleBlock(this@MainActivity, "في الطابور $pending رسالة", "الحالة: " + sendState()))
                addView(Ui.chip(this@MainActivity, "افتح", Ui.BLUE, filled = true).apply {
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
                })
            })
        }

        body.addView(Ui.primary(this, "حملة جديدة") { startActivity(Intent(this, CampaignActivity::class.java)) })

        body.addView(Ui.section(this, "آخر الحملات"))
        val camps = db.campaigns()
        if (camps.isEmpty()) {
            body.addView(Ui.empty(this, "📣", "مفيش حملات لسه", "اعمل صفحة وضيف فيها منشور، وبعدين ابدأ أول حملة"))
        }
        val fmt = SimpleDateFormat("d/M  h:mm a", Locale("ar"))
        camps.forEach { k ->
            val badge = when {
                k.pending == 0 -> "خلصت" to Ui.GREEN
                k.sent + k.failed > 0 -> "شغالة" to Ui.BLUE
                else -> "مستنية" to Ui.AMBER
            }
            body.addView(Ui.listRow(
                this, k.pageName, k.pageColor, k.title,
                "${k.pageName} · ${k.sent} من ${k.total} · ${fmt.format(Date(k.createdAt))}",
                badge
            ) { startActivity(Intent(this, SendActivity::class.java)) })
        }
    }

    private fun sendState(): String =
        if (prefs.mode == Prefs.MODE_AUTO) WaAutoService.status else "اضغط عشان تكمّل"

    // ==================================================================
    // الصفحات
    // ==================================================================
    private fun pagesTab(page: LinearLayout) {
        page.addView(Ui.header(this, "صفحاتي", "كل صفحة ليها اسم ولون وتوقيع ومنشورات جاهزة", Ui.BRAND, null,
            action = "+ صفحة" to { pageDialog(null) }))
        val body = Ui.column(this); page.addView(body)

        val pages = db.pages()
        if (pages.isEmpty()) {
            body.addView(Ui.empty(this, "🗂", "ابدأ بأول صفحة", "مثلاً: اسم المحل، أو براند تاني بتبيعه"))
        }
        pages.forEach { pg ->
            body.addView(Ui.listRow(
                this, pg.name, pg.color, pg.name,
                "${pg.postCount} منشور" + if (pg.signature.isNotBlank()) " · توقيع: ${pg.signature}" else "",
                null
            ) { startActivity(Intent(this, PageActivity::class.java).putExtra("id", pg.id)) })
        }
        body.addView(Ui.secondary(this, "+ إضافة صفحة") { pageDialog(null) })
    }

    /** إضافة أو تعديل صفحة (بتتنادى كمان من PageActivity) */
    private fun pageDialog(existing: Page?) = PageDialog.show(this, existing) { render() }

    // ==================================================================
    // العملاء
    // ==================================================================
    private fun customersTab(page: LinearLayout) {
        val all = db.customers(customerFilter)
        page.addView(Ui.header(this, "العملاء", "${db.customerCount()} عميل · ${db.groups().size} مجموعة", Ui.BRAND, null,
            action = "+ عميل" to { addCustomerDialog() }))
        val body = Ui.column(this); page.addView(body)

        body.addView(Ui.row(this,
            Ui.secondary(this, "جهات الاتصال") { askContacts() },
            Ui.secondary(this, "ملف Excel/CSV") { pickCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel")) }
        ))

        val search = Ui.input(this, "🔍  بحث بالاسم أو الرقم أو المجموعة", customerFilter)
        body.addView(search)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)

        fun fill(items: List<Customer>) {
            list.removeAllViews()
            if (items.isEmpty()) list.addView(Ui.empty(this, "👥", "مفيش عملاء", "ضيفهم يدوي أو من جهات الاتصال أو من ملف"))
            items.take(400).forEach { c ->
                list.addView(Ui.listRow(
                    this, c.name, if (c.optedOut) Ui.RED else Ui.BRAND, c.name, c.phone,
                    if (c.optedOut) "موقوف" to Ui.RED else c.group to Ui.BRAND
                ) { customerMenu(c) })
            }
            if (items.size > 400) list.addView(Ui.sub(this, "بيظهر أول 400 بس، استخدم البحث"))
        }
        fill(all)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { customerFilter = s.toString(); fill(db.customers(customerFilter)) }
        })
    }

    private fun customerMenu(c: Customer) {
        val opts = arrayOf(if (c.optedOut) "رجّع الإرسال له" else "وقّف الإرسال له (طلب إلغاء)", "تغيير المجموعة", "حذف")
        AlertDialog.Builder(this).setTitle("${c.name} · ${c.phone}").setItems(opts) { _, i ->
            when (i) {
                0 -> { db.setOptOut(c.id, !c.optedOut); render() }
                1 -> {
                    val inp = Ui.input(this, "المجموعة", c.group)
                    AlertDialog.Builder(this).setTitle("المجموعة").setView(wrap(inp))
                        .setPositiveButton("حفظ") { _, _ -> db.setGroup(c.id, inp.text.toString()); render() }
                        .setNegativeButton("إلغاء", null).show()
                }
                else -> AlertDialog.Builder(this).setMessage("حذف ${c.name}؟")
                    .setPositiveButton("حذف") { _, _ -> db.deleteCustomer(c.id); render() }
                    .setNegativeButton("لأ", null).show()
            }
        }.show()
    }

    private fun addCustomerDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = Ui.input(this, "الاسم")
        val phone = Ui.input(this, "رقم الموبايل 01xxxxxxxxx", number = true)
        val group = Ui.input(this, "المجموعة (مثلاً VIP)", "عام")
        box.addView(name); box.addView(phone); box.addView(group)
        AlertDialog.Builder(this).setTitle("عميل جديد").setView(wrap(box))
            .setPositiveButton("حفظ") { _, _ ->
                val ok = db.addCustomer(name.text.toString(), phone.text.toString(), group.text.toString())
                toast(if (ok) "اتضاف" else "الرقم غلط أو موجود قبل كده"); render()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun askContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) importContacts()
        else contactsPerm.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun importContacts() {
        val group = Ui.input(this, "اسم المجموعة", "جهات الاتصال")
        AlertDialog.Builder(this).setTitle("استيراد جهات الاتصال").setView(wrap(group))
            .setPositiveButton("استيراد") { _, _ ->
                var n = 0
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) if (db.addCustomer(c.getString(0) ?: "", c.getString(1) ?: "", group.text.toString())) n++
                }
                toast("اتضاف $n عميل"); render()
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun importCsv(uri: Uri) {
        var n = 0
        try {
            contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { raw ->
                    val parts = raw.removePrefix("﻿").split(',', ';', '\t').map { it.trim().trim('"') }
                    if (parts.size >= 2 && db.addCustomer(parts[0], parts[1], parts.getOrElse(2) { "عام" })) n++
                }
            }
            toast("اتضاف $n عميل")
        } catch (e: Exception) { toast("مش قادر أقرا الملف") }
        render()
    }

    // ==================================================================
    // الإعدادات
    // ==================================================================
    private fun settingsTab(page: LinearLayout) {
        page.addView(Ui.header(this, "الإعدادات", "طريقة الإرسال والسرعة والحدود", Ui.BRAND, null))
        val body = Ui.column(this); page.addView(body)

        body.addView(Ui.label(this, "اسم المحل (بيظهر في الرئيسية)"))
        val shop = Ui.input(this, "مثلاً: لمسة للإكسسوارات", prefs.shopName); body.addView(shop)

        body.addView(Ui.section(this, "طريقة الإرسال"))
        var mode = prefs.mode
        body.addView(Ui.choiceCards(this, listOf(
            Triple("نص أوتوماتيك  ·  آمن", "واتساب يفتح جاهز وانت تدوس إرسال، والتطبيق ينقل للي بعده", Ui.GREEN),
            Triple("أوتوماتيك كامل", "التطبيق يدوس إرسال لوحده. فيه خطر حظر الرقم", Ui.AMBER)
        ), if (mode == Prefs.MODE_AUTO) 1 else 0) { i -> mode = if (i == 1) Prefs.MODE_AUTO else Prefs.MODE_SEMI })

        val enabled = WaAutoService.isEnabled(this)
        body.addView(Ui.card(this, onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }).apply {
            addView(Ui.titleBlock(this@MainActivity, "خدمة الإرسال التلقائي", "مطلوبة للأوتوماتيك الكامل بس"))
            addView(Ui.chip(this@MainActivity, if (enabled) "متفعّلة" else "مش متفعّلة", if (enabled) Ui.GREEN else Ui.RED).apply {
                (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
            })
        })

        body.addView(Ui.section(this, "واتساب المستخدم"))
        var pkg = prefs.waPackage
        body.addView(Ui.choiceCards(this, listOf(
            Triple("واتساب بيزنس", "يُفضّل للعروض", Ui.BRAND),
            Triple("واتساب العادي", "", Ui.BRAND)
        ), if (pkg == Prefs.WA) 1 else 0) { i -> pkg = if (i == 1) Prefs.WA else Prefs.WA_BUSINESS })

        body.addView(Ui.section(this, "السرعة والحدود"))
        body.addView(Ui.label(this, "التأخير بين الرسايل (ثانية) من / إلى"))
        val min = Ui.input(this, "25", prefs.minDelaySec.toString(), number = true)
        val max = Ui.input(this, "60", prefs.maxDelaySec.toString(), number = true)
        body.addView(Ui.row(this, min, max))
        body.addView(Ui.label(this, "الحد الأقصى في اليوم"))
        val limit = Ui.input(this, "200", prefs.dailyLimit.toString(), number = true); body.addView(limit)
        body.addView(Ui.label(this, "لو الحد خلص، يكمّل تاني يوم الساعة (0-23)"))
        val hour = Ui.input(this, "10", prefs.resumeHour.toString(), number = true); body.addView(hour)

        body.addView(Ui.note(this,
            "نصايح عشان الرقم ميتحظرش: استخدم رقم بيزنس منفصل، وابعت لعملاء المحل بس، واكتب {الاسم} في الرسايل، " +
            "وخلي في آخر الرسالة طريقة للإلغاء، وابدأ بحد يومي صغير لو الرقم جديد.", Ui.AMBER))

        body.addView(Ui.primary(this, "حفظ الإعدادات") {
            val save = {
                prefs.shopName = shop.text.toString().trim()
                prefs.mode = mode
                prefs.waPackage = pkg
                prefs.minDelaySec = (min.text.toString().toIntOrNull() ?: 25).coerceIn(5, 600)
                prefs.maxDelaySec = (max.text.toString().toIntOrNull() ?: 60).coerceIn(prefs.minDelaySec, 900)
                prefs.dailyLimit = (limit.text.toString().toIntOrNull() ?: 200).coerceIn(1, 1000)
                prefs.resumeHour = (hour.text.toString().toIntOrNull() ?: 10).coerceIn(0, 23)
                toast("اتحفظ")
                if (mode == Prefs.MODE_AUTO && !WaAutoService.isEnabled(this)) askEnableService() else render()
            }
            if (mode == Prefs.MODE_AUTO && prefs.mode != Prefs.MODE_AUTO) {
                AlertDialog.Builder(this).setTitle("تأكيد")
                    .setMessage("أنا فاهم إن الإرسال الأوتوماتيك الكامل ممكن يعرّض رقم الواتساب للحظر، وإني مسؤول عن استخدامه مع عملائي بس.")
                    .setPositiveButton("موافق") { _, _ -> save() }
                    .setNegativeButton("إلغاء", null).show()
            } else save()
        })

        body.addView(Ui.section(this, "الترخيص"))
        body.addView(Ui.card(this).apply {
            addView(Ui.titleBlock(this@MainActivity, "متفعّل على الجهاز ده", "كود الجهاز: ${License.deviceCode(this@MainActivity)}"))
        })
        body.addView(Ui.sub(this, "غاندي وصّل · الإصدار 1.0").apply { setPadding(0, Ui.dp(this@MainActivity, 8), 0, 0) })
    }

    private fun askEnableService() {
        AlertDialog.Builder(this).setTitle("فعّل خدمة الإرسال")
            .setMessage("هتفتحلك إعدادات الوصول (Accessibility). اختار \"غاندي وصّل – الإرسال التلقائي\" وفعّلها.\n\nلو أندرويد 13 أو أحدث وقالك إنه إعداد مقيّد: افتح معلومات التطبيق، وبعدين النقط التلاتة، واختار السماح بالإعدادات المقيّدة.")
            .setPositiveButton("افتح الإعدادات") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("بعدين") { _, _ -> render() }
            .show()
    }

    // ==================================================================
    private fun wrap(v: View) = FrameLayout(this).apply {
        val p = Ui.dp(this@MainActivity, 20); setPadding(p, p / 2, p, 0)
        addView(v)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
