package com.metrolist.music.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.utils.reportException
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.BrowseResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val browseId: String? = savedStateHandle.get<String>("browseId")

    private val _title = MutableStateFlow<String?>(null)
    val title = _title.asStateFlow()

    private val _items = MutableStateFlow<List<YTItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore = _canLoadMore.asStateFlow()

    private var continuation: String? = null

    init {
        loadInitial()
    }

    fun loadInitial() {
        if (_isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            browseId?.let { id ->
                val result = if (id == "FEmusic_charts") {
                    YouTube.loadCharts()
                } else {
                    YouTube.browse(id, null)
                }

                result.onSuccess { response ->
                    _title.value = response.title
                    _items.value = response.items.flatMap { it.items }
                    continuation = response.items.lastOrNull()?.continuation
                    _canLoadMore.value = continuation != null
                }.onFailure {
                    reportException(it)
                }
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoading.value || continuation == null) return

        viewModelScope.launch {
            _isLoading.value = true
            val result = if (browseId == "FEmusic_charts") {
                YouTube.loadCharts(continuation)
            } else {
                YouTube.playlistContinuation(continuation!!)
            }

            result.onSuccess { response ->
                _items.value = _items.value + response.items
                continuation = response.continuation
                _canLoadMore.value = continuation != null
            }.onFailure {
                reportException(it)
            }
            _isLoading.value = false
        }
    }

    fun retry() {
        if (continuation == null) loadInitial() else loadMore()
    }
}
