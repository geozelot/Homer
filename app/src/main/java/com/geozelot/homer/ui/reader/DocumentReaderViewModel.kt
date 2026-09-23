package com.geozelot.homer.ui.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geozelot.homer.data.library.BookDocumentStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The name the document's path travels under on the reader route. */
const val ARG_DOCUMENT_PATH = "docPath"

/**
 * Getting the file. Everything after that — pages, zoom — is the screen's own business.
 *
 * [Missing] and [Unreadable] are deliberately two states and not one. "It is not on this device and
 * the network is not answering" is something a reader can do something about, later, from a
 * different place; "this file is here and Homer cannot open it" is not, and offering a Retry for it
 * would be a button that can only ever fail.
 */
sealed interface DocumentState {
    data object Loading : DocumentState
    data class Ready(val uri: Uri) : DocumentState
    data object Missing : DocumentState
    data object Unreadable : DocumentState
}

@HiltViewModel
class DocumentReaderViewModel @Inject constructor(
    private val store: BookDocumentStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val path: String = savedStateHandle.get<String>(ARG_DOCUMENT_PATH).orEmpty()

    private val _state = MutableStateFlow<DocumentState>(DocumentState.Loading)
    val state: StateFlow<DocumentState> = _state.asStateFlow()

    init {
        fetch()
    }

    fun fetch() {
        viewModelScope.launch {
            _state.value = DocumentState.Loading
            _state.value = store.obtain(path)
                .fold({ DocumentState.Ready(it) }, { DocumentState.Missing })
        }
    }

    /** Said by the screen once the file is here and [PdfPages] has refused it. */
    fun reportUnreadable() {
        _state.value = DocumentState.Unreadable
    }
}
