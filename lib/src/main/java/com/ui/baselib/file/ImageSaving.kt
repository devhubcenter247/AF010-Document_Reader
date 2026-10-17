@file:Suppress("DEPRECATION")

package com.ui.baselib.file

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

// ============================================================================
// region Save to Internal Storage
// ============================================================================

/**
 * Save bitmap to internal storage (suspend version).
 * Runs on IO dispatcher.
 *
 * @param bitmap The bitmap to save
 * @param fileName File name without extension
 * @return Absolute path to saved file, or null if failed
 */
suspend fun Context.saveImageToInternalStorage(bitmap: Bitmap, fileName: String): String? =
    withContext(Dispatchers.IO) {
        val file = File(filesDir, "$fileName.png")
        try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            file.absolutePath
        } catch (e: IOException) {
            Log.e("ImageSaving", "Error saving to internal storage", e)
            null
        }
    }

/**
 * Save bitmap to internal storage with callback.
 * File I/O runs on IO thread, callback invoked on Main thread.
 *
 * @param bitmap The bitmap to save
 * @param fileName File name without extension
 * @param callback Callback with absolute path to saved file, or null if failed
 */
fun Context.saveImageToInternalStorage(
    bitmap: Bitmap,
    fileName: String,
    callback: (String?) -> Unit
) {
    callbackScope().launch(Dispatchers.IO) {
        val file = File(filesDir, "$fileName.png")
        val result = try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            file.absolutePath
        } catch (e: IOException) {
            Log.e("ImageSaving", "Error saving to internal storage", e)
            null
        }
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}

// endregion

// ============================================================================
// region Save to Gallery (Pictures)
// ============================================================================

/**
 * Save bitmap to gallery (Pictures folder).
 * Handles both Android 10+ (scoped storage) and legacy versions.
 *
 * @param bitmap The bitmap to save
 * @param fileName File name with extension
 * @param folderName Subfolder name in Pictures directory
 * @param mimeType MIME type (default: image/png)
 * @return URI of saved image, or null if failed
 */
suspend fun Context.saveImageToGallery(
    bitmap: Bitmap,
    fileName: String,
    folderName: String = "Data",
    mimeType: String = "image/png"
): Uri? = withContext(Dispatchers.IO){
    val contentResolver = contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}${File.separator}$folderName"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        var uri: Uri? = null
        try {
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null
            val written = contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(compressFormatFor(mimeType), 100, outputStream)
            } ?: false
            if (!written) throw IOException("Failed to write image")
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
            uri
        } catch (e: Exception) {
            Log.e("ImageSaving", "Error saving image to gallery", e)
            // Don't leave an invisible pending row behind.
            uri?.let { runCatching { contentResolver.delete(it, null, null) } }
            null
        }
    } else {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            folderName
        )

        try {
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val imageFile = File(directory, fileName)
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(compressFormatFor(mimeType), 100, outputStream)
            }

            val uri = Uri.fromFile(imageFile)
            MediaScannerConnection.scanFile(
                this@saveImageToGallery,
                arrayOf(imageFile.absolutePath),
                arrayOf(mimeType)
            ) { _, _ -> }
            uri
        } catch (e: IOException) {
            Log.e("ImageSaving", "Error saving image to gallery", e)
            null
        }
    }
}

/**
 * Save bitmap to gallery (Pictures folder) with callback.
 * File I/O runs on IO thread, callback invoked on Main thread.
 *
 * @param bitmap The bitmap to save
 * @param fileName File name with extension
 * @param folderName Subfolder name in Pictures directory
 * @param mimeType MIME type (default: image/png)
 * @param callback Callback with URI of saved image, or null if failed
 */
