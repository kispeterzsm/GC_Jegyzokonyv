package hu.gc.jegyzokonyv.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.gc.jegyzokonyv.R
import hu.gc.jegyzokonyv.domain.model.TemplateBlock

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
            items(state.blocks, key = { it.id }) { block ->
                BlockRow(
                    block = block,
                    readOnly = state.isReadOnly,
                    onTextChange = { value -> viewModel.onTextBlockChange(block.id, value) },
                    onMoveUp = { viewModel.moveBlockUp(block.id) },
                    onMoveDown = { viewModel.moveBlockDown(block.id) },
                    onRemove = { viewModel.removeBlock(block.id) },
                )
            }
            if (!state.isReadOnly) {
                item("add-row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AssistChip(
                            onClick = viewModel::addTextBlock,
                            label = { Text(stringResource(R.string.template_editor_add_text_block)) },
                        )
                        AssistChip(
                            onClick = viewModel::addDateBlock,
                            label = { Text(stringResource(R.string.template_editor_add_date_block)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    block: TemplateBlock,
    readOnly: Boolean,
    onTextChange: (String) -> Unit,
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
