package hu.gc.jegyzokonyv.ui.editor

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.domain.model.Draft
import hu.gc.jegyzokonyv.domain.usecase.ExportPdfUseCase
import hu.gc.jegyzokonyv.ui.nav.Routes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface EditorEvent {
    data class ExportFinished(val pdf: File) : EditorEvent
    data class ExportFailed(val reason: String?) : EditorEvent
}

@HiltViewModel
class DocumentEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: DraftRepository,
    private val exportPdfUseCase: ExportPdfUseCase,
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

    private val saveMutex = Mutex()
    @Volatile private var lastHtmlFromEditor: String = ""
    @Volatile private var lastSavedHtml: String = ""
    @Volatile private var pendingSaveJob: Job? = null
    private val htmlRevision = AtomicLong(0)
    private val savedRevision = AtomicLong(0)

    init {
        viewModelScope.launch {
            val loaded = draftRepository.loadHtml(draftId)
            _html.value = loaded
            lastHtmlFromEditor = loaded
            lastSavedHtml = loaded
            savedRevision.set(htmlRevision.get())
        }
    }

    fun reloadHtml() {
        viewModelScope.launch {
            flushEditorHtml()
            val loaded = draftRepository.loadHtml(draftId)
            _html.value = loaded
            lastHtmlFromEditor = loaded
            lastSavedHtml = loaded
        }
    }

    fun onTitleChange(newTitle: String) {
        viewModelScope.launch {
            flushEditorHtml()
            draftRepository.setTitle(draftId, newTitle)
            val loaded = draftRepository.loadHtml(draftId)
            _html.value = loaded
            lastHtmlFromEditor = loaded
            lastSavedHtml = loaded
        }
    }

    fun onAddText(text: String) {
        if (text.isBlank()) return
        if (_html.value.contains("safety-walkthrough")) return
        viewModelScope.launch {
            flushEditorHtml()
            draftRepository.appendTextBlock(draftId, text.trim())
            val loaded = draftRepository.loadHtml(draftId)
            _html.value = loaded
            lastHtmlFromEditor = loaded
            lastSavedHtml = loaded
        }
    }

    fun onDocumentHtmlChanged(html: String) {
        if (html.isBlank()) return
        val revision = htmlRevision.incrementAndGet()
        lastHtmlFromEditor = html
        pendingSaveJob = viewModelScope.launch {
            saveMutex.withLock {
                if (revision < htmlRevision.get()) return@withLock
                if (html != lastSavedHtml || revision > savedRevision.get()) {
                    draftRepository.saveHtml(draftId, html)
                    lastSavedHtml = html
                    savedRevision.set(revision)
                }
            }
        }
    }

    fun onPhotoCaptured(relativePath: String, caption: String?) {
        viewModelScope.launch {
            flushEditorHtml()
            draftRepository.appendPhotoBlock(draftId, relativePath, caption?.takeIf { it.isNotBlank() })
            val loaded = draftRepository.loadHtml(draftId)
            _html.value = loaded
            lastHtmlFromEditor = loaded
            lastSavedHtml = loaded
        }
    }

    fun onExportPdf() {
        if (_isExporting.value) return
        _isExporting.value = true
        viewModelScope.launch {
            flushEditorHtml()
            val result = runCatching { exportPdfUseCase(draftId) }
            _isExporting.value = false
            result
                .onSuccess { pdf ->
                    _events.send(EditorEvent.ExportFinished(pdf))
                }
                .onFailure { error ->
                    Log.e(TAG, "PDF export failed", error)
                    val reason = error.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName
                    _events.send(EditorEvent.ExportFailed(reason))
                }
        }
    }


    fun onDelete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            draftRepository.deleteDraft(draftId)
            onDeleted()
        }
    }

    fun draftFolder(): File = draftRepository.draftDir(draftId)

    private suspend fun flushEditorHtml() {
        pendingSaveJob?.join()
        val latest = lastHtmlFromEditor
        if (latest.isBlank()) return
        saveMutex.withLock {
            val revision = htmlRevision.get()
            if (latest != lastSavedHtml || revision > savedRevision.get()) {
                draftRepository.saveHtml(draftId, latest)
                lastSavedHtml = latest
                savedRevision.set(revision)
            }
        }
    }

    override fun onCleared() {
        // ViewModel scopes are cancelled during clear; persist the latest editor snapshot first.
        val latest = lastHtmlFromEditor
        if (latest.isNotBlank() && (latest != lastSavedHtml || htmlRevision.get() > savedRevision.get())) {
            runCatching { kotlinx.coroutines.runBlocking { draftRepository.saveHtml(draftId, latest) } }
        }
        super.onCleared()
    }

    private companion object {
        const val TAG = "ExportPdf"
    }
}