fun Context.saveImageToGallery(
    bitmap: Bitmap,
    fileName: String,
    folderName: String = "Data",
    mimeType: String = "image/png",
    callback: (Uri?) -> Unit
) {
    callbackScope().launch(Dispatchers.IO) {
        val result = saveImageToGallery(bitmap, fileName, folderName, mimeType)
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}

// endregion

// ============================================================================
// region Move to Gallery
// ============================================================================

/**
 * Copy a cached file into the gallery (suspend version). The cached file is kept.
 * Runs on IO dispatcher.
 *
 * @param cacheFilePath Path to the cached file
 * @param destinationPath For Q+: relative path (e.g., "Pictures/MyApp"). For legacy: absolute path
 * @param mimeType MIME type (default: image/png)
 * @return URI of moved image, or null if failed
 */
suspend fun Context.moveImageToGallery(
    cacheFilePath: String,
    destinationPath: String,
    mimeType: String = "image/png"
): Uri? = withContext(Dispatchers.IO) {
    val cacheFile = File(cacheFilePath)
    if (!cacheFile.exists()) {
        Log.w("ImageSaving", "Cache file does not exist: $cacheFilePath")
        return@withContext null
    }

    val contentResolver = contentResolver

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val relativePath = when {
            destinationPath.contains("Pictures") ->
                "Pictures" + destinationPath.substringAfter("Pictures")
            destinationPath.contains("DCIM") ->
                "DCIM" + destinationPath.substringAfter("DCIM")
            else -> "Pictures${File.separator}${destinationPath.substringAfterLast(File.separator)}"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, cacheFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        try {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)?.also { uri ->
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    cacheFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            Log.e("ImageSaving", "Error moving image to gallery", e)
            null
        }
    } else {
        val directory = File(destinationPath)

        try {
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e("ImageSaving", "Failed to create directory: $destinationPath")
                return@withContext null
            }

            val newFile = File(directory, cacheFile.name)
            if (newFile.exists()) {
                newFile.delete()
            }

            cacheFile.copyTo(newFile, overwrite = true)
            val uri = Uri.fromFile(newFile)

            MediaScannerConnection.scanFile(
                this@moveImageToGallery,
                arrayOf(newFile.absolutePath),
                arrayOf(mimeType)
            ) { _, _ -> }

            uri
        } catch (e: IOException) {
            Log.e("ImageSaving", "Error moving image to gallery", e)
            null
        }
    }
}

/**
 * Copy a cached file into the gallery with callback. The cached file is kept.
 * File I/O runs on IO thread, callback invoked on Main thread.
 *
 * @param cacheFilePath Path to the cached file
 * @param destinationPath For Q+: relative path (e.g., "Pictures/MyApp"). For legacy: absolute path
 * @param mimeType MIME type (default: image/png)
 * @param callback Callback with URI of moved image, or null if failed
 */
