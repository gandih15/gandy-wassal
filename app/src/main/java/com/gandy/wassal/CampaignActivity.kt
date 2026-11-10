package com.gandy.wassal

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** حملة جديدة في 4 خطوات: الصفحة ← المنشور ← العملاء ← الطريقة */
class CampaignActivity : AppCompatActivity() {

    private lateinit var db: Db
    private lateinit var prefs: Prefs
    private var pageId = 0L
    private var postId = 0L
    private var allCustomers = true
    private val groups = mutableSetOf<String>()
    private var mode = Prefs.MODE_SEMI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db.get(this)
        prefs = Prefs(this)
        mode = prefs.mode
        pageId = intent.getLongExtra("page", 0)
        postId = intent.getLongExtra("post", 0)
        val pages = db.pages()
        if (pageId == 0L) pageId = pages.firstOrNull()?.id ?: 0
        render()
    }

    private fun render() {
        val pages = db.pages()
        val page = pages.firstOrNull { it.id == pageId }
        val color = page?.color ?: Ui.BRAND
        val body = Ui.screen(this, "حملة جديدة", "4 خطوات وتبدأ", color)

        if (pages.isEmpty()) {
            body.addView(Ui.empty(this, "🗂", "محتاج صفحة الأول", "روح لتبويب الصفحات واعمل صفحة وضيف فيها منشور"))
            return
        }

        // ---------- 1) الصفحة ----------
        body.addView(step(1, "هتبعت باسم أنهي صفحة؟", color))
        pages.forEach { pg ->
            val sel = pg.id == pageId
            body.addView(Ui.card(this) { pageId = pg.id; postId = 0; render() }.apply {
                if (sel) background = Ui.rounded(Ui.soft(this@CampaignActivity, pg.color), Ui.dp(this@CampaignActivity, 16).toFloat(), pg.color, Ui.dp(this@CampaignActivity, 2))
                addView(Ui.avatar(this@CampaignActivity, pg.name, pg.color, 36))
                addView(Ui.titleBlock(this@CampaignActivity, pg.name, "${pg.postCount} منشور"))
                if (sel) addView(Ui.chip(this@CampaignActivity, "✓", pg.color, filled = true).apply {
                    (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
                })
            })
        }

        // ---------- 2) المنشور ----------
        body.addView(step(2, "أنهي منشور؟", color))
        val posts = db.posts(pageId)
        if (posts.isEmpty()) {
            body.addView(Ui.note(this, "الصفحة دي مفيهاش منشورات. ضيف منشور الأول.", Ui.AMBER))
            body.addView(Ui.secondary(this, "+ منشور جديد", color) {
                startActivity(Intent(this, PostEditActivity::class.java).putExtra("page", pageId))
            })
        } else {
            if (posts.none { it.id == postId }) postId = posts.first().id
            posts.forEach { po ->
                val sel = po.id == postId
                body.addView(Ui.card(this) { postId = po.id; render() }.apply {
                    if (sel) background = Ui.rounded(Ui.soft(this@CampaignActivity, color), Ui.dp(this@CampaignActivity, 16).toFloat(), color, Ui.dp(this@CampaignActivity, 2))
                    addView(Ui.titleBlock(this@CampaignActivity,
                        (if (po.imagePath != null) "🖼  " else "") + po.title,
                        po.body.replace("\n", " ")))
                    addView(Ui.chip(this@CampaignActivity, Db.kindLabel(po.kind), color, filled = sel).apply {
                        (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
                    })
                })
            }
        }

        // ---------- 3) العملاء ----------
        body.addView(step(3, "تبعت لمين؟", color))
        val chips = FlowLayout(this)
        chips.addView(Ui.chip(this, "كل العملاء · ${db.audienceSize(null)}", color, filled = allCustomers) {
            allCustomers = true; groups.clear(); render()
        })
        db.groups().forEach { (g, n) ->
            val on = !allCustomers && g in groups
            chips.addView(Ui.chip(this, "$g · $n", color, filled = on) {
                if (allCustomers) { allCustomers = false; groups.clear() }
                if (g in groups) groups.remove(g) else groups.add(g)
                if (groups.isEmpty()) allCustomers = true
                render()
            })
        }
        body.addView(chips)

        // ---------- 4) الطريقة ----------
        body.addView(step(4, "طريقة الإرسال", color))
        body.addView(Ui.choiceCards(this, listOf(
            Triple("نص أوتوماتيك  ·  آمن", "انت تدوس إرسال والتطبيق ينقل للي بعده", Ui.GREEN),
            Triple("أوتوماتيك كامل", "التطبيق يدوس لوحده · خطر حظر", Ui.AMBER)
        ), if (mode == Prefs.MODE_AUTO) 1 else 0) { i -> mode = if (i == 1) Prefs.MODE_AUTO else Prefs.MODE_SEMI })

        // ---------- ابدأ ----------
        val audience = db.audienceSize(if (allCustomers) null else groups)
        val post = posts.firstOrNull { it.id == postId }
        if (post != null && page != null) {
            body.addView(Ui.section(this, "شكل الرسالة"))
            body.addView(preview(Wa.personalize(post.body, "أحمد") + if (page.signature.isNotBlank()) "\n\n— ${page.signature}" else "", post.imagePath != null))
        }
        body.addView(Ui.primary(this, if (audience > 0) "ابدأ الإرسال لـ $audience عميل" else "مفيش عملاء في الاختيار ده", color) {
            if (post == null || page == null) { toast("اختار منشور"); return@primary }
            if (audience == 0) { toast("مفيش عملاء"); return@primary }
            if (mode == Prefs.MODE_AUTO && prefs.mode != Prefs.MODE_AUTO) {
                androidx.appcompat.app.AlertDialog.Builder(this).setTitle("تأكيد")
                    .setMessage("أنا فاهم إن الإرسال الأوتوماتيك الكامل ممكن يعرّض رقم الواتساب للحظر، وإني مسؤول عن استخدامه مع عملائي بس.")
                    .setPositiveButton("موافق") { _, _ -> launch(post, page) }
                    .setNegativeButton("إلغاء", null).show()
            } else launch(post, page)
        })
    }

    private fun launch(post: Post, page: Page) {
        prefs.mode = mode
        val n = db.createCampaign(page.id, post.id, post.title, if (allCustomers) null else groups.toSet())
        toast("اتضاف $n رسالة للطابور")
        startActivity(Intent(this, SendActivity::class.java).putExtra("autostart", true))
        finish()
    }

    /** فقاعة شبه رسالة واتساب للمعاينة */
    private fun preview(text: String, hasImage: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val dark = Ui.isDark(this@CampaignActivity)
        background = Ui.rounded(if (dark) Color.parseColor("#005C4B") else Color.parseColor("#D9FDD3"), Ui.dp(this@CampaignActivity, 14).toFloat())
        val p = Ui.dp(this@CampaignActivity, 12); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@CampaignActivity, 8) }
        if (hasImage) addView(TextView(this@CampaignActivity).apply {
            this.text = "🖼  صورة"; gravity = Gravity.CENTER; setTextColor(if (dark) Color.WHITE else Color.DKGRAY)
            background = Ui.rounded(Color.argb(40, 0, 0, 0), Ui.dp(this@CampaignActivity, 10).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this@CampaignActivity, 90)).apply { bottomMargin = Ui.dp(this@CampaignActivity, 8) }
        })
        addView(TextView(this@CampaignActivity).apply {
            this.text = text; textSize = 14f; setTextColor(if (dark) Color.WHITE else Color.parseColor("#111B21"))
            setLineSpacing(0f, 1.2f)
        })
    }

    private fun step(n: Int, t: String, color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = Ui.dp(this@CampaignActivity, 14); bottomMargin = Ui.dp(this@CampaignActivity, 8) }
        addView(TextView(this@CampaignActivity).apply {
            text = "$n"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 12f
            background = Ui.rounded(color, Ui.dp(this@CampaignActivity, 12).toFloat())
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@CampaignActivity, 24), Ui.dp(this@CampaignActivity, 24)).apply { marginEnd = Ui.dp(this@CampaignActivity, 10) }
        })
        addView(Ui.title(this@CampaignActivity, t, 15f))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onRestart() {
        super.onRestart()
        render() // رجع من إضافة منشور
    }
}
