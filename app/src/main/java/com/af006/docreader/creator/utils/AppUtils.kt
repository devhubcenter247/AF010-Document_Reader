package com.af010.creator.docreader.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.af010.creator.docreader.database.FavoriteDao
import com.af010.creator.docreader.database.RecentDao
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.FileTypeFilter
import com.af010.creator.docreader.model.RecentDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

object AppUtils {
    val pdfExtensions = listOf("pdf")
    val excelExtensions = listOf("xls", "xlsx", "xlsm")
    val docExtensions = listOf("doc", "docx")
    val pptExtensions = listOf("ppt", "pptx")
    val txtExtensions = listOf("txt")

    private val extensionToType: Map<String, DocumentType> = buildMap {
        pdfExtensions.forEach { put(it, DocumentType.Pdf) }
        docExtensions.forEach { put(it, DocumentType.Doc) }
        excelExtensions.forEach { put(it, DocumentType.Excel) }
        pptExtensions.forEach { put(it, DocumentType.Ppt) }
        txtExtensions.forEach { put(it, DocumentType.Txt) }
    }

    fun documentTypeOf(file: File): DocumentType? =
        extensionToType[file.extension.lowercase(Locale.ROOT)]

    private fun documentTypeOf(path: String): DocumentType? =
        extensionToType[path.substringAfterLast('.', "").lowercase(Locale.ROOT)]

