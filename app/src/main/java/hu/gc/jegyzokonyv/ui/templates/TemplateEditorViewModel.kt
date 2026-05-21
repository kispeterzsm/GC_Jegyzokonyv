package hu.gc.jegyzokonyv.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.html.HtmlEngine
import hu.gc.jegyzokonyv.domain.model.ProfileDataField
import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
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
                        title = if (template?.isBuiltIn == true) content.title else "",
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

    fun onTextBlockSettingsChange(id: String, settings: TableCellSettings) = updateStyledBlock(id, settings)

    fun onNestedTextBlockChange(containerId: String, blockId: String, text: String) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { container ->
                when (container) {
                    is TemplateBlock.Header -> if (container.id == containerId) container.copy(blocks = container.blocks.map { if (it is TemplateBlock.Text && it.id == blockId) it.copy(text = text) else it }) else container
                    is TemplateBlock.Footer -> if (container.id == containerId) container.copy(blocks = container.blocks.map { if (it is TemplateBlock.Text && it.id == blockId) it.copy(text = text) else it }) else container
                    is TemplateBlock.Images -> if (container.id == containerId) container.copy(blocks = container.blocks.map { if (it is TemplateBlock.Text && it.id == blockId) it.copy(text = text) else it }) else container
                    else -> container
                }
            })
        }
    }

    fun onNestedTextBlockSettingsChange(containerId: String, blockId: String, settings: TableCellSettings) = updateNestedStyledBlock(containerId, blockId, settings)

    fun onDateBlockSettingsChange(id: String, settings: TableCellSettings) = updateStyledBlock(id, settings)
    fun onNestedDateBlockSettingsChange(containerId: String, blockId: String, settings: TableCellSettings) = updateNestedStyledBlock(containerId, blockId, settings)
    fun onPageNumberBlockSettingsChange(id: String, settings: TableCellSettings) = updateStyledBlock(id, settings)
    fun onNestedPageNumberBlockSettingsChange(containerId: String, blockId: String, settings: TableCellSettings) = updateNestedStyledBlock(containerId, blockId, settings)

    fun onHtmlBlockChange(id: String, html: String) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { b ->
                if (b is TemplateBlock.Html && b.id == id) b.copy(html = html) else b
            })
        }
    }

    fun onTableCellTextChange(id: String, row: Int, column: Int, text: String) = updateTable(id) { table ->
        table.copy(cells = table.normalizedCells().mapIndexed { rowIndex, values ->
            if (rowIndex == row) values.mapIndexed { columnIndex, value -> if (columnIndex == column) text else value } else values
        })
    }

    fun onTableRowSettingsChange(id: String, row: Int, settings: TableAxisSettings) = updateTable(id) { table ->
        table.copy(rowSettings = table.normalizedRowSettings().mapIndexed { index, value -> if (index == row) settings else value })
    }

    fun onTableColumnSettingsChange(id: String, column: Int, settings: TableAxisSettings) = updateTable(id) { table ->
        table.copy(columnSettings = table.normalizedColumnSettings().mapIndexed { index, value -> if (index == column) settings else value })
    }

    fun onTableCellSettingsChange(id: String, row: Int, column: Int, settings: TableCellSettings) = updateTable(id) { table ->
        table.copy(cellSettings = table.normalizedCellSettings().mapIndexed { rowIndex, values ->
            if (rowIndex == row) values.mapIndexed { columnIndex, value -> if (columnIndex == column) settings else value } else values
        })
    }

    fun insertTableRowBelow(id: String, row: Int) = updateTable(id) { table ->
        if (table.rows >= 50) return@updateTable table
        val insertAt = (row + 1).coerceIn(0, table.rows.coerceIn(1, 50))
        val columns = table.columns.coerceIn(1, 20)
        table.copy(
            rows = table.rows + 1,
            cells = table.normalizedCells().toMutableList().apply { add(insertAt, List(columns) { "" }) },
            rowSettings = table.normalizedRowSettings().toMutableList().apply { add(insertAt, TableAxisSettings()) },
            cellSettings = table.normalizedCellSettings().toMutableList().apply { add(insertAt, List(columns) { TableCellSettings() }) },
        )
    }

    fun insertTableColumnRight(id: String, column: Int) = updateTable(id) { table ->
        if (table.columns >= 20) return@updateTable table
        val rows = table.rows.coerceIn(1, 50)
        val insertAt = (column + 1).coerceIn(0, table.columns.coerceIn(1, 20))
        table.copy(
            columns = table.columns + 1,
            cells = table.normalizedCells().map { row -> row.toMutableList().apply { add(insertAt, "") } },
            columnSettings = table.normalizedColumnSettings().toMutableList().apply { add(insertAt, TableAxisSettings()) },
            cellSettings = table.normalizedCellSettings().map { row -> row.toMutableList().apply { add(insertAt, TableCellSettings()) } }.take(rows),
        )
    }

    fun deleteTableRow(id: String, row: Int) = updateTable(id) { table ->
        if (table.rows <= 1) return@updateTable table
        val removeAt = row.coerceIn(0, table.rows - 1)
        table.copy(
            rows = table.rows - 1,
            cells = table.normalizedCells().filterIndexed { index, _ -> index != removeAt },
            rowSettings = table.normalizedRowSettings().filterIndexed { index, _ -> index != removeAt },
            cellSettings = table.normalizedCellSettings().filterIndexed { index, _ -> index != removeAt },
        )
    }

    fun deleteTableColumn(id: String, column: Int) = updateTable(id) { table ->
        deleteTableColumnTransform(table, column)
    }

    fun onNestedTableCellTextChange(containerId: String, tableId: String, row: Int, column: Int, text: String) = updateNestedTable(containerId, tableId) { table ->
        table.copy(cells = table.normalizedCells().mapIndexed { rowIndex, values ->
            if (rowIndex == row) values.mapIndexed { columnIndex, value -> if (columnIndex == column) text else value } else values
        })
    }

    fun onNestedTableRowSettingsChange(containerId: String, tableId: String, row: Int, settings: TableAxisSettings) = updateNestedTable(containerId, tableId) { table ->
        table.copy(rowSettings = table.normalizedRowSettings().mapIndexed { index, value -> if (index == row) settings else value })
    }

    fun onNestedTableColumnSettingsChange(containerId: String, tableId: String, column: Int, settings: TableAxisSettings) = updateNestedTable(containerId, tableId) { table ->
        table.copy(columnSettings = table.normalizedColumnSettings().mapIndexed { index, value -> if (index == column) settings else value })
    }

    fun onNestedTableCellSettingsChange(containerId: String, tableId: String, row: Int, column: Int, settings: TableCellSettings) = updateNestedTable(containerId, tableId) { table ->
        table.copy(cellSettings = table.normalizedCellSettings().mapIndexed { rowIndex, values ->
            if (rowIndex == row) values.mapIndexed { columnIndex, value -> if (columnIndex == column) settings else value } else values
        })
    }

    fun insertNestedTableRowBelow(containerId: String, tableId: String, row: Int) = updateNestedTable(containerId, tableId) { table -> insertTableRowBelowTransform(table, row) }
    fun insertNestedTableColumnRight(containerId: String, tableId: String, column: Int) = updateNestedTable(containerId, tableId) { table -> insertTableColumnRightTransform(table, column) }
    fun deleteNestedTableRow(containerId: String, tableId: String, row: Int) = updateNestedTable(containerId, tableId) { table -> deleteTableRowTransform(table, row) }
    fun deleteNestedTableColumn(containerId: String, tableId: String, column: Int) = updateNestedTable(containerId, tableId) { table -> deleteTableColumnTransform(table, column) }

    fun addTextBlock() {
        if (_state.value.isReadOnly) return
        addBlockAboveImages(TemplateBlock.Text(id = UUID.randomUUID().toString(), text = ""))
    }

    fun addDateBlock() {
        if (_state.value.isReadOnly) return
        addBlockAboveImages(TemplateBlock.Date(id = UUID.randomUUID().toString()))
    }

    fun addTableBlock(rows: Int, columns: Int, hasHeaderColumn: Boolean) {
        if (_state.value.isReadOnly) return
        addBlockAboveImages(
            TemplateBlock.Table(
                id = UUID.randomUUID().toString(),
                rows = rows.coerceIn(1, 50),
                columns = columns.coerceIn(1, 20),
                hasHeaderColumn = hasHeaderColumn,
                cells = List(rows.coerceIn(1, 50)) { List(columns.coerceIn(1, 20)) { "" } },
            )
        )
    }

    fun addSignatureBlock() {
        if (_state.value.isReadOnly) return
        addBlockBelowImages(TemplateBlock.Signature(id = UUID.randomUUID().toString()))
    }

    fun addStampBlock() {
        if (_state.value.isReadOnly) return
        addBlockBelowImages(TemplateBlock.Stamp(id = UUID.randomUUID().toString()))
    }

    fun addProfileDataBlock(field: ProfileDataField) {
        if (_state.value.isReadOnly) return
        addBlockAboveImages(TemplateBlock.ProfileData(id = UUID.randomUUID().toString(), field = field))
    }

    fun addPageBreakBlock() {
        if (_state.value.isReadOnly) return
        addBlockAboveImages(TemplateBlock.PageBreak(id = UUID.randomUUID().toString()))
    }

    fun addHtmlBlock(html: String = DEFAULT_HTML_BLOCK) {
        if (_state.value.isReadOnly) return
        addBlockAboveImages(TemplateBlock.Html(id = UUID.randomUUID().toString(), html = html))
    }

    fun addHeaderBlock() {
        if (_state.value.isReadOnly) return
        _state.update { s -> if (s.blocks.any { it is TemplateBlock.Header }) s else s.copy(blocks = normalizeLockedBlocks(s.blocks + TemplateBlock.Header(id = UUID.randomUUID().toString()))) }
    }

    fun addFooterBlock() {
        if (_state.value.isReadOnly) return
        _state.update { s -> if (s.blocks.any { it is TemplateBlock.Footer }) s else s.copy(blocks = normalizeLockedBlocks(s.blocks + TemplateBlock.Footer(id = UUID.randomUUID().toString()))) }
    }

    fun addPageNumberToHeader() = addBlockToContainer(header = true, TemplateBlock.PageNumber(id = UUID.randomUUID().toString()))
    fun addPageNumberToFooter() = addBlockToContainer(header = false, TemplateBlock.PageNumber(id = UUID.randomUUID().toString()))
    fun addHeaderTextBlock() = addBlockToContainer(header = true, TemplateBlock.Text(id = UUID.randomUUID().toString(), text = ""))
    fun addFooterTextBlock() = addBlockToContainer(header = false, TemplateBlock.Text(id = UUID.randomUUID().toString(), text = ""))
    fun addHeaderTableBlock(rows: Int, columns: Int, hasHeaderColumn: Boolean) = addBlockToContainer(header = true, newTableBlock(rows, columns, hasHeaderColumn))
    fun addFooterTableBlock(rows: Int, columns: Int, hasHeaderColumn: Boolean) = addBlockToContainer(header = false, newTableBlock(rows, columns, hasHeaderColumn))
    fun addImageTextBlock() = addBlockToImages(TemplateBlock.Text(id = UUID.randomUUID().toString(), text = ""))
    fun addImageDateBlock() = addBlockToImages(TemplateBlock.Date(id = UUID.randomUUID().toString()))
    fun addImageTableBlock(rows: Int, columns: Int, hasHeaderColumn: Boolean) = addBlockToImages(newTableBlock(rows, columns, hasHeaderColumn))
    fun addImagePageNumberBlock() = addBlockToImages(TemplateBlock.PageNumber(id = UUID.randomUUID().toString()))

    fun addCheckTableBlock(rows: Int, columns: Int, tickColumnFirst: Boolean) {
        if (_state.value.isReadOnly) return
        val safeRows = rows.coerceIn(1, 50)
        val safeColumns = columns.coerceIn(1, 20)
        val tickIndex = if (tickColumnFirst) 0 else safeColumns - 1
        addBlockAboveImages(
            TemplateBlock.Table(
                id = UUID.randomUUID().toString(),
                rows = safeRows,
                columns = safeColumns,
                hasHeaderColumn = false,
                cells = List(safeRows) { List(safeColumns) { column -> if (column == tickIndex) "X" else "" } },
                cellSettings = List(safeRows) { List(safeColumns) { column -> if (column == tickIndex) TableCellSettings(editable = false, toggleCheck = true) else TableCellSettings() } },
            )
        )
    }

    fun removeBlock(id: String) {
        if (_state.value.isReadOnly) return
        _state.update { s -> s.copy(blocks = normalizeLockedBlocks(s.blocks.filterNot { it.id == id && it !is TemplateBlock.Images })) }
    }

    fun removeNestedBlock(containerId: String, blockId: String) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { container ->
                when (container) {
                    is TemplateBlock.Header -> if (container.id == containerId) container.copy(blocks = container.blocks.filterNot { it.id == blockId }) else container
                    is TemplateBlock.Footer -> if (container.id == containerId) container.copy(blocks = container.blocks.filterNot { it.id == blockId }) else container
                    is TemplateBlock.Images -> if (container.id == containerId) container.copy(blocks = container.blocks.filterNot { it.id == blockId && it !is TemplateBlock.Image }) else container
                    else -> container
                }
            })
        }
    }

    fun moveNestedBlockUp(containerId: String, blockId: String) = moveNestedBlock(containerId, blockId, offset = -1)
    fun moveNestedBlockDown(containerId: String, blockId: String) = moveNestedBlock(containerId, blockId, offset = 1)

    fun moveBlockUp(id: String) = moveBlock(id, offset = -1)
    fun moveBlockDown(id: String) = moveBlock(id, offset = 1)

    private fun ensureImagesBlock(blocks: List<TemplateBlock>): List<TemplateBlock> =
        normalizeLockedBlocks(if (blocks.any { it is TemplateBlock.Images }) blocks else blocks + TemplateBlock.Images(id = IMAGES_BLOCK_ID))

    private fun addBlockAboveImages(block: TemplateBlock) {
        _state.update { s ->
            val contentBlocks = s.blocks.filterNot { it is TemplateBlock.Header || it is TemplateBlock.Footer }.toMutableList()
            val index = contentBlocks.indexOfFirst { it is TemplateBlock.Images }
            if (index < 0) contentBlocks.add(block) else contentBlocks.add(index, block)
            s.copy(blocks = normalizeLockedBlocks(s.blocks.filter { it is TemplateBlock.Header || it is TemplateBlock.Footer } + contentBlocks))
        }
    }

    private fun addBlockBelowImages(block: TemplateBlock) {
        _state.update { s ->
            val contentBlocks = s.blocks.filterNot { it is TemplateBlock.Header || it is TemplateBlock.Footer }.toMutableList()
            val index = contentBlocks.indexOfFirst { it is TemplateBlock.Images }
            if (index < 0) contentBlocks.add(block) else contentBlocks.add(index + 1, block)
            s.copy(blocks = normalizeLockedBlocks(s.blocks.filter { it is TemplateBlock.Header || it is TemplateBlock.Footer } + contentBlocks))
        }
    }

    private fun addBlockToImages(block: TemplateBlock) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            val withContainer = if (s.blocks.any { it is TemplateBlock.Images }) s.blocks else s.blocks + TemplateBlock.Images(id = IMAGES_BLOCK_ID)
            s.copy(blocks = normalizeLockedBlocks(withContainer.map {
                if (it is TemplateBlock.Images) it.copy(blocks = it.blocks + block) else it
            }))
        }
    }

    private fun addBlockToContainer(header: Boolean, block: TemplateBlock) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            val hasContainer = s.blocks.any { if (header) it is TemplateBlock.Header else it is TemplateBlock.Footer }
            val withContainer = if (hasContainer) s.blocks else s.blocks + if (header) {
                TemplateBlock.Header(id = UUID.randomUUID().toString())
            } else {
                TemplateBlock.Footer(id = UUID.randomUUID().toString())
            }
            s.copy(blocks = normalizeLockedBlocks(withContainer.map {
                when {
                    header && it is TemplateBlock.Header -> it.copy(blocks = it.blocks + block)
                    !header && it is TemplateBlock.Footer -> it.copy(blocks = it.blocks + block)
                    else -> it
                }
            }))
        }
    }

    private fun normalizeLockedBlocks(blocks: List<TemplateBlock>): List<TemplateBlock> {
        val header = blocks.firstOrNull { it is TemplateBlock.Header }
        val footer = blocks.firstOrNull { it is TemplateBlock.Footer }
        val middle = blocks.filterNot { it is TemplateBlock.Header || it is TemplateBlock.Footer }
        return listOfNotNull(header) + middle + listOfNotNull(footer)
    }

    private fun newTableBlock(rows: Int, columns: Int, hasHeaderColumn: Boolean) = TemplateBlock.Table(
        id = UUID.randomUUID().toString(),
        rows = rows.coerceIn(1, 50),
        columns = columns.coerceIn(1, 20),
        hasHeaderColumn = hasHeaderColumn,
        cells = List(rows.coerceIn(1, 50)) { List(columns.coerceIn(1, 20)) { "" } },
    )

    private fun updateStyledBlock(id: String, settings: TableCellSettings) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { block ->
                when (block) {
                    is TemplateBlock.Text -> if (block.id == id) block.copy(settings = settings) else block
                    is TemplateBlock.Date -> if (block.id == id) block.copy(settings = settings) else block
                    is TemplateBlock.PageNumber -> if (block.id == id) block.copy(settings = settings) else block
                    else -> block
                }
            })
        }
    }

    private fun updateNestedStyledBlock(containerId: String, blockId: String, settings: TableCellSettings) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { container ->
                when (container) {
                    is TemplateBlock.Header -> if (container.id == containerId) container.copy(blocks = container.blocks.map { it.withSettings(blockId, settings) }) else container
                    is TemplateBlock.Footer -> if (container.id == containerId) container.copy(blocks = container.blocks.map { it.withSettings(blockId, settings) }) else container
                    is TemplateBlock.Images -> if (container.id == containerId) container.copy(blocks = container.blocks.map { it.withSettings(blockId, settings) }) else container
                    else -> container
                }
            })
        }
    }

    private fun TemplateBlock.withSettings(blockId: String, settings: TableCellSettings): TemplateBlock = when (this) {
        is TemplateBlock.Text -> if (id == blockId) copy(settings = settings) else this
        is TemplateBlock.Date -> if (id == blockId) copy(settings = settings) else this
        is TemplateBlock.PageNumber -> if (id == blockId) copy(settings = settings) else this
        else -> this
    }

    private fun updateTable(id: String, transform: (TemplateBlock.Table) -> TemplateBlock.Table) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { block ->
                if (block is TemplateBlock.Table && block.id == id) transform(block) else block
            })
        }
    }

    private fun updateNestedTable(containerId: String, tableId: String, transform: (TemplateBlock.Table) -> TemplateBlock.Table) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { container ->
                when (container) {
                    is TemplateBlock.Header -> if (container.id == containerId) container.copy(blocks = container.blocks.map { if (it is TemplateBlock.Table && it.id == tableId) transform(it) else it }) else container
                    is TemplateBlock.Footer -> if (container.id == containerId) container.copy(blocks = container.blocks.map { if (it is TemplateBlock.Table && it.id == tableId) transform(it) else it }) else container
                    is TemplateBlock.Images -> if (container.id == containerId) container.copy(blocks = container.blocks.map { if (it is TemplateBlock.Table && it.id == tableId) transform(it) else it }) else container
                    else -> container
                }
            })
        }
    }

    private fun insertTableRowBelowTransform(table: TemplateBlock.Table, row: Int): TemplateBlock.Table {
        if (table.rows >= 50) return table
        val insertAt = (row + 1).coerceIn(0, table.rows.coerceIn(1, 50))
        val columns = table.columns.coerceIn(1, 20)
        return table.copy(
            rows = table.rows + 1,
            cells = table.normalizedCells().toMutableList().apply { add(insertAt, List(columns) { "" }) },
            rowSettings = table.normalizedRowSettings().toMutableList().apply { add(insertAt, TableAxisSettings()) },
            cellSettings = table.normalizedCellSettings().toMutableList().apply { add(insertAt, List(columns) { TableCellSettings() }) },
        )
    }

    private fun insertTableColumnRightTransform(table: TemplateBlock.Table, column: Int): TemplateBlock.Table {
        if (table.columns >= 20) return table
        val rows = table.rows.coerceIn(1, 50)
        val insertAt = (column + 1).coerceIn(0, table.columns.coerceIn(1, 20))
        return table.copy(
            columns = table.columns + 1,
            cells = table.normalizedCells().map { row -> row.toMutableList().apply { add(insertAt, "") } },
            columnSettings = table.normalizedColumnSettings().toMutableList().apply { add(insertAt, TableAxisSettings()) },
            cellSettings = table.normalizedCellSettings().map { row -> row.toMutableList().apply { add(insertAt, TableCellSettings()) } }.take(rows),
        )
    }

    private fun deleteTableRowTransform(table: TemplateBlock.Table, row: Int): TemplateBlock.Table {
        if (table.rows <= 1) return table
        val removeAt = row.coerceIn(0, table.rows - 1)
        return table.copy(
            rows = table.rows - 1,
            cells = table.normalizedCells().filterIndexed { index, _ -> index != removeAt },
            rowSettings = table.normalizedRowSettings().filterIndexed { index, _ -> index != removeAt },
            cellSettings = table.normalizedCellSettings().filterIndexed { index, _ -> index != removeAt },
        )
    }

    private fun deleteTableColumnTransform(table: TemplateBlock.Table, column: Int): TemplateBlock.Table {
        if (table.columns <= 1) return table
        val removeAt = column.coerceIn(0, table.columns - 1)
        val hasTickCells = table.normalizedCellSettings().any { row -> row.getOrNull(removeAt)?.toggleCheck == true }
        if (hasTickCells) return table
        return table.copy(
            columns = table.columns - 1,
            cells = table.normalizedCells().map { row -> row.filterIndexed { index, _ -> index != removeAt } },
            columnSettings = table.normalizedColumnSettings().filterIndexed { index, _ -> index != removeAt },
            cellSettings = table.normalizedCellSettings().map { row -> row.filterIndexed { index, _ -> index != removeAt } },
        )
    }

    private fun TemplateBlock.Table.normalizedCells(): List<List<String>> =
        List(rows.coerceIn(1, 50)) { row -> List(columns.coerceIn(1, 20)) { column -> cells.getOrNull(row)?.getOrNull(column).orEmpty() } }

    private fun TemplateBlock.Table.normalizedRowSettings(): List<TableAxisSettings> =
        List(rows.coerceIn(1, 50)) { row -> rowSettings.getOrNull(row) ?: TableAxisSettings() }

    private fun TemplateBlock.Table.normalizedColumnSettings(): List<TableAxisSettings> =
        List(columns.coerceIn(1, 20)) { column -> columnSettings.getOrNull(column) ?: TableAxisSettings() }

    private fun TemplateBlock.Table.normalizedCellSettings(): List<List<TableCellSettings>> =
        List(rows.coerceIn(1, 50)) { row -> List(columns.coerceIn(1, 20)) { column -> cellSettings.getOrNull(row)?.getOrNull(column) ?: TableCellSettings() } }

    private fun moveBlock(id: String, offset: Int) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            val list = s.blocks.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            val target = index + offset
            if (index < 0 || target < 0 || target >= list.size) return@update s
            if (list[index] is TemplateBlock.Header || list[index] is TemplateBlock.Footer) return@update s
            if (list[target] is TemplateBlock.Header || list[target] is TemplateBlock.Footer) return@update s
            val item = list.removeAt(index)
            list.add(target, item)
            s.copy(blocks = normalizeLockedBlocks(list))
        }
    }

    private fun moveNestedBlock(containerId: String, blockId: String, offset: Int) {
        if (_state.value.isReadOnly) return
        _state.update { s ->
            s.copy(blocks = s.blocks.map { container ->
                when (container) {
                    is TemplateBlock.Header -> if (container.id == containerId) container.copy(blocks = moveInList(container.blocks, blockId, offset)) else container
                    is TemplateBlock.Footer -> if (container.id == containerId) container.copy(blocks = moveInList(container.blocks, blockId, offset)) else container
                    is TemplateBlock.Images -> if (container.id == containerId) container.copy(blocks = moveInList(container.blocks, blockId, offset)) else container
                    else -> container
                }
            })
        }
    }

    private fun moveInList(blocks: List<TemplateBlock>, blockId: String, offset: Int): List<TemplateBlock> {
        val list = blocks.toMutableList()
        val index = list.indexOfFirst { it.id == blockId }
        val target = index + offset
        if (index < 0 || target < 0 || target >= list.size) return blocks
        val item = list.removeAt(index)
        list.add(target, item)
        return list
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
        val htmlBlocks = doc.select(".html-block[data-template-block-id]").associate { block ->
            block.attr("data-template-block-id") to block.html()
        }
        val textBlocks = doc.select(".text-block[data-template-block-id]")
            .filter { it.selectFirst("[contenteditable=true]") != null }
            .associate { block -> block.attr("data-template-block-id") to block.wholeText().trim() }
        if (tables.isEmpty() && htmlBlocks.isEmpty() && textBlocks.isEmpty()) return
        _state.update { s ->
            val updatedBlocks = s.blocks.map { block -> block.withPreviewValues(tables, htmlBlocks, textBlocks) }
            if (updatedBlocks == s.blocks) s else s.copy(blocks = updatedBlocks)
        }
    }

    private fun TemplateBlock.withPreviewValues(
        tables: Map<String, List<List<String>>>,
        htmlBlocks: Map<String, String>,
        textBlocks: Map<String, String>,
    ): TemplateBlock = when {
        this is TemplateBlock.Table && tables.containsKey(id) -> {
            val newCells = tables.getValue(id)
            if (newCells == cells) this else copy(cells = newCells)
        }
        this is TemplateBlock.Html && htmlBlocks.containsKey(id) -> {
            val newHtml = htmlBlocks.getValue(id)
            if (newHtml == html) this else copy(html = newHtml)
        }
        this is TemplateBlock.Text && textBlocks.containsKey(id) -> {
            val newText = textBlocks.getValue(id)
            if (newText == text) this else copy(text = newText)
        }
        this is TemplateBlock.Header -> {
            val newBlocks = blocks.map { it.withPreviewValues(tables, htmlBlocks, textBlocks) }
            if (newBlocks == blocks) this else copy(blocks = newBlocks)
        }
        this is TemplateBlock.Footer -> {
            val newBlocks = blocks.map { it.withPreviewValues(tables, htmlBlocks, textBlocks) }
            if (newBlocks == blocks) this else copy(blocks = newBlocks)
        }
        this is TemplateBlock.Images -> {
            val newBlocks = blocks.map { it.withPreviewValues(tables, htmlBlocks, textBlocks) }
            if (newBlocks == blocks) this else copy(blocks = newBlocks)
        }
        else -> this
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
                    title = "",
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
        const val DEFAULT_HTML_BLOCK = "<p contenteditable=\"true\">Szerkeszthető HTML szöveg</p>"
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
