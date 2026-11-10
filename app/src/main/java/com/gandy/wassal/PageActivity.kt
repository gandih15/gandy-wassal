package com.gandy.wassal

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** نافذة إضافة/تعديل صفحة: الاسم + التوقيع + اللون */
object PageDialog {
    fun show(a: Activity, existing: Page?, done: () -> Unit) {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(a, 20); setPadding(p, p / 2, p, 0)
        }
        val name = Ui.input(a, "اسم الصفحة (مثلاً: لمسة للإكسسوارات)", existing?.name ?: "")
        val sig = Ui.input(a, "التوقيع في آخر الرسالة (اختياري)", existing?.signature ?: "")
        box.addView(Ui.label(a, "الاسم")); box.addView(name)
        box.addView(Ui.label(a, "التوقيع")); box.addView(sig)
        box.addView(Ui.label(a, "اللون"))

        var color = existing?.color ?: Ui.PAGE_COLORS.first()
        val colors = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        fun drawColors() {
            colors.removeAllViews()
            Ui.PAGE_COLORS.forEach { c ->
                colors.addView(View(a).apply {
                    val s = Ui.dp(a, 34)
                    layoutParams = LinearLayout.LayoutParams(s, s).apply { marginEnd = Ui.dp(a, 10) }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(c)
                        if (c == color) setStroke(Ui.dp(a, 4), Ui.soft(a, c) or 0x66000000)
                    }
                    setOnClickListener { color = c; drawColors() }
                })
            }
        }
        drawColors()
        box.addView(colors)

        AlertDialog.Builder(a).setTitle(if (existing == null) "صفحة جديدة" else "تعديل الصفحة").setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                if (name.text.isBlank()) { Toast.makeText(a, "اكتب اسم الصفحة", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                Db.get(a).savePage(existing?.id ?: 0, name.text.toString(), color, sig.text.toString())
                done()
            }
            .setNegativeButton("إلغاء", null).show()
    }
}

/** صفحة واحدة: منشوراتها مقسّمة حسب النوع */
class PageActivity : AppCompatActivity() {
    private var pageId = 0L
    private var kindFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageId = intent.getLongExtra("id", 0)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val db = Db.get(this)
        val pg = db.page(pageId) ?: run { finish(); return }
        val body = Ui.screen(this, pg.name,
            "${pg.postCount} منشور" + if (pg.signature.isNotBlank()) " · ${pg.signature}" else "",
            pg.color, action = "تعديل" to { pageMenu(pg) })

        // فلتر الأنواع
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val options = listOf<Pair<String?, String>>(null to "الكل") + Db.KINDS.map { it.first to it.second }
        options.forEach { (k, label) ->
            chips.addView(Ui.chip(this, label, pg.color, filled = k == kindFilter) { kindFilter = k; render() })
        }
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        val posts = db.posts(pageId).filter { kindFilter == null || it.kind == kindFilter }
        if (posts.isEmpty()) body.addView(Ui.empty(this, "📝", "مفيش منشورات هنا", "ضيف عرض أو إعلان أو تهنئة، ويبقى جاهز لأي حملة"))

        posts.forEach { post -> body.addView(postCard(post, pg)) }
        body.addView(Ui.primary(this, "+ منشور جديد", pg.color) {
            startActivity(Intent(this, PostEditActivity::class.java).putExtra("page", pageId).putExtra("kind", kindFilter ?: Db.KIND_OFFER))
        })
    }

    private fun postCard(post: Post, pg: Page): View {
        val card = Ui.card(this, vertical = true) { postMenu(post) }
        card.setPadding(0, 0, 0, Ui.dp(this, 14))
        val img = post.imagePath?.let { File(it) }?.takeIf { it.exists() }
        if (img != null) {
            card.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(decodeSmall(img.absolutePath))
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                background = Ui.rounded(Ui.pal(this@PageActivity).surface2, Ui.dp(this@PageActivity, 16).toFloat())
                layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this@PageActivity, 160))
            })
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(this@PageActivity, 14); setPadding(p, p, p, 0)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        head.addView(Ui.chip(this, Db.kindLabel(post.kind), pg.color).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = 0
        })
        head.addView(Ui.title(this, post.title, 15f), LinearLayout.LayoutParams(0, -2, 1f))
        inner.addView(head)
        inner.addView(Ui.sub(this, post.body, 13f).apply { maxLines = 3; setPadding(0, Ui.dp(this@PageActivity, 8), 0, 0) })
        card.addView(inner)
        return card
    }

    private fun postMenu(post: Post) {
        AlertDialog.Builder(this).setTitle(post.title)
            .setItems(arrayOf("ابعته في حملة", "تعديل", "حذف")) { _, i ->
                when (i) {
                    0 -> startActivity(Intent(this, CampaignActivity::class.java).putExtra("page", pageId).putExtra("post", post.id))
                    1 -> startActivity(Intent(this, PostEditActivity::class.java).putExtra("page", pageId).putExtra("id", post.id))
                    else -> AlertDialog.Builder(this).setMessage("حذف المنشور؟")
                        .setPositiveButton("حذف") { _, _ -> Db.get(this).deletePost(post.id); render() }
                        .setNegativeButton("لأ", null).show()
                }
            }.show()
    }

    private fun pageMenu(pg: Page) {
        AlertDialog.Builder(this).setTitle(pg.name)
            .setItems(arrayOf("تعديل الاسم والتوقيع واللون", "حذف الصفحة ومنشوراتها")) { _, i ->
                if (i == 0) PageDialog.show(this, pg) { render() }
                else AlertDialog.Builder(this).setMessage("هتتمسح الصفحة وكل منشوراتها. متأكد؟")
                    .setPositiveButton("امسح") { _, _ -> Db.get(this).deletePage(pg.id); finish() }
                    .setNegativeButton("لأ", null).show()
            }.show()
    }

    companion object {
        /** تصغير الصورة عشان القائمة متتقلش */
        fun decodeSmall(path: String, target: Int = 900): android.graphics.Bitmap? {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (o.outWidth / (s * 2) >= target) s *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        }
    }
}