    val filesUri: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }

    private fun fallbackId(path: String): Long =
        -(path.hashCode().toLong() and 0x7FFFFFFFL) - 1L

    private fun isLocalPath(path: String): Boolean = path.startsWith("/")

    fun hasStoragePermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    @get:WorkerThread
    val documentPath: String
        get() {
            val filePath = File(
                "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/Pdf Reader"
            )
            if (!filePath.exists()) filePath.mkdirs()
            return filePath.absolutePath
        }

    suspend fun ensureDocumentDirectory(): String = withContext(Dispatchers.IO) {
        documentPath
    }

    suspend fun getSdStorageDirectories(context: Context): List<String> =
        withContext(Dispatchers.IO) {
            context.getExternalFilesDirs(null).filterNotNull()
                .filter { Environment.isExternalStorageRemovable(it) }
                .map { it.path.substringBefore("/Android/") }
                .distinct()
        }

    fun notifyMediaScanner(context: Context, vararg paths: String) {
        MediaScannerConnection.scanFile(context.applicationContext, paths, null, null)
    }

    private suspend fun scanPaths(context: Context, paths: List<String>): Map<String, Uri?> {
        if (paths.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, Uri?>()
        withTimeoutOrNull(10_000L.milliseconds) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(
                    context.applicationContext, paths.toTypedArray(), null
                ) { path, uri ->
                    val done = synchronized(results) {
                        results[path] = uri
                        results.size >= paths.size
                    }
                    if (done && cont.isActive) cont.resume(Unit)
                }
            }
        }
        return synchronized(results) { results.toMap() }
    }

    private fun queryMediaId(context: Context, path: String): Long? =
        context.contentResolver.query(
            filesUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?",
            arrayOf(path),
            null
        )?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }

    private fun loadMediaIds(context: Context, extensions: Collection<String>): Map<String, Long> {
        if (extensions.isEmpty()) return emptyMap()
        val dataCol = MediaStore.Files.FileColumns.DATA
        val selection = extensions.joinToString(" OR ") { "$dataCol LIKE ?" }
        val args = extensions.map { "%.$it" }.toTypedArray()
        val result = HashMap<String, Long>()
        context.contentResolver.query(
            filesUri,
            arrayOf(MediaStore.Files.FileColumns._ID, dataCol),
            selection,
            args,
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val pathIdx = c.getColumnIndexOrThrow(dataCol)
            while (c.moveToNext()) {
                val path = c.getString(pathIdx) ?: continue
                result[path] = c.getLong(idIdx)
            }
        }
        return result
    }

    fun scanAllDocumentsFlow(
        context: Context,
        recentDao: RecentDao,
        favoriteDao: FavoriteDao,
        filter: FileTypeFilter = FileTypeFilter.All
    ): Flow<RecentDocument> = flow {
        val extensions = extensionToType.filterValues(filter::accepts).keys
        if (extensions.isEmpty()) return@flow

        val sync = PathSync.load(recentDao, favoriteDao)

        val idCol = MediaStore.Files.FileColumns._ID
        val dataCol = MediaStore.Files.FileColumns.DATA
        val dateCol = MediaStore.Files.FileColumns.DATE_MODIFIED
        val sizeCol = MediaStore.Files.FileColumns.SIZE

        val selection = extensions.joinToString(" OR ", "(", ")") { "$dataCol LIKE ?" } +
                " AND $sizeCol > 0"
        val selectionArgs = extensions.map { "%.$it" }.toTypedArray()

        context.contentResolver.query(
            filesUri,
            arrayOf(idCol, dataCol, dateCol, sizeCol),
            selection,
            selectionArgs,
            "$dateCol DESC"
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(idCol)
            val pathIdx = c.getColumnIndexOrThrow(dataCol)
            val dateIdx = c.getColumnIndexOrThrow(dateCol)
            val sizeIdx = c.getColumnIndexOrThrow(sizeCol)

            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val path = c.getString(pathIdx) ?: continue
                if (path.contains("/.")) continue
                val type = documentTypeOf(path) ?: continue

                val doc = RecentDocument(
                    mediaId = c.getLong(idIdx),
                    path = path,
                    lastModified = c.getLong(dateIdx) * 1000L,
                    size = c.getLong(sizeIdx),
                    type = type
                )
                sync.update(doc)
                emit(doc)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun queryImages(context: Context): List<RecentDocument> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.SIZE
        )
        context.contentResolver.query(
            uri,
            projection,
            "${MediaStore.Images.Media.SIZE} > 0",
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
            val dateIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            buildList {
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = c.getLong(idIdx)
                    val path = (if (dataIdx != -1) c.getString(dataIdx) else null)
                        ?: ContentUris.withAppendedId(uri, id).toString()
                    add(
                        RecentDocument(
                            mediaId = id,
                            path = path,
                            lastModified = c.getLong(dateIdx) * 1000L,
                            size = c.getLong(sizeIdx),
                            type = DocumentType.Image
                        )
                    )
                }
            }
        } ?: emptyList()
    }

    fun scanAllDocumentsByFileSystemFlow(
        context: Context,
        recentDao: RecentDao,
        favoriteDao: FavoriteDao,
        filter: FileTypeFilter = FileTypeFilter.All
    ): Flow<RecentDocument> = flow {
        val extensions = extensionToType.filterValues(filter::accepts).keys
        if (extensions.isEmpty()) return@flow

        val sync = PathSync.load(recentDao, favoriteDao)
        val mediaIds = loadMediaIds(context, extensions)

        val roots = getSdStorageDirectories(context).toMutableList()
        val externalStorage = Environment.getExternalStorageDirectory()
        if (externalStorage.exists()) roots.add(externalStorage.absolutePath)

        emitAll(
            scanFilesFlow(
                roots = roots.map(::File),
                fileFilter = { file -> documentTypeOf(file)?.let(filter::accepts) == true },
                buildModel = { file ->
                    val path = file.absolutePath
                    RecentDocument(
                        mediaId = mediaIds[path] ?: fallbackId(path),
                        path = path,
                        lastModified = file.lastModified(),
                        size = file.length(),
                        type = documentTypeOf(file) ?: DocumentType.Doc
                    ).also { sync.update(it) }
                }
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun renameDocument(
        context: Context,
        doc: RecentDocument,
        newName: String,
        recentDao: RecentDao,
        favoriteDao: FavoriteDao
    ): RecentDocument? = withContext(Dispatchers.IO) {
        val oldFile = File(doc.path)
        val parent = oldFile.parentFile ?: return@withContext null
        val newFile = File(parent, newName)
        if (!oldFile.exists() || newFile.exists()) return@withContext null
        if (!oldFile.renameTo(newFile)) return@withContext null

        val newPath = newFile.absolutePath
        val scanned = scanPaths(context, listOf(doc.path, newPath))
        val newId = scanned[newPath]?.let(ContentUris::parseId)?.takeIf { it > 0 }
            ?: queryMediaId(context, newPath)
            ?: fallbackId(newPath)

        if (doc.mediaId != newId) {
            recentDao.updateId(doc.mediaId, newId)
            favoriteDao.updateId(doc.mediaId, newId)
        }
        recentDao.updatePath(newId, newPath)
        favoriteDao.updateName(newId, newPath)

        doc.copy(
            mediaId = newId,
            path = newPath,
            lastModified = newFile.lastModified()
        )
    }

    suspend fun deleteDocument(
        context: Context,
        doc: RecentDocument,
        recentDao: RecentDao,
        favoriteDao: FavoriteDao
    ): Boolean = withContext(Dispatchers.IO) {
        val file = File(doc.path)
        if (file.exists() && !file.delete()) return@withContext false
        scanPaths(context, listOf(doc.path))
        recentDao.deleteById(doc.mediaId)
        favoriteDao.deleteById(doc.mediaId)
        true
    }

    suspend fun pruneMissingFiles(recentDao: RecentDao, favoriteDao: FavoriteDao) =
        withContext(Dispatchers.IO) {
            recentDao.getAll().first()
                .filter { isLocalPath(it.path) && !File(it.path).exists() }
                .forEach { recentDao.deleteById(it.mediaId) }
            favoriteDao.getAll().first()
                .filter { isLocalPath(it.path) && !File(it.path).exists() }
                .forEach { favoriteDao.deleteById(it.mediaId) }
        }

    suspend fun migrateLegacyIds(
        context: Context,
        recentDao: RecentDao,
        favoriteDao: FavoriteDao
    ) = withContext(Dispatchers.IO) {
        val mediaIds = loadMediaIds(context, extensionToType.keys)

        fun newIdFor(path: String): Long? {
            if (!isLocalPath(path) || !File(path).exists()) return null
            return mediaIds[path] ?: fallbackId(path)
        }

        recentDao.getAll().first().forEach { entry ->
            if (entry.mediaId != entry.path.hashCode().toLong()) return@forEach
            val oldId = entry.mediaId
            val newId = newIdFor(entry.path)
            when {
                newId == null -> if (isLocalPath(entry.path)) recentDao.deleteById(oldId)
                newId != oldId -> recentDao.updateId(oldId, newId)
            }
        }

        favoriteDao.getAll().first().forEach { entry ->
            if (entry.mediaId != entry.path.hashCode().toLong()) return@forEach
            val oldId = entry.mediaId
            val newId = newIdFor(entry.path)
            when {
                newId == null -> if (isLocalPath(entry.path)) favoriteDao.deleteById(oldId)
                newId != oldId -> favoriteDao.updateId(oldId, newId)
            }
        }
    }

    private class PathSync(
        private val recentDao: RecentDao,
        private val favoriteDao: FavoriteDao,
        private val recentPaths: Map<Long, String>,
        private val favoritePaths: Map<Long, String>
    ) {
        suspend fun update(doc: RecentDocument) {
            if (recentPaths[doc.mediaId]?.let { it != doc.path } == true) {
                recentDao.updatePath(doc.mediaId, doc.path)
            }
            if (favoritePaths[doc.mediaId]?.let { it != doc.path } == true) {
                favoriteDao.updateName(doc.mediaId, doc.path)
            }
        }

        companion object {
            suspend fun load(recentDao: RecentDao, favoriteDao: FavoriteDao) = PathSync(
                recentDao,
                favoriteDao,
                recentDao.getAll().first().associate { it.mediaId to it.path },
                favoriteDao.getAll().first().associate { it.mediaId to it.path }
            )
        }
    }
}

internal fun <T> scanFilesFlow(
    roots: List<File>,
    fileFilter: (File) -> Boolean,
    buildModel: suspend (File) -> T,
): Flow<T> = flow {
    val directories = ArrayDeque<File>()
    val visited = HashSet<String>()
    roots.forEach(directories::addLast)
    while (directories.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val directory = directories.removeFirst()
        val canonicalPath = try {
            directory.canonicalPath
        } catch (_: IOException) {
            continue
        } catch (_: SecurityException) {
            continue
        }
        if (!visited.add(canonicalPath)) continue
        val children = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: continue
        for (file in children) {
            currentCoroutineContext().ensureActive()
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) directories.addLast(file)
            } else if (file.isFile && fileFilter(file)) {
                emit(buildModel(file))
            }
        }
    }
}.flowOn(Dispatchers.IO)

fun formatDateByMillis(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.ENGLISH).format(Date(millis))

fun formatTimeByMillis(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.ENGLISH).format(Date(millis))