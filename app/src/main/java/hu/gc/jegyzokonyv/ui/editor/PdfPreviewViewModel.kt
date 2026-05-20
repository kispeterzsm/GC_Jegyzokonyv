package hu.gc.jegyzokonyv.ui.editor

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.domain.usecase.SharePdfUseCase
import hu.gc.jegyzokonyv.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface PdfPreviewEvent {
    data class LaunchShare(val intent: Intent) : PdfPreviewEvent
}

@HiltViewModel
class PdfPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: DraftRepository,
    private val sharePdfUseCase: SharePdfUseCase,
) : ViewModel() {
    private val draftId: String = savedStateHandle.get<String>(Routes.ARG_DRAFT_ID).orEmpty()

    private val _pdf = MutableStateFlow<File?>(null)
    val pdf: StateFlow<File?> = _pdf.asStateFlow()

    private val _events = Channel<PdfPreviewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        _pdf.value = draftRepository.latestExportedPdf(draftId)?.takeIf { it.exists() }
    }

    fun onShare(chooserTitle: String) {
        val file = _pdf.value ?: return
        viewModelScope.launch {
            _events.send(PdfPreviewEvent.LaunchShare(sharePdfUseCase(file, chooserTitle)))
        }
    }
}
