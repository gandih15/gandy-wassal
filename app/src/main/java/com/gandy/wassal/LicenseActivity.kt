package com.gandy.wassal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LicenseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = License.deviceCode(this)
        val body = Ui.screen(this, "غاندي وصّل", "ابعت عروضك لعملائك على واتساب", Ui.BRAND, back = false)

        body.addView(TextView(this).apply {
            text = "📣"; textSize = 56f; gravity = Gravity.CENTER
            setPadding(0, Ui.dp(this@LicenseActivity, 20), 0, Ui.dp(this@LicenseActivity, 10))
        })
        body.addView(Ui.title(this, "تفعيل التطبيق", 18f).apply { gravity = Gravity.CENTER })
        body.addView(Ui.sub(this, "ابعت كود الجهاز للمطوّر عشان تاخد كود التفعيل", 13f).apply {
            gravity = Gravity.CENTER; setPadding(0, Ui.dp(this@LicenseActivity, 4), 0, Ui.dp(this@LicenseActivity, 16))
        })

        body.addView(Ui.card(this, vertical = true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(Ui.sub(this@LicenseActivity, "كود الجهاز"))
            addView(TextView(this@LicenseActivity).apply {
                text = code; textSize = 24f; typeface = Typeface.MONOSPACE; letterSpacing = 0.08f
                setTextColor(Ui.BRAND); gravity = Gravity.CENTER
                setPadding(0, Ui.dp(this@LicenseActivity, 6), 0, 0)
            })
        })
        body.addView(Ui.secondary(this, "نسخ كود الجهاز") {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("code", code))
            Toast.makeText(this, "اتنسخ", Toast.LENGTH_SHORT).show()
        })

        body.addView(Ui.spacer(this, 12))
        val key = Ui.input(this, "كود التفعيل  XXXX-XXXX-XXXX-XXXX").apply { gravity = Gravity.CENTER; letterSpacing = 0.05f }
        body.addView(key)
        body.addView(Ui.primary(this, "تفعيل") {
            Prefs(this).licenseKey = key.text.toString()
            if (License.isValid(this)) {
                startActivity(Intent(this, MainActivity::class.java)); finish()
            } else Toast.makeText(this, "الكود غلط", Toast.LENGTH_LONG).show()
        })
        body.addView(Ui.sub(this, "Gandy Wassal · v1.0").apply {
            gravity = Gravity.CENTER; setTextColor(Color.GRAY); setPadding(0, Ui.dp(this@LicenseActivity, 20), 0, 0)
        })
    }
}
