package com.gandy.wassal

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        const val MODE_SEMI = "semi"   // نص أوتوماتيك (آمن)
        const val MODE_AUTO = "auto"   // أوتوماتيك كامل
        const val WA = "com.whatsapp"
        const val WA_BUSINESS = "com.whatsapp.w4b"
    }

    var mode: String
        get() = sp.getString("mode", MODE_SEMI)!!
        set(v) = sp.edit().putString("mode", v).apply()

    var waPackage: String
        get() = sp.getString("pkg", WA_BUSINESS)!!
        set(v) = sp.edit().putString("pkg", v).apply()

    var minDelaySec: Int
        get() = sp.getInt("min", 25)
        set(v) = sp.edit().putInt("min", v).apply()

    var maxDelaySec: Int
        get() = sp.getInt("max", 60)
        set(v) = sp.edit().putInt("max", v).apply()

    var dailyLimit: Int
        get() = sp.getInt("limit", 200)
        set(v) = sp.edit().putInt("limit", v).apply()

    var resumeHour: Int
        get() = sp.getInt("resume_hour", 10)
        set(v) = sp.edit().putInt("resume_hour", v).apply()

    var shopName: String
        get() = sp.getString("shop", "")!!
        set(v) = sp.edit().putString("shop", v).apply()

    var licenseKey: String
        get() = sp.getString("license", "")!!
        set(v) = sp.edit().putString("license", v).apply()

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** عدد اللي اتبعت النهارده (بيتصفر أوتوماتيك كل يوم) */
    val sentToday: Int
        get() = if (sp.getString("count_date", "") == today()) sp.getInt("count", 0) else 0

    fun incSent() {
        sp.edit().putString("count_date", today()).putInt("count", sentToday + 1).apply()
    }

    val limitReached: Boolean get() = sentToday >= dailyLimit

    fun randomDelayMs(): Long {
        val lo = minDelaySec.coerceAtLeast(5)
        val hi = maxDelaySec.coerceAtLeast(lo)
        return (lo..hi).random() * 1000L
    }
}

/**
 * ترخيص بسيط ببصمة الجهاز.
 * المفتاح = أول 16 حرف من SHA-256(كود الجهاز + SECRET).
 * SECRET بييجي من GitHub Secret اسمه WASSAL_SECRET، واستخدم tools/keygen.py بنفس القيمة.
 */
object License {
    private val SECRET = BuildConfig.LICENSE_SECRET

    @SuppressLint("HardwareIds")
    fun deviceCode(ctx: Context): String {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        return sha(id).take(12).uppercase().chunked(4).joinToString("-")
    }

    fun expectedKey(deviceCode: String): String =
        sha(deviceCode.replace("-", "") + SECRET).take(16).uppercase().chunked(4).joinToString("-")

    fun isValid(ctx: Context): Boolean {
        val saved = Prefs(ctx).licenseKey.replace("-", "").trim().uppercase()
        return saved.isNotEmpty() && saved == expectedKey(deviceCode(ctx)).replace("-", "")
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
