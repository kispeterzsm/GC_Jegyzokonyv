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
import androidx.compose.material.icons.filled.Settings
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
    var showHeaderTableDialog by remember { mutableStateOf(false) }
    var showFooterTableDialog by remember { mutableStateOf(false) }
    var showImageTableDialog by remember { mutableStateOf(false) }
    var showChecklistDialog by remember { mutableStateOf(false) }
    val previewHtml = remember(state, todayIso) { viewModel.previewHtml(todayIso) }

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
            if (!state.isReadOnly) {
                item("add-elements") {
                    AddElementsSection(
                        onAddText = viewModel::addTextBlock,
                        onAddDate = viewModel::addDateBlock,
                        onAddTable = { showTableDialog = true },
                        onAddSignature = viewModel::addSignatureBlock,
                        onAddStamp = viewModel::addStampBlock,
                        onAddPageBreak = viewModel::addPageBreakBlock,
                        onAddHeader = viewModel::addHeaderBlock,
                        onAddFooter = viewModel::addFooterBlock,
                        onAddHeaderPageNumber = viewModel::addPageNumberToHeader,
                        onAddFooterPageNumber = viewModel::addPageNumberToFooter,
                        onAddHeaderText = viewModel::addHeaderTextBlock,
                        onAddFooterText = viewModel::addFooterTextBlock,
                        onAddHeaderTable = { showHeaderTableDialog = true },
                        onAddFooterTable = { showFooterTableDialog = true },
                        onAddImageText = viewModel::addImageTextBlock,
                        onAddImageDate = viewModel::addImageDateBlock,
                        onAddImageTable = { showImageTableDialog = true },
                        onAddImagePageNumber = viewModel::addImagePageNumberBlock,
                        onAddChecklist = { showChecklistDialog = true },
                    )
                }
            }
            items(state.blocks, key = { it.id }) { block ->
                BlockRow(
                    block = block,
                    readOnly = state.isReadOnly,
                    onTextChange = { value -> viewModel.onTextBlockChange(block.id, value) },
                    onTextSettingsChange = { settings -> viewModel.onTextBlockSettingsChange(block.id, settings) },
                    onDateSettingsChange = { settings -> viewModel.onDateBlockSettingsChange(block.id, settings) },
                    onPageNumberSettingsChange = { settings -> viewModel.onPageNumberBlockSettingsChange(block.id, settings) },
                    onNestedTextChange = { childId, value -> viewModel.onNestedTextBlockChange(block.id, childId, value) },
                    onNestedTextSettingsChange = { childId, settings -> viewModel.onNestedTextBlockSettingsChange(block.id, childId, settings) },
                    onNestedDateSettingsChange = { childId, settings -> viewModel.onNestedDateBlockSettingsChange(block.id, childId, settings) },
                    onNestedPageNumberSettingsChange = { childId, settings -> viewModel.onNestedPageNumberBlockSettingsChange(block.id, childId, settings) },
                    onNestedRemove = { childId -> viewModel.removeNestedBlock(block.id, childId) },
                    onNestedMoveUp = { childId -> viewModel.moveNestedBlockUp(block.id, childId) },
                    onNestedMoveDown = { childId -> viewModel.moveNestedBlockDown(block.id, childId) },
                    onNestedTableCellTextChange = { tableId, row, column, value -> viewModel.onNestedTableCellTextChange(block.id, tableId, row, column, value) },
                    onNestedTableRowSettingsChange = { tableId, row, settings -> viewModel.onNestedTableRowSettingsChange(block.id, tableId, row, settings) },
                    onNestedTableColumnSettingsChange = { tableId, column, settings -> viewModel.onNestedTableColumnSettingsChange(block.id, tableId, column, settings) },
                    onNestedTableCellSettingsChange = { tableId, row, column, settings -> viewModel.onNestedTableCellSettingsChange(block.id, tableId, row, column, settings) },
                    onNestedInsertRowBelow = { tableId, row -> viewModel.insertNestedTableRowBelow(block.id, tableId, row) },
                    onNestedInsertColumnRight = { tableId, column -> viewModel.insertNestedTableColumnRight(block.id, tableId, column) },
                    onNestedDeleteRow = { tableId, row -> viewModel.deleteNestedTableRow(block.id, tableId, row) },
                    onNestedDeleteColumn = { tableId, column -> viewModel.deleteNestedTableColumn(block.id, tableId, column) },
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
                                html = previewHtml,
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

    if (showHeaderTableDialog) {
        AddTableDialog(
            onDismiss = { showHeaderTableDialog = false },
            onConfirm = { rows, columns, header ->
                showHeaderTableDialog = false
                viewModel.addHeaderTableBlock(rows, columns, header)
            },
        )
    }
    if (showFooterTableDialog) {
        AddTableDialog(
            onDismiss = { showFooterTableDialog = false },
            onConfirm = { rows, columns, header ->
                showFooterTableDialog = false
                viewModel.addFooterTableBlock(rows, columns, header)
            },
        )
    }
    if (showImageTableDialog) {
        AddTableDialog(
            onDismiss = { showImageTableDialog = false },
            onConfirm = { rows, columns, header ->
                showImageTableDialog = false
                viewModel.addImageTableBlock(rows, columns, header)
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
    onAddFooter: () -> Unit,
    onAddHeaderPageNumber: () -> Unit,
    onAddFooterPageNumber: () -> Unit,
    onAddHeaderText: () -> Unit,
    onAddFooterText: () -> Unit,
    onAddHeaderTable: () -> Unit,
    onAddFooterTable: () -> Unit,
    onAddImageText: () -> Unit,
    onAddImageDate: () -> Unit,
    onAddImageTable: () -> Unit,
    onAddImagePageNumber: () -> Unit,
    onAddChecklist: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            var page by remember { mutableStateOf(AddElementPage.Default) }
            Text("Elem hozzáadása", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Válassz egy elemet. A táblázatok és mezők tartalmát az előnézetben érintéssel lehet kitölteni.",
                style = MaterialTheme.typography.bodySmall,
            )
            AddElementButtonRow {
                AddElementPageButton("Alap", selected = page == AddElementPage.Default, onClick = { page = AddElementPage.Default }, modifier = Modifier.weight(1f))
                AddElementPageButton("Fejléc/lábléc", selected = page == AddElementPage.HeaderFooter, onClick = { page = AddElementPage.HeaderFooter }, modifier = Modifier.weight(1f))
                AddElementPageButton("Fotók", selected = page == AddElementPage.Photos, onClick = { page = AddElementPage.Photos }, modifier = Modifier.weight(1f))
            }
            when (page) {
                AddElementPage.Default -> {
                    AddElementButtonRow {
                        AddElementButton(text = stringResource(R.string.template_editor_add_text_block), icon = Icons.Filled.Draw, onClick = onAddText, modifier = Modifier.weight(1f))
                        AddElementButton(text = stringResource(R.string.template_editor_add_date_block), icon = Icons.Filled.DateRange, onClick = onAddDate, modifier = Modifier.weight(1f))
                    }
                    AddElementButtonRow {
                        AddElementButton(text = "Táblázat", icon = Icons.Filled.GridOn, onClick = onAddTable, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Pipálható lista", icon = Icons.Filled.Check, onClick = onAddChecklist, modifier = Modifier.weight(1f))
                    }
                    AddElementButtonRow {
                        AddElementButton(text = "Aláírás", icon = Icons.Filled.Draw, onClick = onAddSignature, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Bélyegző", icon = Icons.Filled.Check, onClick = onAddStamp, modifier = Modifier.weight(1f))
                    }
                    AddElementButtonRow {
                        AddElementButton(text = "Oldaltörés", icon = Icons.Filled.InsertPageBreak, onClick = onAddPageBreak, modifier = Modifier.weight(1f))
                    }
                }
                AddElementPage.HeaderFooter -> {
                    Text("Fejléc és lábléc (minden oldalon)", style = MaterialTheme.typography.titleSmall)
                    AddElementButtonRow {
                        AddElementButton(text = "Fejléc", icon = Icons.Filled.GridOn, onClick = onAddHeader, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Lábléc", icon = Icons.Filled.GridOn, onClick = onAddFooter, modifier = Modifier.weight(1f))
                    }
                    AddElementButtonRow {
                        AddElementButton(text = "Szöveg fejlécbe", icon = Icons.Filled.Draw, onClick = onAddHeaderText, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Szöveg láblécbe", icon = Icons.Filled.Draw, onClick = onAddFooterText, modifier = Modifier.weight(1f))
                    }
                    AddElementButtonRow {
                        AddElementButton(text = "Táblázat fejlécbe", icon = Icons.Filled.GridOn, onClick = onAddHeaderTable, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Táblázat láblécbe", icon = Icons.Filled.GridOn, onClick = onAddFooterTable, modifier = Modifier.weight(1f))
                    }
                    AddElementButtonRow {
                        AddElementButton(text = "Oldalszám fejlécbe", icon = Icons.Filled.InsertPageBreak, onClick = onAddHeaderPageNumber, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Oldalszám láblécbe", icon = Icons.Filled.InsertPageBreak, onClick = onAddFooterPageNumber, modifier = Modifier.weight(1f))
                    }
                }
                AddElementPage.Photos -> {
                    Text("Fotóoldalak (minden kép alatt)", style = MaterialTheme.typography.titleSmall)
                    AddElementButtonRow {
                        AddElementButton(text = "Szöveg fotóhoz", icon = Icons.Filled.Draw, onClick = onAddImageText, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Dátum fotóhoz", icon = Icons.Filled.DateRange, onClick = onAddImageDate, modifier = Modifier.weight(1f))
                    }
                    AddElementButtonRow {
                        AddElementButton(text = "Táblázat fotóhoz", icon = Icons.Filled.GridOn, onClick = onAddImageTable, modifier = Modifier.weight(1f))
                        AddElementButton(text = "Oldalszám fotóhoz", icon = Icons.Filled.InsertPageBreak, onClick = onAddImagePageNumber, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private enum class AddElementPage { Default, HeaderFooter, Photos }

@Composable
private fun AddElementPageButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.heightIn(min = 40.dp)) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 40.dp)) { Text(text) }
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
    onTextSettingsChange: (TableCellSettings) -> Unit,
    onDateSettingsChange: (TableCellSettings) -> Unit,
    onPageNumberSettingsChange: (TableCellSettings) -> Unit,
    onNestedTextChange: (blockId: String, value: String) -> Unit,
    onNestedTextSettingsChange: (blockId: String, settings: TableCellSettings) -> Unit,
    onNestedDateSettingsChange: (blockId: String, settings: TableCellSettings) -> Unit,
    onNestedPageNumberSettingsChange: (blockId: String, settings: TableCellSettings) -> Unit,
    onNestedRemove: (blockId: String) -> Unit,
    onNestedMoveUp: (blockId: String) -> Unit,
    onNestedMoveDown: (blockId: String) -> Unit,
    onNestedTableCellTextChange: (tableId: String, row: Int, column: Int, value: String) -> Unit,
    onNestedTableRowSettingsChange: (tableId: String, row: Int, settings: TableAxisSettings) -> Unit,
    onNestedTableColumnSettingsChange: (tableId: String, column: Int, settings: TableAxisSettings) -> Unit,
    onNestedTableCellSettingsChange: (tableId: String, row: Int, column: Int, settings: TableCellSettings) -> Unit,
    onNestedInsertRowBelow: (tableId: String, row: Int) -> Unit,
    onNestedInsertColumnRight: (tableId: String, column: Int) -> Unit,
    onNestedDeleteRow: (tableId: String, row: Int) -> Unit,
    onNestedDeleteColumn: (tableId: String, column: Int) -> Unit,
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
    var styleDialog by remember(block.id) { mutableStateOf<TableCellSettings?>(null) }
    var styleConfirm by remember(block.id) { mutableStateOf<(TableCellSettings) -> Unit>({}) }
    var tableDialog by remember(block.id) { mutableStateOf(false) }

    val label = when (block) {
        is TemplateBlock.Text -> "Szöveg"
        is TemplateBlock.Date -> "Dátum"
        is TemplateBlock.Table -> "Táblázat: ${block.rows.coerceIn(1, 50)} sor × ${block.columns.coerceIn(1, 20)} oszlop"
        is TemplateBlock.Signature -> "Aláírás a profilból"
        is TemplateBlock.Stamp -> "Bélyegző a profilból"
        is TemplateBlock.Images -> "Fotóoldal sablon (nem törölhető)"
        is TemplateBlock.Image -> "Kép helye"
        is TemplateBlock.PageBreak -> "Oldaltörés / új oldal"
        is TemplateBlock.PageNumber -> "Oldalszám"
        is TemplateBlock.Header -> "Fejléc (minden oldalon)"
        is TemplateBlock.Footer -> "Lábléc (minden oldalon)"
        is TemplateBlock.Html -> "HTML / táblázatos rész"
    }
    val icon = when (block) {
        is TemplateBlock.Date -> Icons.Filled.DateRange
        is TemplateBlock.Table, is TemplateBlock.Header, is TemplateBlock.Footer, is TemplateBlock.Html -> Icons.Filled.GridOn
        is TemplateBlock.Signature, is TemplateBlock.Text -> Icons.Filled.Draw
        is TemplateBlock.Stamp -> Icons.Filled.Check
        is TemplateBlock.Images, is TemplateBlock.Image -> Icons.Filled.Image
        is TemplateBlock.PageBreak, is TemplateBlock.PageNumber -> Icons.Filled.InsertPageBreak
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(imageVector = icon, contentDescription = null)
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (!readOnly) {
                    when (block) {
                        is TemplateBlock.Text -> IconButton(onClick = { styleConfirm = onTextSettingsChange; styleDialog = block.settings }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                        is TemplateBlock.Date -> IconButton(onClick = { styleConfirm = onDateSettingsChange; styleDialog = block.settings }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                        is TemplateBlock.PageNumber -> IconButton(onClick = { styleConfirm = onPageNumberSettingsChange; styleDialog = block.settings }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                        is TemplateBlock.Table -> IconButton(onClick = { tableDialog = true }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                        else -> Unit
                    }
                }
            }

            when (block) {
                is TemplateBlock.Text -> OutlinedTextField(
                    value = block.text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(R.string.template_editor_text_block_label)) },
                    enabled = !readOnly,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                )
                is TemplateBlock.Header -> ContainerBlockLabel(
                    title = "Fejléc tartalma",
                    blocks = block.blocks,
                    readOnly = readOnly,
                    onTextChange = onNestedTextChange,
                    onTextSettingsChange = onNestedTextSettingsChange,
                    onDateSettingsChange = onNestedDateSettingsChange,
                    onPageNumberSettingsChange = onNestedPageNumberSettingsChange,
                    onDeleteBlock = onNestedRemove,
                    onMoveBlockUp = onNestedMoveUp,
                    onMoveBlockDown = onNestedMoveDown,
                    onTableCellTextChange = onNestedTableCellTextChange,
                    onTableRowSettingsChange = onNestedTableRowSettingsChange,
                    onTableColumnSettingsChange = onNestedTableColumnSettingsChange,
                    onTableCellSettingsChange = onNestedTableCellSettingsChange,
                    onInsertRowBelow = onNestedInsertRowBelow,
                    onInsertColumnRight = onNestedInsertColumnRight,
                    onDeleteRow = onNestedDeleteRow,
                    onDeleteColumn = onNestedDeleteColumn,
                )
                is TemplateBlock.Footer -> ContainerBlockLabel(
                    title = "Lábléc tartalma",
                    blocks = block.blocks,
                    readOnly = readOnly,
                    onTextChange = onNestedTextChange,
                    onTextSettingsChange = onNestedTextSettingsChange,
                    onDateSettingsChange = onNestedDateSettingsChange,
                    onPageNumberSettingsChange = onNestedPageNumberSettingsChange,
                    onDeleteBlock = onNestedRemove,
                    onMoveBlockUp = onNestedMoveUp,
                    onMoveBlockDown = onNestedMoveDown,
                    onTableCellTextChange = onNestedTableCellTextChange,
                    onTableRowSettingsChange = onNestedTableRowSettingsChange,
                    onTableColumnSettingsChange = onNestedTableColumnSettingsChange,
                    onTableCellSettingsChange = onNestedTableCellSettingsChange,
                    onInsertRowBelow = onNestedInsertRowBelow,
                    onInsertColumnRight = onNestedInsertColumnRight,
                    onDeleteRow = onNestedDeleteRow,
                    onDeleteColumn = onNestedDeleteColumn,
                )
                is TemplateBlock.Images -> ContainerBlockLabel(
                    title = "Fotóoldal tartalma",
                    blocks = block.blocks,
                    readOnly = readOnly,
                    onTextChange = onNestedTextChange,
                    onTextSettingsChange = onNestedTextSettingsChange,
                    onDateSettingsChange = onNestedDateSettingsChange,
                    onPageNumberSettingsChange = onNestedPageNumberSettingsChange,
                    onDeleteBlock = onNestedRemove,
                    onMoveBlockUp = onNestedMoveUp,
                    onMoveBlockDown = onNestedMoveDown,
                    onTableCellTextChange = onNestedTableCellTextChange,
                    onTableRowSettingsChange = onNestedTableRowSettingsChange,
                    onTableColumnSettingsChange = onNestedTableColumnSettingsChange,
                    onTableCellSettingsChange = onNestedTableCellSettingsChange,
                    onInsertRowBelow = onNestedInsertRowBelow,
                    onInsertColumnRight = onNestedInsertColumnRight,
                    onDeleteRow = onNestedDeleteRow,
                    onDeleteColumn = onNestedDeleteColumn,
                )
                is TemplateBlock.Html -> HtmlBlockLabel(block)
                else -> Unit
            }

            if (!readOnly) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onMoveUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.template_editor_block_move_up)) }
                    IconButton(onClick = onMoveDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.template_editor_block_move_down)) }
                    if (block !is TemplateBlock.Images) {
                        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.template_editor_block_delete)) }
                    }
                }
            }
        }
    }

    styleDialog?.let { settings ->
        StyledBlockSettingsDialog(
            settings = settings,
            onDismiss = { styleDialog = null },
            onDelete = {
                styleDialog = null
                onRemove()
            },
            onConfirm = {
                styleDialog = null
                styleConfirm(it)
            },
        )
    }
    if (tableDialog && block is TemplateBlock.Table) {
        TableSettingsDialog(
            block = block,
            readOnly = readOnly,
            onDismiss = { tableDialog = false },
            onDelete = {
                tableDialog = false
                onRemove()
            },
            onCellTextChange = onTableCellTextChange,
            onRowSettingsChange = onTableRowSettingsChange,
            onColumnSettingsChange = onTableColumnSettingsChange,
            onCellSettingsChange = onTableCellSettingsChange,
            onInsertRowBelow = onInsertRowBelow,
            onInsertColumnRight = onInsertColumnRight,
            onDeleteRow = onDeleteRow,
            onDeleteColumn = onDeleteColumn,
        )
    }
}

@Composable
private fun TableSettingsDialog(
    block: TemplateBlock.Table,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onCellTextChange: (row: Int, column: Int, value: String) -> Unit,
    onRowSettingsChange: (row: Int, settings: TableAxisSettings) -> Unit,
    onColumnSettingsChange: (column: Int, settings: TableAxisSettings) -> Unit,
    onCellSettingsChange: (row: Int, column: Int, settings: TableCellSettings) -> Unit,
    onInsertRowBelow: (row: Int) -> Unit,
    onInsertColumnRight: (column: Int) -> Unit,
    onDeleteRow: (row: Int) -> Unit,
    onDeleteColumn: (column: Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Táblázat beállításai") },
        text = {
            TableBlockEditor(
                block = block,
                readOnly = readOnly,
                onCellTextChange = onCellTextChange,
                onRowSettingsChange = onRowSettingsChange,
                onColumnSettingsChange = onColumnSettingsChange,
                onCellSettingsChange = onCellSettingsChange,
                onInsertRowBelow = onInsertRowBelow,
                onInsertColumnRight = onInsertColumnRight,
                onDeleteRow = onDeleteRow,
                onDeleteColumn = onDeleteColumn,
            )
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            OutlinedButton(onClick = onDelete, enabled = !readOnly) {
                Text(stringResource(R.string.template_editor_block_delete))
            }
        },
    )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            label = { Text("Cella szövege") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { draftText += PAGE_NUMBER_TOKEN }) {
                            Text("Oldalszám")
                        }
                    }
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

private const val PAGE_NUMBER_TOKEN = "{{oldalszam}}"

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
private fun ContainerBlockLabel(
    title: String,
    blocks: List<TemplateBlock>,
    readOnly: Boolean,
    onTextChange: (blockId: String, value: String) -> Unit,
    onTextSettingsChange: (blockId: String, settings: TableCellSettings) -> Unit,
    onDateSettingsChange: (blockId: String, settings: TableCellSettings) -> Unit,
    onPageNumberSettingsChange: (blockId: String, settings: TableCellSettings) -> Unit,
    onDeleteBlock: (blockId: String) -> Unit,
    onMoveBlockUp: (blockId: String) -> Unit,
    onMoveBlockDown: (blockId: String) -> Unit,
    onTableCellTextChange: (tableId: String, row: Int, column: Int, value: String) -> Unit,
    onTableRowSettingsChange: (tableId: String, row: Int, settings: TableAxisSettings) -> Unit,
    onTableColumnSettingsChange: (tableId: String, column: Int, settings: TableAxisSettings) -> Unit,
    onTableCellSettingsChange: (tableId: String, row: Int, column: Int, settings: TableCellSettings) -> Unit,
    onInsertRowBelow: (tableId: String, row: Int) -> Unit,
    onInsertColumnRight: (tableId: String, column: Int) -> Unit,
    onDeleteRow: (tableId: String, row: Int) -> Unit,
    onDeleteColumn: (tableId: String, column: Int) -> Unit,
) {
    var styleDialog by remember(title) { mutableStateOf<Pair<String, TableCellSettings>?>(null) }
    var styleConfirm by remember(title) { mutableStateOf<(String, TableCellSettings) -> Unit>({ _, _ -> }) }
    var tableDialog by remember(title) { mutableStateOf<TemplateBlock.Table?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall)
        if (blocks.isEmpty()) {
            Text("Üres", style = MaterialTheme.typography.bodySmall)
        } else {
            blocks.forEach { block ->
                val label = when (block) {
                    is TemplateBlock.Text -> "Szöveg"
                    is TemplateBlock.Date -> "Dátum"
                    is TemplateBlock.Table -> "Táblázat: ${block.rows.coerceIn(1, 50)} sor × ${block.columns.coerceIn(1, 20)} oszlop"
                    is TemplateBlock.Signature -> "Aláírás"
                    is TemplateBlock.Stamp -> "Bélyegző"
                    is TemplateBlock.Images -> "Fotók"
                    is TemplateBlock.Image -> "Kép"
                    is TemplateBlock.PageBreak -> "Oldaltörés"
                    is TemplateBlock.PageNumber -> "Oldalszám"
                    is TemplateBlock.Header -> "Fejléc"
                    is TemplateBlock.Footer -> "Lábléc"
                    is TemplateBlock.Html -> "HTML / táblázatos rész"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (block is TemplateBlock.Text) {
                        OutlinedTextField(
                            value = block.text,
                            onValueChange = { onTextChange(block.id, it) },
                            label = { Text(label) },
                            enabled = !readOnly,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text("• $label", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                    if (!readOnly) {
                        when (block) {
                            is TemplateBlock.Text -> IconButton(onClick = { styleConfirm = onTextSettingsChange; styleDialog = block.id to block.settings }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                            is TemplateBlock.Date -> IconButton(onClick = { styleConfirm = onDateSettingsChange; styleDialog = block.id to block.settings }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                            is TemplateBlock.PageNumber -> IconButton(onClick = { styleConfirm = onPageNumberSettingsChange; styleDialog = block.id to block.settings }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                            is TemplateBlock.Table -> IconButton(onClick = { tableDialog = block }) { Icon(Icons.Filled.Settings, contentDescription = "Beállítások") }
                            else -> Unit
                        }
                        IconButton(onClick = { onMoveBlockUp(block.id) }) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.template_editor_block_move_up))
                        }
                        IconButton(onClick = { onMoveBlockDown(block.id) }) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.template_editor_block_move_down))
                        }
                    }
                }
            }
        }
    }
    styleDialog?.let { (blockId, settings) ->
        StyledBlockSettingsDialog(
            settings = settings,
            onDismiss = { styleDialog = null },
            onDelete = {
                styleDialog = null
                onDeleteBlock(blockId)
            },
            onConfirm = {
                styleDialog = null
                styleConfirm(blockId, it)
            },
        )
    }
    tableDialog?.let { table ->
        TableSettingsDialog(
            block = table,
            readOnly = readOnly,
            onDismiss = { tableDialog = null },
            onDelete = {
                tableDialog = null
                onDeleteBlock(table.id)
            },
            onCellTextChange = { row, column, value -> onTableCellTextChange(table.id, row, column, value) },
            onRowSettingsChange = { row, settings -> onTableRowSettingsChange(table.id, row, settings) },
            onColumnSettingsChange = { column, settings -> onTableColumnSettingsChange(table.id, column, settings) },
            onCellSettingsChange = { row, column, settings -> onTableCellSettingsChange(table.id, row, column, settings) },
            onInsertRowBelow = { row -> onInsertRowBelow(table.id, row) },
            onInsertColumnRight = { column -> onInsertColumnRight(table.id, column) },
            onDeleteRow = { row -> onDeleteRow(table.id, row) },
            onDeleteColumn = { column -> onDeleteColumn(table.id, column) },
        )
    }
}

@Composable
private fun StyledSettingsButton(readOnly: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !readOnly) { Text("Szín és igazítás") }
}

@Composable
private fun StyledBlockSettingsDialog(
    settings: TableCellSettings,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onConfirm: (TableCellSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Szín és igazítás") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSettingButtons(
                    backgroundColor = draft.backgroundColor,
                    textColor = draft.textColor,
                    onBackgroundChange = { draft = draft.copy(backgroundColor = it) },
                    onTextColorChange = { draft = draft.copy(textColor = it) },
                )
                TextAlignPicker(value = draft.textAlign, onChange = { draft = draft.copy(textAlign = it) })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(draft) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.template_editor_block_delete)) }
                OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
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