/** إضافة/تعديل منشور: النوع + العنوان + النص + الصورة */
class PostEditActivity : AppCompatActivity() {
    private var postId = 0L
    private var pageId = 0L
    private var imagePath: String? = null
    private lateinit var preview: ImageView
    private lateinit var previewBox: FrameLayout

    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) copyImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Db.get(this)
        postId = intent.getLongExtra("id", 0)
        val existing = if (postId > 0) db.post(postId) else null
        pageId = existing?.pageId ?: intent.getLongExtra("page", 0)
        val pg = db.page(pageId) ?: run { finish(); return }
        imagePath = existing?.imagePath
        var kind = existing?.kind ?: intent.getStringExtra("kind") ?: Db.KIND_OFFER

        val body = Ui.screen(this, if (existing == null) "منشور جديد" else "تعديل المنشور", pg.name, pg.color)

        body.addView(Ui.label(this, "النوع"))
        val kinds = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun drawKinds() {
            kinds.removeAllViews()
            Db.KINDS.forEach { (k, label) ->
                kinds.addView(Ui.chip(this, label, pg.color, filled = k == kind) { kind = k; drawKinds() })
            }
        }
        drawKinds()
        body.addView(kinds)

        body.addView(Ui.label(this, "العنوان (بيظهر لك انت بس)"))
        val title = Ui.input(this, "مثلاً: خصم الجمعة 30%", existing?.title ?: "")
        body.addView(title)

        body.addView(Ui.label(this, "نص الرسالة"))
        val text = Ui.input(this, "اكتب الرسالة هنا",
            existing?.body ?: "أهلاً {الاسم} 👋\n\n\n\nللإلغاء ابعت: إلغاء", lines = 7)
        body.addView(text)
        body.addView(Ui.row(this,
            Ui.secondary(this, "+ {الاسم}", pg.color) { text.text.insert(text.selectionStart.coerceAtLeast(0), "{الاسم}") },
            Ui.secondary(this, "+ سطر إلغاء", pg.color) { text.append("\n\nللإلغاء ابعت: إلغاء") }
        ))
        if (pg.signature.isNotBlank()) body.addView(Ui.note(this, "هيتضاف في آخر الرسالة: — ${pg.signature}", pg.color))

        body.addView(Ui.label(this, "الصورة (اختياري)"))
        previewBox = FrameLayout(this).apply {
            background = Ui.rounded(Ui.soft(this@PostEditActivity, pg.color), Ui.dp(this@PostEditActivity, 16).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, Ui.dp(this@PostEditActivity, 190)).apply { bottomMargin = Ui.dp(this@PostEditActivity, 10) }
            setOnClickListener { pickImage.launch(arrayOf("image/*")) }
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = Ui.rounded(0, Ui.dp(this@PostEditActivity, 16).toFloat())
        }
        previewBox.addView(Ui.title(this, "🖼  اضغط واختار صورة", 15f).apply {
            setTextColor(Ui.onSoft(this@PostEditActivity, pg.color))
            layoutParams = FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER)
        })
        previewBox.addView(preview, FrameLayout.LayoutParams(-1, -1))
        body.addView(previewBox)
        body.addView(Ui.secondary(this, "شيل الصورة", Ui.RED) { imagePath = null; showPreview() })
        showPreview()

        body.addView(Ui.primary(this, "حفظ المنشور", pg.color) {
            if (text.text.isBlank()) { Toast.makeText(this, "اكتب نص الرسالة", Toast.LENGTH_SHORT).show(); return@primary }
            db.savePost(postId, pageId, kind,
                title.text.toString().ifBlank { text.text.toString().replace("{الاسم}", "").trim().take(28) },
                text.text.toString().trim(), imagePath)
            finish()
        })
    }

    private fun showPreview() {
        val p = imagePath
        if (p != null && File(p).exists()) { preview.setImageBitmap(PageActivity.decodeSmall(p)); preview.visibility = View.VISIBLE }
        else { preview.setImageDrawable(null); preview.visibility = View.GONE }
    }

    /** بننسخ الصورة جوه التطبيق عشان تفضل موجودة حتى لو اتمسحت من المعرض */
    private fun copyImage(uri: android.net.Uri) {
        try {
            val dir = File(filesDir, "images").apply { mkdirs() }
            val out = File(dir, "post_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            imagePath = out.absolutePath
            showPreview()
        } catch (e: Exception) {
            Toast.makeText(this, "مش قادر أفتح الصورة", Toast.LENGTH_SHORT).show()
        }
    }
}
