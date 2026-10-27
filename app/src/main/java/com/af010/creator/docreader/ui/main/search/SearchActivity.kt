package com.af010.creator.docreader.ui.main.search

import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySearchBinding
import com.af010.creator.docreader.model.DocumentType
import com.af010.creator.docreader.model.RecentUi
import com.af010.creator.docreader.ui.main.common.FilterChip
import com.af010.creator.docreader.ui.main.common.FilterChipAdapter
import com.af010.creator.docreader.ui.main.common.RowDivider
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.common.show
import com.af010.creator.docreader.ui.main.common.showNoAccess
import com.af010.creator.docreader.ui.main.files.DocumentActions
import com.af010.creator.docreader.ui.main.files.DocumentAdapter
import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.files.RowEnd
import com.af010.creator.docreader.ui.main.files.SearchScope
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Looks for the query in file names and, apart, in the text of PDF and text files. Before
 * anything is typed it offers the recent searches.
 */
class SearchActivity : BaseAppActivity<ActivitySearchBinding>(ActivitySearchBinding::inflate) {

    private val viewModel: DocumentsViewModel by viewModel()
    private val typeChips = FilterChipAdapter<DocumentType?>()
    private val names = DocumentAdapter(RowEnd.Nothing)
    private val inside = ContentHitAdapter { actions.open(it.document) }
    private val recentAdapter = RecentSearchAdapter()
    private val actions by lazy { DocumentActions(this, viewModel) }
    private val permissionRequester = StoragePermissionRequester(this) { viewModel.refresh() }

    private var noAccess = false
    private var nameCount = 0
    private var nameMatches = emptyList<RecentUi>()
    private var showAllNames = false

    private val query: String get() = viewModel.filter.value.query

    override fun initialize() = Unit

    override fun backPressed() = finish()

    override fun ActivitySearchBinding.setData() {
        rcvFilters.adapter = typeChips
        rcvDocuments.adapter = names
        rcvInside.adapter = inside
        rcvRecent.adapter = recentAdapter
        rcvDocuments.addItemDecoration(RowDivider())
        rcvInside.addItemDecoration(RowDivider(com.intuit.sdp.R.dimen._12sdp))
        rcvRecent.addItemDecoration(RowDivider(com.intuit.sdp.R.dimen._34sdp))
        typeChips.submitList(TYPES.map { (type, label) -> FilterChip(type, label, isSelected = type == null) })
        viewModel.setSearchScope(SearchScope.Apart)
        showRecentSearches()

        viewModel.documents.mainCollect { response ->
            noAccess = response.isNoAccess
            val files = (response as? DataResponse.DataSuccess)?.data.orEmpty()
            nameMatches = files
            nameCount = files.size
            tvResultCount.text = resources.getQuantityString(R.plurals.fo_found, files.size, files.size)
            showNames()
            showState()
        }
        viewModel.contentHits.mainCollect { hits ->
            inside.submitList(hits)
            showState()
        }
        edtSearch.requestFocus()
        showKeyboard(edtSearch)
    }

    override fun ActivitySearchBinding.onClick() {
        btnBack.clickSingle { finish() }
        emptyState.btnPrimary.clickSingle { permissionRequester.request(this@SearchActivity) }
        btnClear.click { edtSearch.setText("") }
        btnShowAll.clickSingle {
            showAllNames = true
            showNames()
        }
        edtSearch.afterTextChanged {
            showAllNames = false
            btnClear.isVisible = it.isNotEmpty()
            viewModel.setQuery(it)
        }
        edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                rememberSearch()
                hideKeyboard()
            }
            false
        }
        typeChips.onChipClick { type ->
            typeChips.select(type)
            viewModel.setScope(type, null)
        }
        recentAdapter
            .onQueryClick {
                edtSearch.setText(it)
                edtSearch.setSelection(it.length)
            }
            .onQueryRemove {
                preferenceHelper.recentSearches -= it
                showRecentSearches()
            }
        names.onDocumentClick {
            rememberSearch()
            actions.open(it.document)
        }
    }

    /** A long list of name matches would push the hits inside documents out of sight. */
    private fun showNames() = with(binding) {
        val capped = !showAllNames && nameMatches.size > NAME_LIMIT
        names.query = query
        names.submitList(if (capped) nameMatches.take(NAME_LIMIT) else nameMatches)
        btnShowAll.isVisible = capped
        btnShowAll.text = resources.getQuantityString(R.plurals.fo_show_all_files, nameMatches.size, nameMatches.size)
    }

    private fun showRecentSearches() {
        recentAdapter.submitList(preferenceHelper.recentSearches)
        showState()
    }

    /** Below the chips there is always something: results, recent searches, or a card. */
    private fun showState() = with(binding) {
        val typed = query.isNotEmpty()
        val hits = inside.itemCount
        layoutResults.isVisible = typed && nameCount > 0
        layoutInside.isVisible = typed && hits > 0
        val showRecent = !noAccess && !typed && recentAdapter.isNotEmpty()
        layoutRecent.isVisible = showRecent
        when {
            noAccess -> emptyState.showNoAccess(R.string.search_no_access_desc)
            typed && nameCount == 0 && hits == 0 ->
                emptyState.show(getString(R.string.search_no_results), R.string.search_no_results_desc, icon = R.drawable.ic_fo_search)
            !typed && !showRecent ->
                emptyState.show(getString(R.string.search_start_title), R.string.search_start_desc, icon = R.drawable.ic_fo_search)
            else -> emptyState.root.isVisible = false
        }
    }

    /** Newest first, no duplicates, at most [MAX_RECENT]. */
    private fun rememberSearch() {
        if (query.isEmpty()) return
        preferenceHelper.recentSearches = (listOf(query) + preferenceHelper.recentSearches).distinct().take(MAX_RECENT)
        showRecentSearches()
    }

    private companion object {
        const val MAX_RECENT = 10
        const val NAME_LIMIT = 6
        val TYPES = listOf(
            null to R.string.fo_all,
            DocumentType.Pdf to R.string.tab_pdf,
            DocumentType.Doc to R.string.tab_word,
            DocumentType.Excel to R.string.tab_excel,
            DocumentType.Txt to R.string.fo_text
        )
    }
}
