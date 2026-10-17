package com.af010.creator.docreader.ui.main.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.af010.creator.docreader.database.DocumentRepository
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.RecentDocument
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.model.SortByData
import com.af010.creator.docreader.model.SortOption
import com.af010.creator.docreader.model.SortOrder
import com.af010.creator.docreader.model.StorageTab
import com.af010.creator.docreader.pdf.ContentIndex
import com.af010.creator.docreader.utils.PreferenceHelper
import com.af010.creator.docreader.utils.extensions.folderName
import com.af010.creator.docreader.utils.extensions.name
import com.af010.creator.docreader.utils.extensions.sortedBy
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.lifecycle.launchMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

typealias DocumentsResponse = DataResponse<List<RecentUi>>

/** What a document list shows: every field narrows [DocumentRepository.documents] further. */
data class DocumentFilter(
    val tab: StorageTab = StorageTab.All,
    val sort: SortOption = SortOption(),
    val type: DocumentType? = null,
    val folder: String? = null,
    val query: String = "",
    val scope: SearchScope = SearchScope.FileName
)

/** Where [DocumentFilter.query] is looked for: file names, the text inside the files, or both. */
enum class SearchScope { All, FileName, Content }

/** Shared by every screen that lists documents; each screen owns its own instance and filter. */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentsViewModel(
    private val repository: DocumentRepository,
    private val preferenceHelper: PreferenceHelper,
    private val contentIndex: ContentIndex
) : ViewModel() {

    val filter: StateFlow<DocumentFilter>
        field = MutableStateFlow(
            DocumentFilter(sort = SortOption(preferenceHelper.sortBy, preferenceHelper.sortOrder))
        )

    private val favoriteIds: StateFlow<Set<Long>> = repository.favoriteIds
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** When each document was last opened in the app, by media id. */
    private val openedAt: StateFlow<Map<Long, Long>> = repository.recents
        .map { list -> list.associate { it.mediaId to it.lastTimeView } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val withImages = MutableStateFlow(false)

    /** [DocumentRepository.documents], plus the device's pictures after [includeImages]. */
    private val source: Flow<DataResponse<List<RecentDocument>>> = withImages.flatMapLatest { images ->
        if (!images) repository.documents
        else combine(repository.documents, repository.images) { documents, pictures ->
            if (documents is DataResponse.DataSuccess && pictures is DataResponse.DataSuccess) {
                DataResponse.DataSuccess(documents.data + pictures.data, documents.ts)
            } else {
                documents
            }
        }
    }

    /** Ids of the documents whose text has the query; it grows while the files are being read. */
    private val contentMatches = MutableStateFlow<Set<Long>>(emptySet())

    init {
        viewModelScope.launch {
            combine(
                filter.map { it.query to it.scope }.distinctUntilChanged(),
                source
            ) { search, response -> search to (response as? DataResponse.DataSuccess)?.data.orEmpty() }
                .collectLatest { (search, documents) ->
                    val (query, scope) = search
                    contentMatches.value = emptySet()
                    if (scope == SearchScope.FileName || query.length < MIN_CONTENT_QUERY) return@collectLatest
                    // Wait for a pause in typing: reading files is slow the first time.
                    delay(CONTENT_SEARCH_DELAY_MS)
                    val found = HashSet<Long>()
                    for (document in documents) {
                        if (contentIndex.contains(document, query)) {
                            found += document.mediaId
                            contentMatches.value = found.toSet()
                        }
                    }
                }
        }
    }

    val documents: StateFlow<DocumentsResponse> =
        combine(source, favoriteIds, openedAt, filter, contentMatches) { response, favorites, opened, filter, matches ->
            when (response) {
                is DataResponse.DataSuccess ->
                    DataResponse.DataSuccess(response.data.applying(filter, favorites, opened, matches), response.ts)
                is DataResponse.DataError -> DataResponse.DataError(response.message)
                is DataResponse.DataLoading -> DataResponse.DataLoading()
                is DataResponse.DataIdle -> DataResponse.DataIdle()
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataResponse.DataIdle())

    /** Number of files per type on the device, ignoring the current filter. */
    val typeCounts: StateFlow<Map<DocumentType, Int>> = repository.documents
        .map { response ->
            (response as? DataResponse.DataSuccess)?.data.orEmpty().groupingBy { it.type }.eachCount()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Folder name to file count, biggest folder first. */
    val folders: StateFlow<List<Pair<String, Int>>> = source
        .map { response ->
            (response as? DataResponse.DataSuccess)?.data.orEmpty()
                .groupingBy { it.folderName }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun List<RecentDocument>.applying(
        filter: DocumentFilter,
        favorites: Set<Long>,
        opened: Map<Long, Long>,
        contentMatches: Set<Long>
    ): List<RecentUi> = asSequence()
        .filter {
            when (filter.tab) {
                StorageTab.Recent -> it.mediaId in opened
                StorageTab.Favorites -> it.mediaId in favorites
                StorageTab.All -> true
            }
        }
        .filter { filter.type == null || it.type == filter.type }
        .filter { filter.folder == null || it.folderName == filter.folder }
        .filter {
            filter.query.isEmpty() || when (filter.scope) {
                SearchScope.FileName -> it.name.contains(filter.query, ignoreCase = true)
                SearchScope.Content -> it.mediaId in contentMatches
                SearchScope.All -> it.name.contains(filter.query, ignoreCase = true) || it.mediaId in contentMatches
            }
        }
        .toList()
        .let { list ->
            // Recent: "by date" is when the file was last opened, newest first by default.
            if (filter.tab == StorageTab.Recent && filter.sort.by in DATE_SORTS) {
                val byOpened = compareBy<RecentDocument> { opened[it.mediaId] ?: 0L }
                list.sortedWith(if (filter.sort.order == SortOrder.Decrease) byOpened.reversed() else byOpened)
            } else list.sortedBy(filter.sort)
        }
        .map { RecentUi(it, it.mediaId in favorites) }

    /** Lists pictures too, for tools that take any file (ZIP). */
    fun includeImages() {
        withImages.value = true
    }

    fun setTab(tab: StorageTab) {
        filter.value = filter.value.copy(tab = tab)
    }

    fun setSort(sort: SortOption) {
        preferenceHelper.sortBy = sort.by
        preferenceHelper.sortOrder = sort.order
        filter.value = filter.value.copy(sort = sort)
    }

    fun setScope(type: DocumentType?, folder: String?) {
        filter.value = filter.value.copy(type = type, folder = folder)
    }

    fun setQuery(query: String) {
        filter.value = filter.value.copy(query = query.trim())
    }

    fun setSearchScope(scope: SearchScope) {
        filter.value = filter.value.copy(scope = scope)
    }

    /** Re-checks the storage permission and reloads the device files. */
    fun refresh() = repository.refresh()

    fun markOpened(doc: RecentDocument) = launchMain {
        repository.addRecent(doc.copy(lastTimeView = System.currentTimeMillis()))
    }

    fun setFavorite(doc: RecentDocument, isFavorite: Boolean) = launchMain {
        repository.setFavorite(doc, isFavorite)
    }

    fun rename(doc: RecentDocument, newName: String, onResult: (Boolean) -> Unit) = launchMain {
        onResult(repository.rename(doc, newName).isSuccess)
    }

    fun copy(doc: RecentDocument, onResult: (Boolean) -> Unit) = launchMain {
        onResult(repository.copy(doc).isSuccess)
    }

    /** [onResult] gets the number of files that could not be deleted. */
    fun delete(docs: List<RecentDocument>, onResult: (Int) -> Unit) = launchMain {
        onResult(docs.count { repository.delete(it).isFailure })
    }

    fun shareUri(doc: RecentDocument) = repository.shareUri(doc)

    private companion object {
        /** Shorter queries match nearly every file. */
        const val MIN_CONTENT_QUERY = 2
        val DATE_SORTS = setOf(SortByData.SortByDate, SortByData.None)
        const val CONTENT_SEARCH_DELAY_MS = 400L
    }
}
