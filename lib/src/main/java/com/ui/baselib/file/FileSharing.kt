package com.ui.baselib.file

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ui.baselib.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
/**
 * Throttler to prevent rapid share actions (e.g., double-click protection).
 */
object ShareThrottler {
    private var lastTs = 0L
    private const val GAP = 800L

    /**
     * Check if share action is allowed (not throttled).
     * @return true if allowed, false if throttled
     */
    fun allow(): Boolean {
        val now = SystemClock.elapsedRealtime()
        return if (now - lastTs > GAP) {
            lastTs = now
            true
        } else false
    }

    /**
     * Reset throttle state.
     */
    fun reset() {
        lastTs = 0L
    }
}

/**
 * Share multiple images by their file paths.
 *
 * @param imagePaths List of absolute file paths to share
 * @param title Chooser dialog title
 */
fun Context.shareImages(
    imagePaths: List<String>,
    title: String = getString(R.string.baselib_share_images_title)
) {
    val imageUris = ArrayList<Uri>()

    for (path in imagePaths) {
        val file = File(path)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            imageUris.add(uri)
        }
    }

    if (imageUris.isEmpty()) {
        Toast.makeText(this, R.string.baselib_no_images_to_share, Toast.LENGTH_SHORT).show()
        return
    }

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
        type = "image/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, title))
}
/**
 * Converts a `file://` Uri into a `content://` Uri via [FileProvider] so it can be passed
 * through [Intent.EXTRA_STREAM] without tripping `FileUriExposedException` on API 24+.
 * Uris with any other scheme (e.g. already `content://`) are returned unchanged.
 */
fun Uri.toShareableUri(context: Context): Uri {
    if (scheme != "file") return this
    val file = path?.let(::File) ?: return this
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}
/**
 * Share a single file by its path.
 *
 * @param path Absolute file path
 * @param mimeType MIME type of the file (default: image/png)
 * @param title Chooser dialog title
 */
fun Context.shareFile(
    path: String,
    mimeType: String = "*/*",
    title: String = getString(R.string.baselib_share_file_title)
) {
    val file = File(path)

    if (!file.exists()) {
        Toast.makeText(this, R.string.baselib_file_not_found, Toast.LENGTH_SHORT).show()
        return
    }

    val fileUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, fileUri)
        type = mimeType
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    startActivity(Intent.createChooser(shareIntent, title))
}

/**
 * Share multiple files by their paths.
 *
 * @param paths List of absolute file paths
 * @param mimeType MIME type of the files (default: wildcard for mixed types)
 * @param title Chooser dialog title
 */
fun Context.shareFiles(
    paths: List<String>,
    mimeType: String = "*/*",
    title: String = getString(R.string.baselib_share_files_title)
) {
    val uris = paths.mapNotNull { path ->
        val file = File(path)
        if (file.exists()) {
            FileProvider.getUriForFile(this, "$packageName.provider", file)
        } else null
    }

    if (uris.isEmpty()) {
        Toast.makeText(this, R.string.baselib_no_files_to_share, Toast.LENGTH_SHORT).show()
        return
    }

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND_MULTIPLE
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        type = mimeType
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    startActivity(Intent.createChooser(shareIntent, title))
}

/**
 * Share a bitmap by saving it to cache first.
 *
 * @param bitmap The bitmap to share
 * @param fileName File name for the cached image
 * @param title Chooser dialog title
 * @param deleteDelay Delay in ms before deleting the cached file, or null (default) to keep it.
 *   The receiving app reads the file whenever the user finishes picking a target, which can
 *   take longer than any fixed delay; the next share with the same [fileName] overwrites it.
 */
fun Context.shareBitmap(
    bitmap: Bitmap,
    fileName: String = "shared_image.png",
    title: String = getString(R.string.baselib_share_photo_title),
    deleteDelay: Long? = null
) {
    try {
        val cachePath = File(cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, fileName)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }

        val fileUri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, fileUri)
            type = "image/png"
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(shareIntent, title))

        if (deleteDelay != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (file.exists()) {
                    file.delete()
                }
            }, deleteDelay)
        }

    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(this, R.string.baselib_share_image_failed, Toast.LENGTH_SHORT).show()
    }
}
// endregion
