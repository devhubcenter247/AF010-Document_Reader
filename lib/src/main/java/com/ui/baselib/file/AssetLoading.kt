package com.ui.baselib.file

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import java.io.IOException

// ============================================================================
// region Load Bitmap from Assets
// ============================================================================

/**
 * Load a bitmap from assets folder.
 *
 * @param filePath Path to the file in assets folder
 * @return Bitmap or null if loading failed
 */
fun Context.loadBitmapFromAssets(filePath: String): Bitmap? {
    return try {
        assets.open(filePath).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

/**
 * Load a bitmap from assets folder with callback.
 *
 * @param filePath Path to the file in assets folder
 * @param callback Callback with the loaded bitmap (null if failed)
 */
inline fun Context.loadBitmapFromAssets(filePath: String, callback: (Bitmap?) -> Unit) {
    callback(loadBitmapFromAssets(filePath))
}

/**
 * Load a bitmap from assets folder, throwing exception if failed.
 *
 * @param filePath Path to the file in assets folder
 * @return Bitmap
 * @throws IOException if loading failed
 */
@Throws(IOException::class)
fun Context.loadBitmapFromAssetsOrThrow(filePath: String): Bitmap {
    return assets.open(filePath).use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
            ?: throw IOException("Failed to decode bitmap from: $filePath")
    }
}

// endregion

// ============================================================================
// region Load Drawable from Assets
// ============================================================================

/**
 * Load a drawable from assets folder.
 *
 * @param filePath Path to the file in assets folder
 * @return Drawable or null if loading failed
 */
fun Context.loadDrawableFromAssets(filePath: String): Drawable? {
    return try {
        assets.open(filePath).use { inputStream ->
            Drawable.createFromStream(inputStream, null)
        }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

// endregion

// ============================================================================
// region Load Text from Assets
// ============================================================================

/**
 * Load text content from assets folder.
 *
 * @param filePath Path to the file in assets folder
 * @return File content as String or null if loading failed
 */
fun Context.loadTextFromAssets(filePath: String): String? {
    return try {
        assets.open(filePath).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

/**
 * Load JSON content from assets folder.
 * Same as loadTextFromAssets but with semantic naming.
 *
 * @param filePath Path to the JSON file in assets folder
 * @return JSON content as String or null if loading failed
 */
fun Context.loadJsonFromAssets(filePath: String): String? = loadTextFromAssets(filePath)

// endregion

// ============================================================================
// region List Assets
// ============================================================================

/**
 * List files in an assets folder.
 *
 * @param path Path to the folder in assets
 * @return Array of file names or empty array if failed
 */
fun Context.listAssets(path: String = ""): Array<String> {
    return try {
        assets.list(path) ?: emptyArray()
    } catch (e: IOException) {
        e.printStackTrace()
        emptyArray()
    }
}

// endregion

// ============================================================================
// region Activity Extensions
// ============================================================================

/**
 * Open bitmap from assets (Activity extension with callback).
 *
 * @param filePath Path to the file in assets folder
 * @param callback Callback with the loaded bitmap (null if failed)
 */
fun Activity.openFromAssets(filePath: String, callback: (Bitmap?) -> Unit) {
    try {
        assets.open(filePath).use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)
            callback(bitmap)
        }
    } catch (e: IOException) {
        callback(null)
        e.printStackTrace()
    }
}

/**
 * Open bitmap from assets (Activity extension).
 *
 * @param filePath Path to the file in assets folder
 * @return Bitmap or null if loading failed
 */
fun Activity.openFromAssets(filePath: String): Bitmap? {
    return try {
        assets.open(filePath).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: IOException) {
        e.printStackTrace()
        null
    }
}

/**
 * Get bitmap from Uri (Activity extension).
 *
 * @param uri The URI to load bitmap from
 * @return Bitmap or null if loading failed
 */
fun Activity.bitmapFromUri(uri: Uri): Bitmap? {
    return try {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        Log.e("AssetLoading", "Error loading bitmap from URI", e)
        null
    }
}

// endregion