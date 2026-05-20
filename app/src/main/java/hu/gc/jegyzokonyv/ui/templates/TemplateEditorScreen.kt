package hu.gc.jegyzokonyv.ui.templates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertPageBreak
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.gc.jegyzokonyv.R
import hu.gc.jegyzokonyv.domain.model.TableAxisSettings
import hu.gc.jegyzokonyv.domain.model.TableCellSettings
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.ui.editor.EditorHtmlWebView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TemplateEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fallbackName = stringResource(R.string.template_editor_fallback_name)
    val titleRes = if (state.isEdit) {
        R.string.template_editor_edit_title
    } else {
        R.string.template_editor_new_title
    }
    val canSave = !state.isReadOnly && !state.isSaving && !state.isLoading
    val context = LocalContext.current
    val previewDir = remember { File(context.cacheDir, "template-preview").apply { mkdirs() } }
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale("hu", "HU")).format(Date()) }
    var showTableDialog by remember { mutableStateOf(false) }
    var showChecklistDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(fallbackName, onSaved) },
                        enabled = canSave,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.action_save),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("name") {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text(stringResource(R.string.template_editor_name_hint)) },
                    singleLine = true,
                    enabled = !state.isReadOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item("title") {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text(stringResource(R.string.template_editor_title_hint)) },
                    supportingText = {
                        Text(stringResource(R.string.template_editor_title_helper))
                    },
                    singleLine = true,
                    enabled = !state.isReadOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!state.isReadOnly) {
                item("add-elements") {
                    AddElementsSection(
                        onAddText = viewModel::addTextBlock,
                        onAddDate = viewModel::addDateBlock,
                        onAddTable = { showTableDialog = true },
                        onAddSignature = viewModel::addSignatureBlock,
                        onAddStamp = viewModel::addStampBlock,
                        onAddPageBreak = viewModel::addPageBreakBlock,
                        onAddHeader = viewModel::addSafetyHeaderBlock,
                        onAddChecklist = { showChecklistDialog = true },
                    )
                }
            }
            items(state.blocks, key = { it.id }) { block ->
                BlockRow(
                    block = block,
                    readOnly = state.isReadOnly,
                    onTextChange = { value -> viewModel.onTextBlockChange(block.id, value) },
                    onTableCellTextChange = { row, column, value -> viewModel.onTableCellTextChange(block.id, row, column, value) },
                    onTableRowSettingsChange = { row, settings -> viewModel.onTableRowSettingsChange(block.id, row, settings) },
                    onTableColumnSettingsChange = { column, settings -> viewModel.onTableColumnSettingsChange(block.id, column, settings) },
                    onTableCellSettingsChange = { row, column, settings -> viewModel.onTableCellSettingsChange(block.id, row, column, settings) },
                    onInsertRowBelow = { row -> viewModel.insertTableRowBelow(block.id, row) },
                    onInsertColumnRight = { column -> viewModel.insertTableColumnRight(block.id, column) },
                    onDeleteRow = { row -> viewModel.deleteTableRow(block.id, row) },
                    onDeleteColumn = { column -> viewModel.deleteTableColumn(block.id, column) },
                    onMoveUp = { viewModel.moveBlockUp(block.id) },
                    onMoveDown = { viewModel.moveBlockDown(block.id) },
                    onRemove = { viewModel.removeBlock(block.id) },
                )
            }
            item("preview") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Előnézet", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 360.dp),
                            tonalElevation = 1.dp,
                        ) {
                            EditorHtmlWebView(
                                html = viewModel.previewHtml(todayIso),
                                draftDir = previewDir,
                                onHtmlChanged = viewModel::onPreviewHtmlChanged,
                                onEditableCellFocusedChanged = {},
                                dictatedText = null,
                                dictationToken = 0,
                                scrollToBottomToken = 0,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTableDialog) {
        AddTableDialog(
            onDismiss = { showTableDialog = false },
            onConfirm = { rows, columns, header ->
                showTableDialog = false
                viewModel.addTableBlock(rows, columns, header)
            },
        )
    }

    if (showChecklistDialog) {
        AddChecklistDialog(
            onDismiss = { showChecklistDialog = false },
            onConfirm = { rows, columns, tickColumnFirst ->
                showChecklistDialog = false
                viewModel.addCheckTableBlock(rows, columns, tickColumnFirst)
            },
        )
    }
}

