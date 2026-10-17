package com.af010.creator.docreader.ui.main.search

import android.view.inputmethod.EditorInfo
import androidx.core.view.updatePadding
import com.af010.creator.docreader.R
import com.af010.creator.docreader.base.BaseAppActivity
import com.af010.creator.docreader.databinding.ActivitySearchBinding
import com.af010.creator.docreader.ui.main.common.FilterChip
import com.af010.creator.docreader.ui.main.common.FilterChipAdapter
import com.af010.creator.docreader.ui.main.common.isNoAccess
import com.af010.creator.docreader.ui.main.common.show
import com.af010.creator.docreader.ui.main.common.showNoAccess
import com.af010.creator.docreader.ui.main.files.DocumentActions
import com.af010.creator.docreader.ui.main.files.DocumentAdapter
import com.af010.creator.docreader.ui.main.files.DocumentsViewModel
import com.af010.creator.docreader.ui.main.files.SearchScope
import com.af010.creator.docreader.utils.StoragePermissionRequester
import com.d010.pdf.reader.viewmodel.DataResponse
import com.ui.baselib.extensions.afterTextChanged
import com.ui.baselib.extensions.click
import com.ui.baselib.extensions.clickSingle
import com.ui.baselib.extensions.gone
import com.ui.baselib.extensions.visibleOrGone
import org.koin.androidx.viewmodel.ext.android.viewModel

class SearchActivity : BaseAppActivity<ActivitySearchBinding>(ActivitySearchBinding::inflate) {
    override val fullStatus: Boolean
        get() = true

    private val viewModel: DocumentsViewModel by viewModel()
    private val scopeAdapter by lazy { FilterChipAdapter<SearchScope>() }
    private val documentAdapter by lazy { DocumentAdapter() }
    private val recentAdapter by lazy { RecentSearchAdapter() }
    private val documentActions by lazy { DocumentActions(this, viewModel) }

    private var noAccess = false
    private var noResults = false

    private val permissionRequester = StoragePermissionRequester(this) {
        viewModel.refresh()
    }

    private val query: String
        get() = viewModel.filter.value.query

    override fun backPressed() {
        finish()
    }

    override fun initialize() {

    }

    override fun ActivitySearchBinding.setData() {
        layoutTop.updatePadding(top = statusBarHeight)
        rcvFilters.adapter = scopeAdapter
        rcvDocuments.adapter = documentAdapter
        rcvRecent.adapter = recentAdapter

        scopeAdapter.submitList(
            listOf(
                FilterChip(SearchScope.All, R.string.filter_all, isSelected = true),
                FilterChip(SearchScope.FileName, R.string.filter_file_name),
                FilterChip(SearchScope.Content, R.string.filter_content)
            )
        )
        viewModel.setSearchScope(SearchScope.All)
        showRecentSearches()

        viewModel.documents.mainCollect { response ->
            noAccess = response.isNoAccess
            val documents = (response as? DataResponse.DataSuccess)?.data
            if (documents != null) {
                documentAdapter.query = query
                documentAdapter.submitList(documents)
                tvResultCount.text = resources.getQuantityString(
                    R.plurals.result_count, documents.size, documents.size
                )
            }
            layoutResults.visibleOrGone(query.isNotEmpty() && !documents.isNullOrEmpty())
            noResults = query.isNotEmpty() && documents?.isEmpty() == true
            renderEmptyState()
        }
        edtSearch.requestFocus()
        showKeyboard(edtSearch)
    }

    override fun ActivitySearchBinding.onClick() {
        btnBack.clickSingle { finish() }
        emptyState.btnPrimary.clickSingle { permissionRequester.request(this@SearchActivity) }
        btnClear.click { edtSearch.setText("") }
        edtSearch.afterTextChanged {
            btnClear.visibleOrGone(it.isNotEmpty())
            viewModel.setQuery(it)
        }
        edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                rememberSearch()
                hideKeyboard()
            }
            false
        }

        scopeAdapter.onChipClick { scope ->
            scopeAdapter.select(scope)
            viewModel.setSearchScope(scope)
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
        documentAdapter
            .onDocumentClick {
                rememberSearch()
                documentActions.open(it.document)
            }
            .onMoreClick { documentActions.showOptions(it) }
    }

    private fun ActivitySearchBinding.showRecentSearches() {
        recentAdapter.submitList(preferenceHelper.recentSearches)
        renderEmptyState()
    }

    /** Below the chips there is always something: results, recent searches, or this card. */
    private fun ActivitySearchBinding.renderEmptyState() {
        val showRecent = !noAccess && query.isEmpty() && recentAdapter.isNotEmpty()
        layoutRecent.visibleOrGone(showRecent)
        when {
            noAccess -> emptyState.showNoAccess(R.string.search_no_access_desc)
            noResults -> emptyState.show(
                getString(R.string.search_no_results),
                R.string.search_no_results_desc,
                icon = R.drawable.ic_app_search
            )
            query.isEmpty() && !showRecent -> emptyState.show(
                getString(R.string.search_start_title),
                R.string.search_start_desc,
                icon = R.drawable.ic_app_search
            )
            else -> emptyState.root.gone()
        }
    }

    /** Newest first, no duplicates, at most [MAX_RECENT]. */
    private fun rememberSearch() {
        if (query.isEmpty()) return
        preferenceHelper.recentSearches =
            (listOf(query) + preferenceHelper.recentSearches).distinct().take(MAX_RECENT)
        binding.showRecentSearches()
    }

    companion object {
        private const val MAX_RECENT = 10
    }
}
