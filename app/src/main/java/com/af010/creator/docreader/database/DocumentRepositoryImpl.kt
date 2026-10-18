package com.af010.creator.docreader.database

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.af010.creator.docreader.R
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.FolderItem
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.utils.AppUtils
import com.d010.pdf.reader.viewmodel.DataResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException

typealias DocsResponse = DataResponse<List<RecentDocument>>

interface DocumentRepository {
    val documents: StateFlow<DocsResponse>
    val images: StateFlow<DocsResponse>
    val recents: Flow<List<RecentDocument>>
    val favoriteIds: Flow<List<Long>>

    fun documentsOf(vararg types: DocumentType): Flow<DocsResponse>
    fun folders(list: List<RecentDocument>): List<FolderItem>
    fun refresh()

    suspend fun addRecent(doc: RecentDocument)

    /**
     * Puts the document at [path] at the top of the recent files, once the device's list has it
     * (a file just written can take a moment to be scanned). Files outside it are skipped.
     */
    fun markOpened(path: String)
    suspend fun removeRecent(doc: RecentDocument)
    suspend fun clearRecents()
    suspend fun setFavorite(doc: RecentDocument, isFavorite: Boolean)

    suspend fun rename(doc: RecentDocument, newName: String): Result<RecentDocument>
    suspend fun delete(doc: RecentDocument): Result<Unit>
    suspend fun copy(doc: RecentDocument): Result<Unit>
    fun shareUri(doc: RecentDocument): Uri
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DocumentRepositoryImpl(
    private val context: Context,
    private val recentDao: RecentDao,
    private val favoriteDao: FavoriteDao,
    private val scope: CoroutineScope
) : DocumentRepository {

    private val prefs = context.getSharedPreferences("document_repo", Context.MODE_PRIVATE)
    private val migrationMutex = Mutex()
    private val permission = MutableStateFlow(AppUtils.hasStoragePermission(context))
    private val refreshSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val documents: StateFlow<DocsResponse> =
        watch(AppUtils.filesUri, SharingStarted.Eagerly) { loadDocuments() }

    override val images: StateFlow<DocsResponse> =
        watch(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, SharingStarted.WhileSubscribed(5_000)) {
            loadImages()
        }

    override val recents: Flow<List<RecentDocument>> = recentDao.getAll()

    override val favoriteIds: Flow<List<Long>> = favoriteDao.favoriteIds()

    override fun documentsOf(vararg types: DocumentType): Flow<DocsResponse> {
        val wanted = types.toSet()
        return documents.map { response ->
            if (response is DataResponse.DataSuccess) {
                DataResponse.DataSuccess(response.data.filter { it.type in wanted }, response.ts)
            } else {
                response
            }
        }
    }

    override fun folders(list: List<RecentDocument>): List<FolderItem> {
        if (list.isEmpty()) return emptyList()
        val all = FolderItem(
            folderName = context.getString(R.string.all_photo),
            previewPath = list.first().path,
            listData = list.toMutableList(),
            itemCount = list.size
        )
        val grouped = list
            .groupBy { File(it.path).parentFile?.name.orEmpty() }
            .map { (folderName, items) ->
                FolderItem(
                    folderName = folderName,
                    previewPath = items.first().path,
                    listData = items.toMutableList(),
                    itemCount = items.size
                )
            }
        return listOf(all) + grouped
    }

    override fun refresh() {
        val granted = AppUtils.hasStoragePermission(context)
        if (permission.value != granted) {
            permission.value = granted
        } else {
            refreshSignal.tryEmit(Unit)
        }
    }

    override suspend fun addRecent(doc: RecentDocument) = recentDao.insert(doc)

    override fun markOpened(path: String) {
        val openedAt = System.currentTimeMillis()
        scope.launch {
            val doc = withTimeoutOrNull(OPENED_LOOKUP_MS) {
                documents.mapNotNull { response ->
                    (response as? DataResponse.DataSuccess)?.data?.firstOrNull { it.path == path }
                }.first()
            } ?: return@launch
            recentDao.insert(doc.copy(lastTimeView = openedAt))
        }
    }

    override suspend fun removeRecent(doc: RecentDocument) = recentDao.delete(doc)

    override suspend fun clearRecents() = recentDao.clearAll()

    override suspend fun setFavorite(doc: RecentDocument, isFavorite: Boolean) {
        favoriteDao.toggleFavorite(doc, isFavorite)
    }

    override suspend fun rename(doc: RecentDocument, newName: String): Result<RecentDocument> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed.contains('/')) {
            return Result.failure(IllegalArgumentException("invalid_name"))
        }
        val ext = File(doc.path).extension
        val finalName =
            if (ext.isNotEmpty() && !trimmed.endsWith(".$ext", ignoreCase = true)) "$trimmed.$ext"
            else trimmed