@Composable
private fun AddElementsSection(
    onAddText: () -> Unit,
    onAddDate: () -> Unit,
    onAddTable: () -> Unit,
    onAddSignature: () -> Unit,
    onAddStamp: () -> Unit,
    onAddPageBreak: () -> Unit,
    onAddHeader: () -> Unit,
    onAddChecklist: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Elem hozzáadása", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Válassz egy elemet. A táblázatok és mezők tartalmát az előnézetben érintéssel lehet kitölteni.",
                style = MaterialTheme.typography.bodySmall,
            )
            AddElementButtonRow {
                AddElementButton(
                    text = stringResource(R.string.template_editor_add_text_block),
                    icon = Icons.Filled.Draw,
                    onClick = onAddText,
                    modifier = Modifier.weight(1f),
                )
                AddElementButton(
                    text = stringResource(R.string.template_editor_add_date_block),
                    icon = Icons.Filled.DateRange,
                    onClick = onAddDate,
                    modifier = Modifier.weight(1f),
                )
            }
            AddElementButtonRow {
                AddElementButton(
                    text = "Táblázat",
                    icon = Icons.Filled.GridOn,
                    onClick = onAddTable,
                    modifier = Modifier.weight(1f),
                )
                AddElementButton(
                    text = "Pipálható lista",
                    icon = Icons.Filled.Check,
                    onClick = onAddChecklist,
                    modifier = Modifier.weight(1f),
                )
            }
            AddElementButtonRow {
                AddElementButton(
                    text = "Aláírás",
                    icon = Icons.Filled.Draw,
                    onClick = onAddSignature,
                    modifier = Modifier.weight(1f),
                )
                AddElementButton(
                    text = "Bélyegző",
                    icon = Icons.Filled.Check,
                    onClick = onAddStamp,
                    modifier = Modifier.weight(1f),
                )
            }
            AddElementButtonRow {
                AddElementButton(
                    text = "Fejléc",
                    icon = Icons.Filled.GridOn,
                    onClick = onAddHeader,
                    modifier = Modifier.weight(1f),
                )
                AddElementButton(
                    text = "Oldaltörés",
                    icon = Icons.Filled.InsertPageBreak,
                    onClick = onAddPageBreak,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AddElementButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun AddElementButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun BlockRow(
    block: TemplateBlock,
    readOnly: Boolean,
    onTextChange: (String) -> Unit,
    onTableCellTextChange: (row: Int, column: Int, value: String) -> Unit,
    onTableRowSettingsChange: (row: Int, settings: TableAxisSettings) -> Unit,
    onTableColumnSettingsChange: (column: Int, settings: TableAxisSettings) -> Unit,
    onTableCellSettingsChange: (row: Int, column: Int, settings: TableCellSettings) -> Unit,
    onInsertRowBelow: (row: Int) -> Unit,
    onInsertColumnRight: (column: Int) -> Unit,
    onDeleteRow: (row: Int) -> Unit,
    onDeleteColumn: (column: Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (block) {
                is TemplateBlock.Text -> {
                    OutlinedTextField(
                        value = block.text,
                        onValueChange = onTextChange,
                        label = { Text(stringResource(R.string.template_editor_text_block_label)) },
                        enabled = !readOnly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                    )
                }
                is TemplateBlock.Date -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.template_editor_date_block_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is TemplateBlock.Table -> TableBlockEditor(
                    block = block,
                    readOnly = readOnly,
                    onCellTextChange = onTableCellTextChange,
                    onRowSettingsChange = onTableRowSettingsChange,
                    onColumnSettingsChange = onTableColumnSettingsChange,
                    onCellSettingsChange = onTableCellSettingsChange,
                    onInsertRowBelow = onInsertRowBelow,
                    onInsertColumnRight = onInsertColumnRight,
                    onDeleteRow = onDeleteRow,
                    onDeleteColumn = onDeleteColumn,
                )
                is TemplateBlock.Signature -> BlockLabel(Icons.Filled.Draw, "Aláírás a profilból")
                is TemplateBlock.Stamp -> BlockLabel(Icons.Filled.Check, "Bélyegző a profilból")
                is TemplateBlock.Images -> BlockLabel(Icons.Filled.Image, "Fotók helye (nem törölhető)")
                is TemplateBlock.PageBreak -> BlockLabel(Icons.Filled.InsertPageBreak, "Oldaltörés / új oldal")
                is TemplateBlock.Html -> HtmlBlockLabel(block)
            }
            if (!readOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onMoveUp) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(R.string.template_editor_block_move_up),
                        )
                    }
                    IconButton(onClick = onMoveDown) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = stringResource(R.string.template_editor_block_move_down),
                        )
                    }
                    if (block !is TemplateBlock.Images) {
                        IconButton(onClick = onRemove) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.template_editor_block_delete),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableBlockEditor(
    block: TemplateBlock.Table,
    readOnly: Boolean,
    onCellTextChange: (row: Int, column: Int, value: String) -> Unit,
    onRowSettingsChange: (row: Int, settings: TableAxisSettings) -> Unit,
    onColumnSettingsChange: (column: Int, settings: TableAxisSettings) -> Unit,
    onCellSettingsChange: (row: Int, column: Int, settings: TableCellSettings) -> Unit,
    onInsertRowBelow: (row: Int) -> Unit,
    onInsertColumnRight: (column: Int) -> Unit,
    onDeleteRow: (row: Int) -> Unit,
    onDeleteColumn: (column: Int) -> Unit,
) {
    val rows = block.rows.coerceIn(1, 50)
    val columns = block.columns.coerceIn(1, 20)
    var rowDialog by remember(block.id) { mutableStateOf<Int?>(null) }
    var columnDialog by remember(block.id) { mutableStateOf<Int?>(null) }
    var cellDialog by remember(block.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockLabel(
            icon = Icons.Filled.GridOn,
            text = "Táblázat: $rows sor × $columns oszlop" + if (block.hasHeaderColumn) ", fejléc oszloppal" else "",
        )
        Text(
            text = "Koppints az oszlop nyílra, sor nyílra vagy cellára a beállítások szerkesztéséhez.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(42.dp))
            repeat(columns) { columnIndex ->
                val isTickColumn = (0 until rows).any { row -> block.cellSettings.getOrNull(row)?.getOrNull(columnIndex)?.toggleCheck == true }
                SettingsArrow(
                    text = "↓",
                    onClick = { if (!readOnly) columnDialog = columnIndex },
                    modifier = if (isTickColumn) Modifier.width(46.dp) else Modifier.weight(1f),
                )
            }
        }
        repeat(rows) { rowIndex ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SettingsArrow(
                    text = "→",
                    onClick = { if (!readOnly) rowDialog = rowIndex },
                    modifier = Modifier.width(42.dp).heightIn(min = 64.dp),
                )
                repeat(columns) { columnIndex ->
                    val cellSettings = block.cellSettings.getOrNull(rowIndex)?.getOrNull(columnIndex)
                    CellPreview(
                        value = block.cells.getOrNull(rowIndex)?.getOrNull(columnIndex).orEmpty(),
                        label = "${rowIndex + 1}.${columnIndex + 1}",
                        enabled = !readOnly,
                        onClick = { if (!readOnly) cellDialog = rowIndex to columnIndex },
                        modifier = if (cellSettings?.toggleCheck == true) Modifier.width(46.dp) else Modifier.weight(1f),
                    )
                }
            }
        }
    }

    rowDialog?.let { row ->
        AxisSettingsDialog(
            title = "${row + 1}. sor beállítása",
            settings = block.rowSettings.getOrNull(row) ?: TableAxisSettings(),
            mergeEnabled = true,
            mergeText = "Sor celláinak összevonása",
            tickColorsEnabled = false,
            insertText = "Új sor beszúrása alá",
            deleteText = "Sor törlése",
            deleteEnabled = rows > 1,
            onInsert = {
                rowDialog = null
                onInsertRowBelow(row)
            },
            onDelete = {
                rowDialog = null
                onDeleteRow(row)
            },
            onDismiss = { rowDialog = null },
            onConfirm = {
                rowDialog = null
                onRowSettingsChange(row, it)
            },
        )
    }
    columnDialog?.let { column ->
        AxisSettingsDialog(
            title = "${column + 1}. oszlop beállítása",
            settings = block.columnSettings.getOrNull(column) ?: TableAxisSettings(),
            mergeEnabled = (0 until rows).none { block.cellSettings.getOrNull(it)?.getOrNull(column)?.toggleCheck == true },
            mergeText = "Oszlop celláinak összevonása",
            tickColorsEnabled = (0 until rows).any { block.cellSettings.getOrNull(it)?.getOrNull(column)?.toggleCheck == true },
            insertText = "Új oszlop beszúrása jobbra",
            deleteText = "Oszlop törlése",
            deleteEnabled = columns > 1 && (0 until rows).none { block.cellSettings.getOrNull(it)?.getOrNull(column)?.toggleCheck == true },
            onInsert = {
                columnDialog = null
                onInsertColumnRight(column)
            },
            onDelete = {
                columnDialog = null
                onDeleteColumn(column)
            },
            onDismiss = { columnDialog = null },
            onConfirm = {
                columnDialog = null
                onColumnSettingsChange(column, it)
            },
        )
    }
    cellDialog?.let { (row, column) ->
        CellSettingsDialog(
            title = "${row + 1}.${column + 1} cella szerkesztése",
            text = block.cells.getOrNull(row)?.getOrNull(column).orEmpty(),
            settings = block.cellSettings.getOrNull(row)?.getOrNull(column) ?: TableCellSettings(),
            mergeRightEnabled = column + 1 < columns && block.cellSettings.getOrNull(row)?.getOrNull(column)?.toggleCheck != true && block.cellSettings.getOrNull(row)?.getOrNull(column + 1)?.toggleCheck != true,
            onDismiss = { cellDialog = null },
            onConfirm = { newText, newSettings ->
                cellDialog = null
                onCellTextChange(row, column, newText)
                onCellSettingsChange(row, column, newSettings)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CellPreview(
    value: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .heightIn(min = 64.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick, enabled = enabled)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value.ifBlank { " " }, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsArrow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AxisSettingsDialog(
    title: String,
    settings: TableAxisSettings,
    mergeEnabled: Boolean,
    mergeText: String,
    tickColorsEnabled: Boolean,
    insertText: String,
    deleteText: String,
    deleteEnabled: Boolean,
    onInsert: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (TableAxisSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSettingButtons(
                    backgroundColor = draft.backgroundColor,
                    textColor = draft.textColor,
                    onBackgroundChange = { draft = draft.copy(backgroundColor = it) },
                    onTextColorChange = { draft = draft.copy(textColor = it) },
                )
                TextAlignPicker(value = draft.textAlign, onChange = { draft = draft.copy(textAlign = it) })
                if (tickColorsEnabled) {
                    TickColorSettings(
                        xBackgroundColor = draft.tickXBackgroundColor,
                        xTextColor = draft.tickXTextColor,
                        checkedBackgroundColor = draft.tickCheckedBackgroundColor,
                        checkedTextColor = draft.tickCheckedTextColor,
                        onXBackgroundChange = { draft = draft.copy(tickXBackgroundColor = it) },
                        onXTextChange = { draft = draft.copy(tickXTextColor = it) },
                        onCheckedBackgroundChange = { draft = draft.copy(tickCheckedBackgroundColor = it) },
                        onCheckedTextChange = { draft = draft.copy(tickCheckedTextColor = it) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft.editable != false, onCheckedChange = { draft = draft.copy(editable = it) })
                    Text("Szerkeszthető cellák")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft.hideIfEmpty, onCheckedChange = { draft = draft.copy(hideIfEmpty = it) })
                    Text("Eltűnik exportnál, ha az üres szerkeszthető cellák üresek")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft.mergeAll && mergeEnabled, onCheckedChange = { draft = draft.copy(mergeAll = it) }, enabled = mergeEnabled)
                    Text(if (mergeEnabled) mergeText else "$mergeText (pipa oszlopnál nem elérhető)")
                }
                OutlinedButton(onClick = onInsert, modifier = Modifier.fillMaxWidth()) {
                    Text(insertText)
                }
                OutlinedButton(onClick = onDelete, enabled = deleteEnabled, modifier = Modifier.fillMaxWidth()) {
                    Text(if (deleteEnabled) deleteText else "$deleteText (nem elérhető)")
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CellSettingsDialog(
    title: String,
    text: String,
    settings: TableCellSettings,
    mergeRightEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, TableCellSettings) -> Unit,
) {
    var draftText by remember(text) { mutableStateOf(text) }
    var draft by remember(settings) { mutableStateOf(settings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.toggleCheck) {
                    Text("Alapértelmezett érték", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { draftText = "X" }, modifier = Modifier.weight(1f)) { Text("X") }
                        OutlinedButton(onClick = { draftText = "✓" }, modifier = Modifier.weight(1f)) { Text("✓") }
                    }
                } else {
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        label = { Text("Cella szövege") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ColorSettingButtons(
                    backgroundColor = draft.backgroundColor,
                    textColor = draft.textColor,
                    onBackgroundChange = { draft = draft.copy(backgroundColor = it) },
                    onTextColorChange = { draft = draft.copy(textColor = it) },
                )
                if (!draft.toggleCheck) {
                    TextAlignPicker(value = draft.textAlign, onChange = { draft = draft.copy(textAlign = it) })
                } else {
                    TickColorSettings(
                        xBackgroundColor = draft.tickXBackgroundColor,
                        xTextColor = draft.tickXTextColor,
                        checkedBackgroundColor = draft.tickCheckedBackgroundColor,
                        checkedTextColor = draft.tickCheckedTextColor,
                        onXBackgroundChange = { draft = draft.copy(tickXBackgroundColor = it) },
                        onXTextChange = { draft = draft.copy(tickXTextColor = it) },
                        onCheckedBackgroundChange = { draft = draft.copy(tickCheckedBackgroundColor = it) },
                        onCheckedTextChange = { draft = draft.copy(tickCheckedTextColor = it) },
                    )
                }
                if (!draft.toggleCheck) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = draft.editable != false, onCheckedChange = { draft = draft.copy(editable = it) })
                        Text("Szerkeszthető")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft.hideIfEmpty, onCheckedChange = { draft = draft.copy(hideIfEmpty = it) })
                    Text("Eltűnik exportnál, ha üres")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = draft.mergeRight && mergeRightEnabled,
                        onCheckedChange = { draft = draft.copy(mergeRight = it) },
                        enabled = mergeRightEnabled,
                    )
                    Text(if (mergeRightEnabled) "Összevonás a jobb oldali cellával" else "Összevonás nem elérhető pipa cellánál")
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(draftText, draft) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TextAlignPicker(value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Szöveg igazítása", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("left" to "Bal", "center" to "Közép", "right" to "Jobb").forEach { (align, label) ->
                val selected = value == align || (value.isBlank() && align == "left")
                if (selected) {
                    Button(onClick = { onChange(align) }, modifier = Modifier.weight(1f)) { Text(label) }
                } else {
                    OutlinedButton(onClick = { onChange(align) }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun TickColorSettings(
    xBackgroundColor: String,
    xTextColor: String,
    checkedBackgroundColor: String,
    checkedTextColor: String,
    onXBackgroundChange: (String) -> Unit,
    onXTextChange: (String) -> Unit,
    onCheckedBackgroundChange: (String) -> Unit,
    onCheckedTextChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pipa/X állapot színei", style = MaterialTheme.typography.labelLarge)
        ColorSettingButtons(
            backgroundColor = xBackgroundColor,
            textColor = xTextColor,
            backgroundLabel = "X háttér",
            textLabel = "X betű",
            emptyBackgroundText = "X: alap háttér",
            emptyTextColorText = "X: alap betűszín",
            onBackgroundChange = onXBackgroundChange,
            onTextColorChange = onXTextChange,
        )
        ColorSettingButtons(
            backgroundColor = checkedBackgroundColor,
            textColor = checkedTextColor,
            backgroundLabel = "✓ háttér",
            textLabel = "✓ betű",
            emptyBackgroundText = "✓: alap háttér",
            emptyTextColorText = "✓: alap betűszín",
            onBackgroundChange = onCheckedBackgroundChange,
            onTextColorChange = onCheckedTextChange,
        )
    }
}

@Composable
private fun ColorSettingButtons(
    backgroundColor: String,
    textColor: String,
    backgroundLabel: String = "Háttérszín",
    textLabel: String = "Betűszín",
    emptyBackgroundText: String = "Nincs háttérszín",
    emptyTextColorText: String = "Alapértelmezett betűszín",
    onBackgroundChange: (String) -> Unit,
    onTextColorChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf<ColorTarget?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = if (expanded == ColorTarget.Background) null else ColorTarget.Background }, modifier = Modifier.weight(1f)) {
                Text(backgroundLabel)
            }
            OutlinedButton(onClick = { expanded = if (expanded == ColorTarget.Text) null else ColorTarget.Text }, modifier = Modifier.weight(1f)) {
                Text(textLabel)
            }
        }
        when (expanded) {
            ColorTarget.Background -> ColorPicker(
                title = backgroundLabel,
                emptyText = emptyBackgroundText,
                value = backgroundColor,
                onChange = onBackgroundChange,
            )
            ColorTarget.Text -> ColorPicker(
                title = textLabel,
                emptyText = emptyTextColorText,
                value = textColor,
                onChange = onTextColorChange,
            )
            null -> Unit
        }
    }
}

private enum class ColorTarget { Background, Text }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorPicker(title: String, emptyText: String, value: String, onChange: (String) -> Unit) {
    var customRed by remember(value) { mutableStateOf(hexPart(value, 1)) }
    var customGreen by remember(value) { mutableStateOf(hexPart(value, 3)) }
    var customBlue by remember(value) { mutableStateOf(hexPart(value, 5)) }
    var showCustom by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESET_COLORS.forEach { (_, hex) ->
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(hex.toComposeColor(), RoundedCornerShape(6.dp))
                        .border(2.dp, if (value.equals(hex, true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .combinedClickable(onClick = { onChange(hex) }),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onChange("") }, modifier = Modifier.weight(1f)) { Text(emptyText) }
            OutlinedButton(onClick = { showCustom = !showCustom }, modifier = Modifier.weight(1f)) { Text("Egyedi szín") }
        }
        if (showCustom) {
            ColorSlider("Piros", customRed) {
                customRed = it
                onChange(rgbHex(customRed, customGreen, customBlue))
            }
            ColorSlider("Zöld", customGreen) {
                customGreen = it
                onChange(rgbHex(customRed, customGreen, customBlue))
            }
            ColorSlider("Kék", customBlue) {
                customBlue = it
                onChange(rgbHex(customRed, customGreen, customBlue))
            }
        }
    }
}

@Composable
private fun ColorSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value", style = MaterialTheme.typography.bodySmall)
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt().coerceIn(0, 255)) }, valueRange = 0f..255f)
    }
}

