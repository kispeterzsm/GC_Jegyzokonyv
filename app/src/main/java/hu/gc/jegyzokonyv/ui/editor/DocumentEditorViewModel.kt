package hu.gc.jegyzokonyv.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import android.content.Intent
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.domain.model.Draft
import hu.gc.jegyzokonyv.domain.usecase.ExportPdfUseCase
import hu.gc.jegyzokonyv.domain.usecase.SharePdfUseCase
import hu.gc.jegyzokonyv.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface EditorEvent {
    data class ExportFinished(val pdf: File) : EditorEvent
    data object ExportFailed : EditorEvent
    data object NoPdfAvailable : EditorEvent
    data class LaunchShare(val intent: Intent) : EditorEvent
}

@HiltViewModel
class DocumentEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: DraftRepository,
    private val exportPdfUseCase: ExportPdfUseCase,
    private val sharePdfUseCase: SharePdfUseCase,
) : ViewModel() {

    private val draftId: String = savedStateHandle.get<String>(Routes.ARG_DRAFT_ID).orEmpty()

    val draft: StateFlow<Draft?> = draftRepository.observeDraft(draftId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _html = MutableStateFlow("")
    val html: StateFlow<String> = _html.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            _html.value = draftRepository.loadHtml(draftId)
        }
    }

    fun reloadHtml() {
        viewModelScope.launch { _html.value = draftRepository.loadHtml(draftId) }
    }

    fun onTitleChange(newTitle: String) {
        viewModelScope.launch {
            draftRepository.setTitle(draftId, newTitle)
            _html.value = draftRepository.loadHtml(draftId)
        }
    }

    fun onAddText(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            draftRepository.appendTextBlock(draftId, text.trim())
            _html.value = draftRepository.loadHtml(draftId)
        }
    }

    fun onPhotoCaptured(relativePath: String, caption: String?) {
        viewModelScope.launch {
            draftRepository.appendPhotoBlock(draftId, relativePath, caption?.takeIf { it.isNotBlank() })
            _html.value = draftRepository.loadHtml(draftId)
        }
    }

    fun onExportPdf() {
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch {
            val result = runCatching { exportPdfUseCase(draftId) }
            _isExporting.value = false
            result
                .onSuccess { _events.send(EditorEvent.ExportFinished(it)) }
                .onFailure { _events.send(EditorEvent.ExportFailed) }
        }
    }

    fun onShareLastPdf(chooserTitle: String) {
        viewModelScope.launch {
            val pdf = draftRepository.exportPdfFile(draftId)
            if (pdf.exists()) {
                _events.send(EditorEvent.LaunchShare(sharePdfUseCase(pdf, chooserTitle)))
            } else {
                _events.send(EditorEvent.NoPdfAvailable)
            }
        }
    }

    fun shareAfterExport(pdf: File, chooserTitle: String): Intent =
        sharePdfUseCase(pdf, chooserTitle)

    fun onDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            draftRepository.deleteDraft(draftId)
            onDeleted()
        }
    }

    fun draftFolder(): File = draftRepository.draftDir(draftId)
}
