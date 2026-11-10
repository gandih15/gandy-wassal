package com.gandy.wassal

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Customer(val id: Long, val name: String, val phone: String, val group: String, val optedOut: Boolean)
data class Page(val id: Long, val name: String, val color: Int, val signature: String, val postCount: Int)
data class Post(val id: Long, val pageId: Long, val kind: String, val title: String, val body: String, val imagePath: String?)
data class Campaign(
    val id: Long, val title: String, val pageName: String, val pageColor: Int,
    val total: Int, val sent: Int, val failed: Int, val pending: Int, val createdAt: Long
)
/** رسالة واحدة في الطابور. body = نص المنشور + توقيع الصفحة (قبل استبدال {الاسم}) */
data class QueueItem(
    val id: Long, val customerName: String, val phone: String,
    val body: String, val imagePath: String?, val status: String
)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "wassal.db", null, 1) {

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) {
            inst ?: Db(ctx.applicationContext).also { inst = it }
        }
        const val PENDING = "pending"
        const val SENT = "sent"
        const val FAILED = "failed"

        const val KIND_OFFER = "offer"
        const val KIND_AD = "ad"
        const val KIND_GREETING = "greeting"
        val KINDS = listOf(KIND_OFFER to "عرض", KIND_AD to "إعلان", KIND_GREETING to "تهنئة")
        fun kindLabel(k: String) = KINDS.firstOrNull { it.first == k }?.second ?: "منشور"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, phone TEXT UNIQUE, grp TEXT, opted_out INTEGER DEFAULT 0)")
        db.execSQL("CREATE TABLE pages(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, color INTEGER, signature TEXT)")
        db.execSQL("CREATE TABLE posts(id INTEGER PRIMARY KEY AUTOINCREMENT, page_id INTEGER, kind TEXT, title TEXT, body TEXT, image_path TEXT)")
        db.execSQL("CREATE TABLE campaigns(id INTEGER PRIMARY KEY AUTOINCREMENT, page_id INTEGER, post_id INTEGER, title TEXT, created_at INTEGER)")
        db.execSQL("CREATE TABLE queue(id INTEGER PRIMARY KEY AUTOINCREMENT, campaign_id INTEGER, customer_id INTEGER, status TEXT, updated_at INTEGER)")
        db.execSQL("CREATE INDEX q_status ON queue(status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {}

    private fun count(sql: String, args: Array<String>? = null): Int =
        readableDatabase.rawQuery(sql, args).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ======================= العملاء =======================
    fun addCustomer(name: String, phone: String, group: String): Boolean {
        val p = Phone.normalize(phone) ?: return false
        val cv = ContentValues().apply {
            put("name", name.trim().ifBlank { p }); put("phone", p); put("grp", group.trim().ifBlank { "عام" })
        }
        return writableDatabase.insertWithOnConflict("customers", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun customers(filter: String = ""): List<Customer> {
        val out = ArrayList<Customer>()
        val q = "%$filter%"
        readableDatabase.rawQuery(
            "SELECT id,name,phone,grp,opted_out FROM customers WHERE name LIKE ? OR phone LIKE ? OR grp LIKE ? ORDER BY name",
            arrayOf(q, q, q)
        ).use { c ->
            while (c.moveToNext()) out += Customer(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4) == 1)
        }
        return out
    }

    /** المجموعات وعدد العملاء النشطين في كل واحدة */
    fun groups(): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        readableDatabase.rawQuery("SELECT grp, COUNT(*) FROM customers WHERE opted_out=0 GROUP BY grp ORDER BY grp", null).use { c ->
            while (c.moveToNext()) out += c.getString(0) to c.getInt(1)
        }
        return out
    }

    fun setOptOut(id: Long, v: Boolean) =
        writableDatabase.execSQL("UPDATE customers SET opted_out=? WHERE id=?", arrayOf<Any>(if (v) 1 else 0, id))

    fun setGroup(id: Long, g: String) =
        writableDatabase.execSQL("UPDATE customers SET grp=? WHERE id=?", arrayOf<Any>(g.ifBlank { "عام" }, id))

    fun deleteCustomer(id: Long) {
        writableDatabase.delete("customers", "id=?", arrayOf(id.toString()))
        writableDatabase.delete("queue", "customer_id=? AND status=?", arrayOf(id.toString(), PENDING))
    }

    fun customerCount(): Int = count("SELECT COUNT(*) FROM customers")

    // ======================= الصفحات =======================
    fun savePage(id: Long, name: String, color: Int, signature: String): Long {
        val cv = ContentValues().apply { put("name", name.trim()); put("color", color); put("signature", signature.trim()) }
        return if (id > 0) { writableDatabase.update("pages", cv, "id=?", arrayOf(id.toString())); id }
        else writableDatabase.insert("pages", null, cv)
    }

    fun pages(): List<Page> {
        val out = ArrayList<Page>()
        readableDatabase.rawQuery(
            "SELECT p.id,p.name,p.color,p.signature,(SELECT COUNT(*) FROM posts WHERE page_id=p.id) FROM pages p ORDER BY p.id", null
        ).use { c ->
            while (c.moveToNext()) out += Page(c.getLong(0), c.getString(1), c.getInt(2), c.getString(3) ?: "", c.getInt(4))
        }
        return out
    }

    fun page(id: Long): Page? = pages().firstOrNull { it.id == id }

    fun deletePage(id: Long) {
        val db = writableDatabase
        db.execSQL("DELETE FROM queue WHERE status='pending' AND campaign_id IN (SELECT id FROM campaigns WHERE page_id=?)", arrayOf<Any>(id))
        db.delete("posts", "page_id=?", arrayOf(id.toString()))
        db.delete("pages", "id=?", arrayOf(id.toString()))
    }

    // ======================= المنشورات =======================
    fun savePost(id: Long, pageId: Long, kind: String, title: String, body: String, image: String?): Long {
        val cv = ContentValues().apply {
            put("page_id", pageId); put("kind", kind); put("title", title.trim()); put("body", body); put("image_path", image)
        }
        return if (id > 0) { writableDatabase.update("posts", cv, "id=?", arrayOf(id.toString())); id }
        else writableDatabase.insert("posts", null, cv)
    }

    fun posts(pageId: Long): List<Post> {
        val out = ArrayList<Post>()
        readableDatabase.rawQuery(
            "SELECT id,page_id,kind,title,body,image_path FROM posts WHERE page_id=? ORDER BY id DESC", arrayOf(pageId.toString())
        ).use { c ->
            while (c.moveToNext()) out += Post(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))
        }
        return out
    }

    fun post(id: Long): Post? =
        readableDatabase.rawQuery("SELECT id,page_id,kind,title,body,image_path FROM posts WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) Post(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5)) else null
        }

    fun deletePost(id: Long) {
        writableDatabase.execSQL("DELETE FROM queue WHERE status='pending' AND campaign_id IN (SELECT id FROM campaigns WHERE post_id=?)", arrayOf<Any>(id))
        writableDatabase.delete("posts", "id=?", arrayOf(id.toString()))
    }

    // ======================= الحملات =======================
    /** بيعمل حملة ويحط عملاء المجموعات المختارة في الطابور (null = كل العملاء). بيرجع عدد الرسايل */
    fun createCampaign(pageId: Long, postId: Long, title: String, groups: Set<String>?): Int {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        var n = 0
        db.beginTransaction()
        try {
            val campId = db.insert("campaigns", null, ContentValues().apply {
                put("page_id", pageId); put("post_id", postId); put("title", title); put("created_at", now)
            })
            val sql: String
            val args: Array<String>?
            if (groups == null) {
                sql = "SELECT id FROM customers WHERE opted_out=0"; args = null
            } else {
                sql = "SELECT id FROM customers WHERE opted_out=0 AND grp IN (${groups.joinToString(",") { "?" }})"
                args = groups.toTypedArray()
            }
            db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    db.insert("queue", null, ContentValues().apply {
                        put("campaign_id", campId); put("customer_id", c.getLong(0)); put("status", PENDING); put("updated_at", now)
                    })
                    n++
                }
            }
            if (n == 0) db.delete("campaigns", "id=?", arrayOf(campId.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return n
    }

    fun audienceSize(groups: Set<String>?): Int =
        if (groups == null) count("SELECT COUNT(*) FROM customers WHERE opted_out=0")
        else if (groups.isEmpty()) 0
        else count("SELECT COUNT(*) FROM customers WHERE opted_out=0 AND grp IN (${groups.joinToString(",") { "?" }})", groups.toTypedArray())

    fun campaigns(limit: Int = 20): List<Campaign> {
        val out = ArrayList<Campaign>()
        readableDatabase.rawQuery(
            """
            SELECT k.id, k.title, IFNULL(p.name,'—'), IFNULL(p.color,0),
                   (SELECT COUNT(*) FROM queue WHERE campaign_id=k.id),
                   (SELECT COUNT(*) FROM queue WHERE campaign_id=k.id AND status='sent'),
                   (SELECT COUNT(*) FROM queue WHERE campaign_id=k.id AND status='failed'),
                   (SELECT COUNT(*) FROM queue WHERE campaign_id=k.id AND status='pending'),
                   k.created_at
            FROM campaigns k LEFT JOIN pages p ON p.id=k.page_id
            ORDER BY k.id DESC LIMIT $limit
            """.trimIndent(), null
        ).use { c ->
            while (c.moveToNext()) out += Campaign(
                c.getLong(0), c.getString(1), c.getString(2), c.getInt(3),
                c.getInt(4), c.getInt(5), c.getInt(6), c.getInt(7), c.getLong(8)
            )
        }
        return out
    }

    // ======================= الطابور =======================
    private val queueSelect = """
        SELECT q.id, c.name, c.phone, po.body, po.image_path, q.status, IFNULL(pg.signature,'')
        FROM queue q
        JOIN customers c ON c.id=q.customer_id
        JOIN campaigns k ON k.id=q.campaign_id
        JOIN posts po ON po.id=k.post_id
        LEFT JOIN pages pg ON pg.id=k.page_id
    """.trimIndent()

    private fun compose(body: String, signature: String) =
        if (signature.isBlank()) body else "$body\n\n— $signature"

    private fun readItem(c: android.database.Cursor) = QueueItem(
        c.getLong(0), c.getString(1), c.getString(2), compose(c.getString(3), c.getString(6)), c.getString(4), c.getString(5)
    )

    fun nextPending(): QueueItem? =
        readableDatabase.rawQuery("$queueSelect WHERE q.status=? AND c.opted_out=0 ORDER BY q.id LIMIT 1", arrayOf(PENDING)).use { c ->
            if (c.moveToFirst()) readItem(c) else null
        }

    fun recentQueue(limit: Int = 60): List<QueueItem> {
        val out = ArrayList<QueueItem>()
        readableDatabase.rawQuery(
            "$queueSelect ORDER BY CASE q.status WHEN 'pending' THEN 1 ELSE 0 END, q.updated_at DESC, q.id LIMIT $limit", null
        ).use { c -> while (c.moveToNext()) out += readItem(c) }
        return out
    }

    fun mark(id: Long, status: String) =
        writableDatabase.execSQL("UPDATE queue SET status=?, updated_at=? WHERE id=?", arrayOf<Any>(status, System.currentTimeMillis(), id))

    fun pendingCount(): Int = count("SELECT COUNT(*) FROM queue WHERE status='pending'")
    fun clearPending() { writableDatabase.delete("queue", "status=?", arrayOf(PENDING)) }

    /** نسبة النجاح من كل اللي اتبعت أو فشل */
    fun successRate(): Int {
        val sent = count("SELECT COUNT(*) FROM queue WHERE status='sent'")
        val failed = count("SELECT COUNT(*) FROM queue WHERE status='failed'")
        return if (sent + failed == 0) 100 else sent * 100 / (sent + failed)
    }
}

object Phone {
    /** يحوّل الرقم لصيغة دولية بدون + (مصر افتراضيًا): 01012345678 -> 201012345678 */
    fun normalize(raw: String): String? {
        var d = raw.filter { it.isDigit() }
            .map { ch -> if (ch in '٠'..'٩') ('0' + (ch - '٠')) else ch }
            .joinToString("")
        if (d.startsWith("00")) d = d.drop(2)
        if (d.length == 11 && d.startsWith("01")) d = "2$d"
        if (d.length == 10 && d.startsWith("1")) d = "20$d"
        return if (d.length in 10..15) d else null
    }
}