private val PRESET_COLORS = listOf(
    "Piros" to "#FF0000",
    "Kék" to "#0000FF",
    "Zöld" to "#00AA00",
    "Sárga" to "#FFFF00",
    "Fekete" to "#000000",
    "Fehér" to "#FFFFFF",
    "Szürke" to "#808080",
)

private fun String.toComposeColor(): Color = runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(Color.Transparent)
private fun hexPart(hex: String, start: Int): Int = hex.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.substring(start, start + 2)?.toInt(16) ?: 255
private fun rgbHex(red: Int, green: Int, blue: Int): String = "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

@Composable
private fun HtmlBlockLabel(block: TemplateBlock.Html) {
    val label = when {
        block.html.contains("data-toggle-check") -> "Pipálható lista"
        block.html.contains("kockázati szint", ignoreCase = true) -> "Kockázat tábla"
        block.html.contains("<table", ignoreCase = true) -> "Fejléc / táblázatos rész"
        else -> "Szerkeszthető mező"
    }
    BlockLabel(Icons.Filled.GridOn, "$label (az előnézetben szerkeszthető)")
}

@Composable
private fun BlockLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AddTableDialog(
    onDismiss: () -> Unit,
    onConfirm: (rows: Int, columns: Int, hasHeaderColumn: Boolean) -> Unit,
) {
    var rows by remember { mutableStateOf("3") }
    var columns by remember { mutableStateOf("3") }
    var headerColumn by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Táblázat hozzáadása") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = rows, onValueChange = { rows = it.filter(Char::isDigit) }, label = { Text("Sorok") }, singleLine = true)
                OutlinedTextField(value = columns, onValueChange = { columns = it.filter(Char::isDigit) }, label = { Text("Oszlopok") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = headerColumn, onCheckedChange = { headerColumn = it })
                    Text("Legyen fejléc oszlop")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(rows.toIntOrNull() ?: 1, columns.toIntOrNull() ?: 1, headerColumn) }) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun AddChecklistDialog(
    onDismiss: () -> Unit,
    onConfirm: (rows: Int, columns: Int, tickColumnFirst: Boolean) -> Unit,
) {
    var rows by remember { mutableStateOf("3") }
    var columns by remember { mutableStateOf("2") }
    var tickColumnFirst by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pipálható lista hozzáadása") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = rows, onValueChange = { rows = it.filter(Char::isDigit) }, label = { Text("Sorok") }, singleLine = true)
                OutlinedTextField(value = columns, onValueChange = { columns = it.filter(Char::isDigit) }, label = { Text("Oszlopok") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tickColumnFirst, onCheckedChange = { tickColumnFirst = it })
                    Text("A pipa oszlop legyen az első (kikapcsolva: utolsó)")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(rows.toIntOrNull() ?: 1, columns.toIntOrNull() ?: 1, tickColumnFirst) }) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
