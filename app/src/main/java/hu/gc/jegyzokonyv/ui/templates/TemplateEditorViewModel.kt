package hu.gc.jegyzokonyv.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import hu.gc.jegyzokonyv.domain.usecase.SaveTemplateUseCase
import hu.gc.jegyzokonyv.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: TemplateRepository,
    private val saveTemplate: SaveTemplateUseCase,
) : ViewModel() {

    private val templateId: String? = savedStateHandle.get<String>(Routes.ARG_TEMPLATE_ID)

    private val _state = MutableStateFlow(
        TemplateEditorState(isEdit = templateId != null, isLoading = true)
    )
    val state: StateFlow<TemplateEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existingId = templateId
            if (existingId != null) {
                val template = templateRepository.getTemplate(existingId)
                val content = templateRepository.loadContent(existingId)
                    ?: TemplateContent(title = template?.title.orEmpty(), blocks = emptyList())
                _state.update {
                    it.copy(
                        isLoading = false,
                        name = template?.name.orEmpty(),
                        title = content.title,
                        blocks = content.blocks,
                        isReadOnly = template?.isBuiltIn == true,
                    )
                }
            } else {
                val starter = templateRepository.starterContent()
                _state.update {
                    it.copy(
                        isLoading = false,
                        title = starter.title,
                        blocks = starter.blocks,
                    )
                }
            }
        }
    }

    fun onNameChange(value: String) {
        if (_state.value.isReadOnly) return
        _state.update { it.copy(name = value) }
    }

    fun onTitleChange(value: String) {
        if (_state.value.isReadOnly) return
        _state.update { it.copy(title = value) }
    }

    fun onTextBlockChange(id: String, text: String) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { b ->
                if (b is TemplateBlock.Text && b.id == id) b.copy(text = text) else b
            })
        }
    }

    fun addTextBlock() {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks + TemplateBlock.Text(id = UUID.randomUUID().toString(), text = ""))
        }
    }

    fun addDateBlock() {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks + TemplateBlock.Date(id = UUID.randomUUID().toString()))
        }
    }

    fun removeBlock(id: String) {
        if (_state.value.isReadOnly) return
        _state.update { s -> s.copy(blocks = s.blocks.filterNot { it.id == id }) }
    }

    fun moveBlockUp(id: String) = moveBlock(id, offset = -1)
    fun moveBlockDown(id: String) = moveBlock(id, offset = 1)

    private fun moveBlock(id: String, offset: Int) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            val list = s.blocks.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            val target = index + offset
            if (index < 0 || target < 0 || target >= list.size) return@update s
            val item = list.removeAt(index)
            list.add(target, item)
            s.copy(blocks = list)
        }
    }

    fun save(fallbackName: String, onSaved: (String) -> Unit) {
        val current = _state.value
        if (current.isReadOnly || current.isSaving) return
        if (current.name.isBlank() && fallbackName.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val id = saveTemplate(
                existingId = templateId,
                name = current.name,
                content = TemplateContent(
                    title = current.title,
                    blocks = current.blocks,
                ),
                fallbackName = fallbackName,
            )
            _state.update { it.copy(isSaving = false) }
            onSaved(id)
        }
    }
}

data class TemplateEditorState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isReadOnly: Boolean = false,
    val name: String = "",
    val title: String = "",
    val blocks: List<TemplateBlock> = emptyList(),
)