fun Context.moveImageToGallery(
    cacheFilePath: String,
    destinationPath: String,
    mimeType: String = "image/png",
    callback: (Uri?) -> Unit
) {
    callbackScope().launch(Dispatchers.IO) {
        val result = moveImageToGallery(cacheFilePath, destinationPath, mimeType)
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}

// endregion

// ============================================================================
// region Save to Downloads
// ============================================================================

/**
 * Save bitmap to Downloads folder (suspend version).
 * Runs on IO dispatcher.
 *
 * @param bitmap The bitmap to save
 * @param fileName File name with extension
 * @param folderName Subfolder name in Downloads directory
 * @return Saved file path, or empty string if failed
 */
suspend fun Context.saveImageToDownloads(
    bitmap: Bitmap,
    fileName: String,
    folderName: String
): String = withContext(Dispatchers.IO) {
    val cacheFile = File(cacheDir, fileName)

    try {
        FileOutputStream(cacheFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }

        val result = saveFileToDownloads(cacheFile.absolutePath, fileName, folderName)
        cacheFile.delete()
        result
    } catch (e: Exception) {
        Log.e("ImageSaving", "Error saving image to downloads", e)
        ""
    }
}

/**
 * Save bitmap to Downloads folder with callback.
 * File I/O runs on IO thread, callback invoked on Main thread.
 *
 * @param bitmap The bitmap to save
 * @param fileName File name with extension
 * @param folderName Subfolder name in Downloads directory
 * @param callback Callback with the saved file path, or empty string if failed
 */
fun Context.saveImageToDownloads(
    bitmap: Bitmap,
    fileName: String,
    folderName: String,
    callback: (String) -> Unit
) {
    callbackScope().launch(Dispatchers.IO) {
        val result = saveImageToDownloads(bitmap, fileName, folderName)
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}

/**
 * Save/move a file to Downloads folder (suspend version).
 * Runs on IO dispatcher.
 *
 * @param cachePath Path to the source file
 * @param fileName File name with extension
 * @param folderName Subfolder name in Downloads directory
 * @return Saved file path, or empty string if failed
 */
suspend fun Context.saveFileToDownloads(
    cachePath: String,
    fileName: String,
    folderName: String
): String = withContext(Dispatchers.IO) {
    val contentResolver = contentResolver
    val cacheFile = File(cachePath)

    if (!cacheFile.exists()) {
        return@withContext ""
    }
    val mime = guessMimeType(fileName)

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}${File.separator}$folderName"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { savedUri ->
                contentResolver.openOutputStream(savedUri)?.use { outputStream ->
                    FileInputStream(cacheFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(savedUri, contentValues, null, null)

                getMediaStorePath(savedUri)
            } ?: ""
        } else {
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                folderName
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val newFile = File(downloadsDir, fileName)
            cacheFile.copyTo(newFile, overwrite = true)

            MediaScannerConnection.scanFile(
                this@saveFileToDownloads,
                arrayOf(newFile.absolutePath),
                arrayOf(mime)
            ) { _, _ -> }

            newFile.absolutePath
        }
    } catch (e: Exception) {
        Log.e("ImageSaving", "Error saving file to downloads", e)
        ""
    }
}

/**
 * Save/move a file to Downloads folder with callback.
 * File I/O runs on IO thread, callback invoked on Main thread.
 *
 * @param cachePath Path to the source file
 * @param fileName File name with extension
 * @param folderName Subfolder name in Downloads directory
 * @param callback Callback with the saved file path, or empty string if failed
 */
fun Context.saveFileToDownloads(
    cachePath: String,
    fileName: String,
    folderName: String,
    callback: (String) -> Unit
) {
    callbackScope().launch(Dispatchers.IO) {
        val result = saveFileToDownloads(cachePath, fileName, folderName)
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}

// endregion

// ============================================================================
// region Save to Cache
// ============================================================================

/**
 * Save bitmap to cache directory (suspend version).
 * Runs on IO dispatcher.
 *
 * @param bitmap The bitmap to save
 * @param fileName Optional file name (default: timestamp-based)
 * @param quality Compression quality (0-100); ignored by PNG, which is lossless
 * @return File object of saved file
 */
suspend fun Context.saveBitmapToCache(
    bitmap: Bitmap,
    fileName: String = "${System.currentTimeMillis()}_image.png",
    quality: Int = 70
): File = withContext(Dispatchers.IO) {
    val file = File(cacheDir, fileName)
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)
    }
    file
}

/**
 * Save bitmap to cache directory with callback.
 * File I/O runs on IO thread, callback invoked on Main thread.
 *
 * @param bitmap The bitmap to save
 * @param fileName Optional file name (default: timestamp-based)
 * @param quality Compression quality (0-100); ignored by PNG, which is lossless
 * @param callback Callback with File object of saved file
 */
