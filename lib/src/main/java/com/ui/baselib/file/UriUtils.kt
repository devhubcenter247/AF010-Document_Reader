@file:Suppress("DEPRECATION")

package com.ui.baselib.file

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import androidx.core.net.toUri

// ============================================================================
// region Get Path from URI
// ============================================================================

/**
 * Get the real file path from a URI.
 * Handles file://, content://, and document URIs.
 *
 * @param uri The URI to resolve
 * @return Absolute file path or null if cannot be resolved
 */
fun Context.getPathFromUri(uri: Uri): String? {
    return when (uri.scheme) {
        "file" -> uri.path
        "content" -> {
            try {
                when {
                    isGooglePhotosUri(uri) -> uri.lastPathSegment
                    isDownloadsDocument(uri) -> getDownloadsPath(uri)
                    isMediaDocument(uri) -> getMediaPath(uri)
                    isExternalStorageDocument(uri) -> getExternalStoragePath(uri)
                    else -> getContentPath(uri)
                }
            } catch (e: Exception) {
                Log.e("UriUtils", "Error getting path from URI", e)
                null
            }
        }
        else -> null
    }
}

/**
 * Get bitmap from a URI.
 *
 * @param uri The URI to load bitmap from
 * @return Bitmap or null if loading failed
 */
fun Context.getBitmapFromUri(uri: Uri): Bitmap? {
    return try {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        }
    } catch (e: Exception) {
        Log.e("UriUtils", "Error loading bitmap from URI", e)
        null
    }
}

// endregion

// ============================================================================
// region File Info from URI
// ============================================================================

/**
 * Information about a file resolved from URI.
 */
data class FileInfo(
    val path: String,
    val name: String,
    val mimeType: String,
    val size: Long
)

/**
 * Get detailed file information from a URI.
 *
 * @param uri The URI to get info from
 * @return FileInfo or null if cannot be resolved
 */
fun Context.getFileInfoFromUri(uri: Uri): FileInfo? {
    return try {
        contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE
            ),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getStringOrNull(0)
                    ?: copyFileToTemp(uri)
                    ?: return null

                FileInfo(
                    path = path,
                    name = cursor.getStringOrNull(1) ?: File(path).name,
                    mimeType = cursor.getStringOrNull(2) ?: getMimeType(path),
                    size = cursor.getLongOrNull(3) ?: File(path).length()
                )
            } else null
        }
    } catch (e: Exception) {
        Log.e("UriUtils", "Error getting file info", e)
        null
    }
}

// endregion

// ============================================================================
// region Internal Helpers - Path Resolution
// ============================================================================

private fun Context.getContentPath(uri: Uri, selection: String? = null, selectionArgs: Array<String>? = null): String? {
    return try {
        contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATA),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                cursor.getString(columnIndex)
            } else null
        }
    } catch (e: Exception) {
        Log.e("UriUtils", "Error getting content path", e)
        copyFileToTemp(uri)
    }
}

private fun Context.getDownloadsPath(uri: Uri): String? {
    val id = DocumentsContract.getDocumentId(uri)

    if (id.startsWith("raw:")) {
        return id.substring(4)
    }

    if (id.startsWith("msf:")) {
        return copyFileToTemp(uri)
    }

    return try {
        val contentUri = ContentUris.withAppendedId(
            "content://downloads/public_downloads".toUri(),
            id.toLong()
        )
        getContentPath(contentUri)
    } catch (e: NumberFormatException) {
        copyFileToTemp(uri)
    }
}

private fun Context.getMediaPath(uri: Uri): String? {
    val id = DocumentsContract.getDocumentId(uri)
    val split = id.split(":")
    val type = split[0]

    val contentUri = when (type.lowercase()) {
        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> null
    }

    return contentUri?.let {
        getContentPath(it, "_id=?", arrayOf(split[1]))
    }
}

private fun getExternalStoragePath(uri: Uri): String? {
    val docId = DocumentsContract.getDocumentId(uri)
    val split = docId.split(":")
    val type = split[0]

    return if ("primary".equals(type, ignoreCase = true)) {
        "${Environment.getExternalStorageDirectory()}/${split.getOrNull(1) ?: ""}"
    } else {
        "/storage/$type/${split.getOrNull(1) ?: ""}"
    }
}

private fun Context.copyFileToTemp(uri: Uri): String? {
    return try {
        // Document ids like "primary:Download/a.pdf" contain '/', which would point into a
        // directory that doesn't exist; keep only the last name part.
        val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "file"
        val tempFile = File(cacheDir, "temp_${System.currentTimeMillis()}_$name")
        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tempFile.absolutePath
    } catch (e: Exception) {
        Log.e("UriUtils", "Error copying file to temp", e)
        null
    }
}

// endregion

// ============================================================================
// region Internal Helpers - URI Type Detection
// ============================================================================

private fun isGooglePhotosUri(uri: Uri): Boolean =
    "com.google.android.apps.photos.content" == uri.authority

private fun isDownloadsDocument(uri: Uri): Boolean =
    "com.android.providers.downloads.documents" == uri.authority

private fun isMediaDocument(uri: Uri): Boolean =
    "com.android.providers.media.documents" == uri.authority

private fun isExternalStorageDocument(uri: Uri): Boolean =
    "com.android.externalstorage.documents" == uri.authority

// endregion

// ============================================================================
// region Internal Helpers - Utilities
// ============================================================================

private fun getMimeType(path: String): String {
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path))
        ?: "application/octet-stream"
}

private fun Cursor.getStringOrNull(columnIndex: Int): String? {
    return try {
        getString(columnIndex)
    } catch (e: Exception) {
        null
    }
}

private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
    return try {
        getLong(columnIndex)
    } catch (e: Exception) {
        null
    }
}

// endregion

