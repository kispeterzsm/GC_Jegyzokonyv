package hu.gc.jegyzokonyv.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.gc.jegyzokonyv.R
import hu.gc.jegyzokonyv.domain.model.Draft
import hu.gc.jegyzokonyv.ui.common.ConfirmDialog
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewDraft: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<Draft?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewDraft,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_new_draft)) },
            )
        },
    ) { padding ->
        if (drafts.isEmpty()) {
            EmptyState(padding)
        } else {
            DraftList(
                drafts = drafts,
                padding = padding,
                onOpen = onOpenDraft,
                onLongPress = { pendingDelete = it },
            )
        }
    }

    UpdateDialog(
        state = updateState,
        onInstall = viewModel::downloadAndInstallUpdate,
        onOpenSettings = { viewModel.openInstallSettings(context::startActivity) },
        onRetryInstall = viewModel::retryInstall,
        onDismiss = viewModel::dismissUpdateMessage,
    )

    val toDelete = pendingDelete
    if (toDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_confirm_delete_title),
            message = stringResource(R.string.dialog_confirm_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                viewModel.deleteDraft(toDelete.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onInstall: (hu.gc.jegyzokonyv.data.update.GithubRelease) -> Unit,
    onOpenSettings: () -> Unit,
    onRetryInstall: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> Unit
        UpdateUiState.Checking -> ProgressDialog(stringResource(R.string.update_checking))
        UpdateUiState.Downloading -> ProgressDialog(stringResource(R.string.update_downloading))
        UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            },
            title = { Text(stringResource(R.string.home_check_update)) },
            text = { Text(stringResource(R.string.update_up_to_date)) },
        )
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { onInstall(state.release) }) {
                    Text(stringResource(R.string.update_install))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }
            },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_available_message, state.release.versionName)) },
        )
        is UpdateUiState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.update_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { onRetryInstall(state.apkFile) }) {
                    Text(stringResource(R.string.update_install))
                }
            },
            title = { Text(stringResource(R.string.update_install_permission_title)) },
            text = { Text(stringResource(R.string.update_install_permission_message)) },
        )
        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            },
            title = { Text(stringResource(R.string.home_check_update)) },
            text = { Text(stringResource(R.string.update_failed, state.message)) },
        )
    }
}

@Composable
private fun ProgressDialog(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(message) },
        text = { CircularProgressIndicator() },
    )
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.home_empty),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DraftList(
    drafts: List<Draft>,
    padding: PaddingValues,
    onOpen: (String) -> Unit,
    onLongPress: (Draft) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(drafts, key = { it.id }) { draft ->
            DraftRow(draft, onOpen = onOpen, onLongPress = onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraftRow(
    draft: Draft,
    onOpen: (String) -> Unit,
    onLongPress: (Draft) -> Unit,
) {
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .combinedClickable(
                onClick = { onOpen(draft.id) },
                onLongClick = { onLongPress(draft) },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = draft.title.ifBlank { "—" },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = df.format(Date(draft.updatedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
