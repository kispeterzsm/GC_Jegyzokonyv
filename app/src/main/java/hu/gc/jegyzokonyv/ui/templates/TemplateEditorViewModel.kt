package hu.gc.jegyzokonyv.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.html.HtmlEngine
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import hu.gc.jegyzokonyv.domain.model.TemplateKind
import hu.gc.jegyzokonyv.domain.usecase.SaveTemplateUseCase
import hu.gc.jegyzokonyv.ui.nav.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: TemplateRepository,
    private val saveTemplate: SaveTemplateUseCase,
    private val htmlEngine: HtmlEngine,
    private val profileRepository: ProfileRepository,
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
                        kind = content.kind,
                        blocks = ensureImagesBlock(content.blocks),
                        isReadOnly = template?.isBuiltIn == true,
                    )
                }
            } else {
                val starter = templateRepository.starterContent()
                _state.update {
                    it.copy(
                        isLoading = false,
                        title = starter.title,
                        kind = starter.kind,
                        blocks = ensureImagesBlock(starter.blocks),
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

    fun addTableBlock(rows: Int, columns: Int, hasHeaderColumn: Boolean) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(
                blocks = s.blocks + TemplateBlock.Table(
                    id = UUID.randomUUID().toString(),
                    rows = rows.coerceIn(1, 50),
                    columns = columns.coerceIn(1, 20),
                    hasHeaderColumn = hasHeaderColumn,
                    cells = List(rows.coerceIn(1, 50)) { List(columns.coerceIn(1, 20)) { "" } },
                )
            )
        }
    }

    fun addSignatureBlock() {
        if (_state.value.isReadOnly) return
        _state.update { s -> s.copy(blocks = s.blocks + TemplateBlock.Signature(id = UUID.randomUUID().toString())) }
    }

    fun addStampBlock() {
        if (_state.value.isReadOnly) return
        _state.update { s -> s.copy(blocks = s.blocks + TemplateBlock.Stamp(id = UUID.randomUUID().toString())) }
    }

    fun addPageBreakBlock() {
        if (_state.value.isReadOnly) return
        _state.update { s -> s.copy(blocks = s.blocks + TemplateBlock.PageBreak(id = UUID.randomUUID().toString())) }
    }

    fun removeBlock(id: String) {
        if (_state.value.isReadOnly) return
        _state.update { s -> s.copy(blocks = s.blocks.filterNot { it.id == id && it !is TemplateBlock.Images }) }
    }

    fun moveBlockUp(id: String) = moveBlock(id, offset = -1)
    fun moveBlockDown(id: String) = moveBlock(id, offset = 1)

    private fun ensureImagesBlock(blocks: List<TemplateBlock>): List<TemplateBlock> =
        if (blocks.any { it is TemplateBlock.Images }) blocks else blocks + TemplateBlock.Images(id = IMAGES_BLOCK_ID)

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

    fun previewHtml(todayIso: String): String {
        val current = _state.value
        val content = TemplateContent(title = current.title, kind = current.kind, blocks = current.blocks)
        val title = current.title.ifBlank { current.name.ifBlank { "Jegyzőkönyv" } }
        return htmlEngine.renderTemplate(content, title, todayIso, profileRepository.profile.value)
    }

    fun onPreviewHtmlChanged(html: String) {
        if (_state.value.isReadOnly || html.isBlank()) return
        val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return
        val tables = doc.select("table.editable-table[data-template-block-id]").associate { table ->
            table.attr("data-template-block-id") to table.select("tr").map { row ->
                row.select("th,td").map { it.wholeText().trim() }
            }
        }
        if (tables.isEmpty()) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { block ->
                if (block is TemplateBlock.Table && tables.containsKey(block.id)) {
                    block.copy(cells = tables.getValue(block.id))
                } else {
                    block
                }
            })
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
                    kind = current.kind,
                    blocks = current.blocks,
                ),
                fallbackName = fallbackName,
            )
            _state.update { it.copy(isSaving = false) }
            onSaved(id)
        }
    }
    private companion object {
        const val IMAGES_BLOCK_ID = "template-images"
    }
}

data class TemplateEditorState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isReadOnly: Boolean = false,
    val name: String = "",
    val title: String = "",
    val kind: TemplateKind = TemplateKind.Standard,
    val blocks: List<TemplateBlock> = emptyList(),
)