        val renamed = AppUtils.renameDocument(context, doc, finalName, recentDao, favoriteDao)
            ?: return Result.failure(IOException("rename_failed"))
        refreshSignal.tryEmit(Unit)
        return Result.success(renamed)
    }

    override suspend fun delete(doc: RecentDocument): Result<Unit> {
        val ok = AppUtils.deleteDocument(context, doc, recentDao, favoriteDao)
        if (!ok) return Result.failure(IOException("delete_failed"))
        refreshSignal.tryEmit(Unit)
        return Result.success(Unit)
    }

    override suspend fun copy(doc: RecentDocument): Result<Unit> = withContext(Dispatchers.IO) {
        val source = File(doc.path)
        val parent = source.parentFile
        if (!source.exists() || parent == null) {
            return@withContext Result.failure(IOException("copy_failed"))
        }
        // "name (1).ext", "name (2).ext", ... until a free name is found.
        val suffix = if (source.extension.isEmpty()) "" else ".${source.extension}"
        val target = generateSequence(1) { it + 1 }
            .map { File(parent, "${source.nameWithoutExtension} ($it)$suffix") }
            .first { !it.exists() }
        try {
            source.copyTo(target)
        } catch (e: IOException) {
            return@withContext Result.failure(e)
        }
        AppUtils.notifyMediaScanner(context, target.absolutePath)
        refreshSignal.tryEmit(Unit)
        Result.success(Unit)
    }

    override fun shareUri(doc: RecentDocument): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(doc.path))

    private fun watch(
        uri: Uri,
        started: SharingStarted,
        load: suspend () -> List<RecentDocument>
    ): StateFlow<DocsResponse> =
        permission
            .flatMapLatest<Boolean, DocsResponse> { granted ->
                if (!granted) {
                    flowOf(DataResponse.DataError(PERMISSION_DENIED))
                } else {
                    merge(changesOf(uri), refreshSignal)
                        .onStart { emit(Unit) }
                        .debounce(300L)
                        .mapLatest { safeLoad(load) }
                        .onStart { emit(DataResponse.DataLoading()) }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, started, DataResponse.DataIdle())

    private fun changesOf(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private suspend fun safeLoad(load: suspend () -> List<RecentDocument>): DocsResponse =
        try {
            DataResponse.DataSuccess(load())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DataResponse.DataError(e.message ?: e.javaClass.simpleName)
        }

    private suspend fun loadDocuments(): List<RecentDocument> {
        migrateIfNeeded()
        AppUtils.pruneMissingFiles(recentDao, favoriteDao)
        return AppUtils.scanAllDocumentsFlow(context, recentDao, favoriteDao).toList()
    }

    private suspend fun migrateIfNeeded() = migrationMutex.withLock {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return@withLock
        try {
            AppUtils.migrateLegacyIds(context, recentDao, favoriteDao)
            prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun loadImages(): List<RecentDocument> = AppUtils.queryImages(context)

    companion object {
        const val PERMISSION_DENIED = "permission_denied"
        private const val KEY_MIGRATED = "ids_migrated"
        private const val OPENED_LOOKUP_MS = 15_000L
    }
}