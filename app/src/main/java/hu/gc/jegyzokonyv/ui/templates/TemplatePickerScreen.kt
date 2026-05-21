package hu.gc.jegyzokonyv.ui.templates

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.gc.jegyzokonyv.R
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.ui.common.ConfirmDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerScreen(
    onDraftCreated: (String) -> Unit,
    onNewTemplate: () -> Unit,
    onEditTemplate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: TemplatePickerViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val docxImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importDocx(it, onImported = onEditTemplate) }
    }
    var pendingActions by remember { mutableStateOf<Template?>(null) }
    var pendingDelete by remember { mutableStateOf<Template?>(null) }
    val copySuffix = stringResource(R.string.template_default_copy_suffix)

    LaunchedEffect(viewModel) {
        viewModel.importErrors.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            docxImportLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/zip",
                                    "*/*",
                                )
                            )
                        },
                    ) {
                        Text(stringResource(R.string.templates_import_docx))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTemplate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.templates_new_template)) },
            )
        },
    ) { padding ->
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.templates_empty))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(templates, key = { it.id }) { template ->
                    TemplateRow(
                        template = template,
                        onClick = { viewModel.createDraft(template.id, onCreated = onDraftCreated) },
                        onLongPress = { pendingActions = template },
                    )
                }
            }
        }
    }

    pendingActions?.let { selected ->
        TemplateActionsSheet(
            template = selected,
            onDismiss = { pendingActions = null },
            onDuplicate = {
                pendingActions = null
                viewModel.duplicate(selected.id, copySuffix, onDuplicated = onEditTemplate)
            },
            onEdit = {
                pendingActions = null
                onEditTemplate(selected.id)
            },
            onDelete = {
                pendingActions = null
                pendingDelete = selected
            },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.template_action_delete_title),
            message = stringResource(R.string.template_action_delete_message),
            confirmLabel = stringResource(R.string.template_action_delete),
            onConfirm = {
                viewModel.delete(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateRow(
    template: Template,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (template.isBuiltIn) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.templates_built_in_badge)) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
            IconButton(onClick = onLongPress) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateActionsSheet(
    template: Template,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = template.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
        SheetAction(
            label = stringResource(R.string.template_action_duplicate),
            onClick = onDuplicate,
        )
        if (!template.isBuiltIn) {
            SheetAction(
                label = stringResource(R.string.template_action_edit),
                onClick = onEdit,
            )
            SheetAction(
                label = stringResource(R.string.template_action_delete),
                onClick = onDelete,
            )
        }
        Box(modifier = Modifier.padding(bottom = 12.dp))
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }
}
