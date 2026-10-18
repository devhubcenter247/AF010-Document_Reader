package com.ui.baselib.file

import android.os.Environment
import android.webkit.MimeTypeMap

// ============================================================================
// region MIME Type Utilities
// ============================================================================

/**
 * Guess MIME type from file name based on extension.
 * Supports 100+ file types.
 *
 * @param fileName File name with extension
 * @return MIME type string
 */
fun guessMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        // Images
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heif"
        "ico" -> "image/x-icon"
        "svg" -> "image/svg+xml"
        "tiff", "tif" -> "image/tiff"
        // Videos
        "mp4" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "webm" -> "video/webm"
        "m4v" -> "video/x-m4v"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        "mpeg", "mpg" -> "video/mpeg"
        // Audio
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        "wma" -> "audio/x-ms-wma"
        "amr" -> "audio/amr"
        "aiff", "aif" -> "audio/aiff"
        // Documents
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "rtf" -> "application/rtf"
        "csv" -> "text/csv"
        "html", "htm" -> "text/html"
        "xml" -> "application/xml"
        "json" -> "application/json"
        // Archives
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        "7z" -> "application/x-7z-compressed"
        "tar" -> "application/x-tar"
        "gz" -> "application/gzip"
        // Executables
        "apk" -> "application/vnd.android.package-archive"
        "exe" -> "application/x-msdownload"
        "dmg" -> "application/x-apple-diskimage"
        // Fonts
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        // Code
        "js" -> "application/javascript"
        "css" -> "text/css"
        "java" -> "text/x-java-source"
        "kt" -> "text/x-kotlin"
        "py" -> "text/x-python"
        "php" -> "application/x-httpd-php"
        "c", "cpp", "h" -> "text/x-c"
        "swift" -> "text/x-swift"
        // Database
        "db" -> "application/x-sqlite3"
        "sql" -> "application/sql"
        // eBooks
        "epub" -> "application/epub+zip"
        "mobi" -> "application/x-mobipocket-ebook"
        // Certificates
        "pem", "crt", "cer" -> "application/x-x509-ca-cert"
        "p12", "pfx" -> "application/x-pkcs12"
        "key" -> "application/x-pem-file"
        // Config
        "ini", "conf", "cfg" -> "text/plain"
        "yaml", "yml" -> "application/x-yaml"
        // Design
        "ai" -> "application/postscript"
        "eps" -> "application/postscript"
        "ps" -> "application/postscript"
        // 3D
        "obj" -> "model/obj"
        "stl" -> "model/stl"
        "fbx" -> "application/octet-stream"
        "blend" -> "application/x-blender"
        else -> "application/octet-stream"
    }
}

/**
 * Get MIME type using Android's MimeTypeMap.
 * Falls back to application/octet-stream if unknown.
 *
 * @param path File path
 * @return MIME type string
 */
fun getMimeTypeFromPath(path: String): String {
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path))
        ?: "application/octet-stream"
}

/**
 * Get suggested storage directory for file based on MIME type.
 *
 * @param fileName File name with extension
 * @return Environment directory constant
 */
fun getSuggestedDirectoryForFile(fileName: String): String {
    val mime = guessMimeType(fileName)
    return when {
        mime.startsWith("image/") -> Environment.DIRECTORY_PICTURES
        mime.startsWith("video/") -> Environment.DIRECTORY_MOVIES
        mime.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
        mime.startsWith("application/") || mime.startsWith("text/") -> Environment.DIRECTORY_DOCUMENTS
        else -> Environment.DIRECTORY_DOWNLOADS
    }
}

/**
 * Check if file is an image based on extension.
 */
fun isImageFile(fileName: String): Boolean {
    return guessMimeType(fileName).startsWith("image/")
}

/**
 * Check if file is a video based on extension.
 */
fun isVideoFile(fileName: String): Boolean {
    return guessMimeType(fileName).startsWith("video/")
}

/**
 * Check if file is audio based on extension.
 */
fun isAudioFile(fileName: String): Boolean {
    return guessMimeType(fileName).startsWith("audio/")
}

/**
 * Check if file is a document based on extension.
 */
fun isDocumentFile(fileName: String): Boolean {
    val mime = guessMimeType(fileName)
    return mime.startsWith("application/") || mime.startsWith("text/")
}

// endregion