fun Context.saveBitmapToCache(
    bitmap: Bitmap,
    fileName: String = "${System.currentTimeMillis()}_image.png",
    quality: Int = 70,
    callback: (File) -> Unit
) {
    callbackScope().launch(Dispatchers.IO) {
        val result = saveBitmapToCache(bitmap, fileName, quality)
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}

/**
 * Save a drawable resource to cache directory (suspend version).
 * Runs on IO dispatcher.
 *
 * @param drawableId Resource ID of the drawable
 * @return Absolute path to saved file, or null if failed
 */
suspend fun Context.saveDrawableToCache(drawableId: Int): String? =
    withContext(Dispatchers.IO) {
        try {
            val drawable = androidx.core.content.ContextCompat.getDrawable(
                this@saveDrawableToCache, drawableId
            )
            val bitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
            val file = File(cacheDir, "${System.currentTimeMillis()}_drawable.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 70, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("ImageSaving", "Error saving drawable to cache", e)
            null
        }
    }

/**
 * Save a drawable resource to cache directory with callback.
 * File I/O runs on IO thread, callback invoked on Main thread.
 *
 * @param drawableId Resource ID of the drawable
 * @param callback Callback with absolute path to saved file, or null if failed
 */
fun Context.saveDrawableToCache(drawableId: Int, callback: (String?) -> Unit) {
    callbackScope().launch(Dispatchers.IO) {
        val result = saveDrawableToCache(drawableId)
        withContext(Dispatchers.Main) {
            callback(result)
        }
    }
}

// endregion

// ============================================================================
// region Internal Helpers
// ============================================================================

/**
 * Ties callback-style saves to the caller's lifecycle when it has one, so the callback can't
 * run after the Activity is destroyed.
 */
private fun Context.callbackScope(): CoroutineScope =
    (this as? LifecycleOwner)?.lifecycleScope ?: CoroutineScope(Dispatchers.IO)

@Suppress("DEPRECATION")
private fun compressFormatFor(mimeType: String): Bitmap.CompressFormat = when (mimeType.lowercase()) {
    "image/jpeg", "image/jpg" -> Bitmap.CompressFormat.JPEG
    "image/webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    else -> Bitmap.CompressFormat.PNG
}

private fun Context.getMediaStorePath(uri: Uri): String {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME
            )
            var path = ""

            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val relativePath = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    )
                    val fileName = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    )
                    path = "${Environment.getExternalStorageDirectory()}/$relativePath$fileName"
                }
            }
            path
        } else {
            uri.path ?: ""
        }
    } catch (e: Exception) {
        Log.e("ImageSaving", "Error getting media path", e)
        ""
    }
}

// endregion

// ============================================================================
// region Save File to Public Storage
// ============================================================================

/**
 * Save file to public storage directory.
 *
 * @param sourcePath Path to source file
 * @param destFileName Destination file name
 * @param environment Target directory (e.g., Environment.DIRECTORY_DOWNLOADS)
 * @param folderName Subfolder name
 * @return URI of saved file, or null if failed
 */
fun Context.saveFileToStorage(
    sourcePath: String,
    destFileName: String,
    environment: String = Environment.DIRECTORY_DOWNLOADS,
    folderName: String = "Downloads"
): Uri? {
    val src = File(sourcePath)
    if (!src.exists()) {
        Log.e("ImageSaving", "Source file does not exist: $sourcePath")
        return null
    }
    val fileName = destFileName.ifBlank { src.name }
    val mime = guessMimeType(fileName)

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveFileUsingMediaStore(src, fileName, mime, environment, folderName)
        } else {
            saveFileLegacy(src, fileName, mime, environment, folderName)
        }
    } catch (e: Exception) {
        Log.e("ImageSaving", "saveFileToStorage error", e)
        null
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun Context.saveFileUsingMediaStore(
    src: File,
    fileName: String,
    mime: String,
    environment: String,
    folderName: String
): Uri? {
    val cr = contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "$environment/$folderName")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
        put(MediaStore.MediaColumns.SIZE, src.length())
        put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        put(MediaStore.MediaColumns.DATE_MODIFIED, System.currentTimeMillis() / 1000)
    }
    val contentUri = mediaStoreCollection(environment, mime)
    var uri: Uri? = null
    try {
        uri = cr.insert(contentUri, values)
        if (uri != null) {
            cr.openOutputStream(uri)?.use { out ->
                FileInputStream(src).use { it.copyTo(out) }
            } ?: throw IOException("OpenOutputStream failed")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            cr.update(uri, values, null, null)
            return uri
        }
    } catch (e: Exception) {
        Log.e("ImageSaving", "saveFileUsingMediaStore error", e)
        uri?.let { runCatching { cr.delete(it, null, null) } }
    }
    return null
}

