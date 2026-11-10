package com.gandy.wassal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** بيجهّز Intent بيفتح شات العميل على واتساب والرسالة (والصورة) جاهزين. */
object Wa {

    fun personalize(body: String, name: String): String =
        body.replace("{الاسم}", name).replace("{name}", name)

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun buildIntent(ctx: Context, item: QueueItem, pkg: String): Intent {
        val text = personalize(item.body, item.customerName)
        val img = item.imagePath?.let { File(it) }?.takeIf { it.exists() }

        val intent = if (img != null) {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", img)
            ctx.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra("jid", item.phone + "@s.whatsapp.net") // يفتح على شات العميل مباشرة
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=${item.phone}&text=${Uri.encode(text)}"))
        }
        intent.setPackage(pkg)
        return intent
    }
}
