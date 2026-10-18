package com.d010.pdf.reader.base

import android.app.*
import android.content.*
import android.net.*
import android.util.*
import android.view.Gravity
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.af010.creator.docreader.R
import com.d010.pdf.reader.base.Aso.MAIL_SUPPORT
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.*
import java.io.File

object Aso {
    const val POLICY_LINK ="https://docs.google.com/document/d/e/2PACX-1vS9fB2OVQK9mJqrJNCxFPkPNiU9vpVZfOMEfSgU1Z3Ya6LurXGlzTExVE3tJuF-pA/pub"

    const val TEAM_SERVICE ="https://docs.google.com/document/d/e/2PACX-1vTNwnUGcBgKUQagmz23J9yrhEIzqldF8fZ6nLkMULdwGRLsZLRCDg3fiMXLHU986g/pub"

    const val MAIL_SUPPORT = "dong2k2.dev@gmail.com"
}

fun Context.openUrl(url: String) {
    runCatching {
        Intent(Intent.ACTION_VIEW, url.toUri()).also {
            this.startActivity(it)
        }
    }
}

fun Context.toastShort(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.toastTop(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).apply {
        setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
        show()
    }
}


fun Context.toastLong(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Activity.rateApp(onFinish: () -> Unit) {
    val manager = ReviewManagerFactory.create(this)
    val request = manager.requestReviewFlow()
    request.addOnCompleteListener { task: Task<ReviewInfo> ->
        if (task.isSuccessful) {
            val reviewInfo = task.result
            val flow =
                manager.launchReviewFlow(this, reviewInfo)
            flow.addOnCompleteListener {
                Log.d("RateApp", "Rate complete")
                onFinish()
            }
        } else {
            Log.e("RateApp", "error: " + task.exception.toString())
            onFinish()
        }
    }
}


fun Activity.sendFeedBack() {
    val emailIntent = Intent(Intent.ACTION_SENDTO)
    emailIntent.data = Uri.parse("mailto:")
    emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(MAIL_SUPPORT))
    emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Feedback")

    try {
        startActivity(Intent.createChooser(emailIntent, "Feedback"))
    }
    catch (e: ActivityNotFoundException) {
        e.printStackTrace()
    }
}

fun Context.isInternetAvailable(): Boolean {
    val connectivityManager =
        this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

    return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun Context.shareApp() {
    val applicationID = this.packageName
    val appPlayStoreUrl = "https://play.google.com/store/apps/details?id=$applicationID"
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, this.getString(R.string.app_name))
    shareIntent.putExtra(
        Intent.EXTRA_TEXT,
        getString(R.string.check_out_this_amazing_app) + appPlayStoreUrl
    )
    val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.share_via))
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooserIntent)
}

fun Context.shareValue(value: String) {
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_SUBJECT, this.getString(R.string.app_name))
    shareIntent.putExtra(
        Intent.EXTRA_TEXT,
        value
    )
    val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.share_via))
    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(chooserIntent)
}

/** Opens [path] in another app that can view its type; false when none can. */
fun Context.openFileWith(path: String): Boolean {
    val file = File(path)
    if (!file.exists()) return false
    return try {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(viewIntent, null)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooserIntent)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun Context.shareFile(path: String): Boolean {
    val file = File(path)
    if (!file.exists()) return false
    return try {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.share_via))
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooserIntent)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