/** The MediaStore collection whose allowed top-level directory matches [environment]. */
@RequiresApi(Build.VERSION_CODES.Q)
private fun mediaStoreCollection(environment: String, mime: String): Uri = when (environment) {
    Environment.DIRECTORY_DOWNLOADS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
    Environment.DIRECTORY_PICTURES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    Environment.DIRECTORY_DOCUMENTS -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    Environment.DIRECTORY_MOVIES -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    Environment.DIRECTORY_MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    else -> when {
        mime.startsWith("image/") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        mime.startsWith("video/") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        mime.startsWith("audio/") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }
}

@Suppress("DEPRECATION")
private fun Context.saveFileLegacy(
    src: File,
    fileName: String,
    mime: String,
    environment: String,
    folderName: String
): Uri? {
    try {
        val baseDir = when (environment) {
            Environment.DIRECTORY_DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            Environment.DIRECTORY_PICTURES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            Environment.DIRECTORY_DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            Environment.DIRECTORY_MOVIES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            Environment.DIRECTORY_MUSIC -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            else -> when {
                mime.startsWith("image/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                mime.startsWith("video/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                mime.startsWith("audio/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val dir = File(baseDir, folderName).apply { if (!exists()) mkdirs() }
        val dst = File(dir, fileName)

        src.copyTo(dst, overwrite = true)

        MediaScannerConnection.scanFile(this, arrayOf(dst.absolutePath), arrayOf(mime), null)
        return Uri.fromFile(dst)
    } catch (e: Exception) {
        Log.e("ImageSaving", "saveFileLegacy error", e)
        return null
    }
}

// endregion

// ============================================================================
// region Save Bytes/Stream to Downloads
// ============================================================================

/**
 * Save bytes to downloads folder.
 *
 * @param bytes Byte array to save
 * @param fileName File name with extension
 * @param environment Target directory
 * @param folderName Subfolder name
 * @return URI of saved file, or null if failed
 */
fun Context.saveBytesToDownloads(
    bytes: ByteArray,
    fileName: String,
    environment: String = Environment.DIRECTORY_DOWNLOADS,
    folderName: String = "Downloads"
): Uri? = ByteArrayInputStream(bytes).use {
    saveStreamToStorage(it, fileName, environment, folderName)
}

/**
 * Save input stream to storage.
 *
 * @param input Input stream to save
 * @param fileName File name with extension
 * @param environment Target directory
 * @param folderName Subfolder name
 * @return URI of saved file, or null if failed
 */
fun Context.saveStreamToStorage(
    input: InputStream,
    fileName: String,
    environment: String = Environment.DIRECTORY_DOWNLOADS,
    folderName: String = "Downloads"
): Uri? {
    val mime = guessMimeType(fileName)
    val cr = contentResolver

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "$environment${File.separator}$folderName")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        var uri: Uri? = null
        try {
            uri = cr.insert(mediaStoreCollection(environment, mime), values)
            if (uri != null) {
                cr.openOutputStream(uri)?.use { out -> input.copyTo(out) }
                    ?: throw IOException("OpenOutputStream failed")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                cr.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            Log.e("ImageSaving", "saveStreamToStorage Q+ error", e)
            if (uri != null) runCatching { cr.delete(uri, null, null) }
            null
        }
    } else {
        try {
            @Suppress("DEPRECATION")
            val base = Environment.getExternalStoragePublicDirectory(environment)
            val dir = File(base, folderName).apply { if (!exists()) mkdirs() }
            val dst = File(dir, fileName)
            FileOutputStream(dst).use { out -> input.copyTo(out) }
            val uri = Uri.fromFile(dst)
            MediaScannerConnection.scanFile(this, arrayOf(dst.absolutePath), arrayOf(mime), null)
            uri
        } catch (e: Exception) {
            Log.e("ImageSaving", "saveStreamToStorage pre-Q error", e)
            null
        }
    }
}

// endregion
