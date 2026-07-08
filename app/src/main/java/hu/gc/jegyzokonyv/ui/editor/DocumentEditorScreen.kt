package hu.gc.jegyzokonyv.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import hu.gc.jegyzokonyv.R
import hu.gc.jegyzokonyv.ui.common.ConfirmDialog
import hu.gc.jegyzokonyv.ui.nav.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    @Suppress("UNUSED_PARAMETER") draftId: String,
    onTakePhoto: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onPdfExported: () -> Unit,
    navController: NavController,
    viewModel: DocumentEditorViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val html by viewModel.html.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var focusedEditableCell by remember { mutableStateOf(false) }
    var dictatedText by remember { mutableStateOf<String?>(null) }
    var dictationToken by remember { mutableStateOf(0) }
    var scrollToBottomToken by remember { mutableStateOf(0) }

    val exportFailMsg = stringResource(R.string.editor_export_failed)
    val isSafetyWalkthrough = html.contains("safety-walkthrough")
    val dictationUnavailableMsg = stringResource(R.string.editor_dictation_unavailable)
    val dictationPermissionMsg = stringResource(R.string.editor_dictation_permission)
    val dictationFailedMsg = stringResource(R.string.editor_dictation_failed)
    val dictationNoCellMsg = stringResource(R.string.editor_dictation_no_cell)
    var isListening by remember { mutableStateOf(false) }
    var lastPartialDictation by remember { mutableStateOf("") }

    val onDictationResult by rememberUpdatedState<(String) -> Unit> { text ->
        if (text.isNotBlank()) {
            dictatedText = text
            dictationToken += 1
        }
    }
    val onDictationError by rememberUpdatedState<(String) -> Unit> { message ->
        isListening = false
        if (lastPartialDictation.isNotBlank()) {
            onDictationResult(lastPartialDictation)
            lastPartialDictation = ""
        } else {
            snackbarScope.launch { snackbarHostState.showSnackbar(message) }
        }
    }
    val speechRecognizer = rememberHungarianSpeechRecognizer(
        onReady = {
            lastPartialDictation = ""
            isListening = true
        },
        onPartialText = { text -> lastPartialDictation = text },
        onFinalText = { text ->
            isListening = false
            val recognizedText = text.ifBlank { lastPartialDictation }
            lastPartialDictation = ""
            onDictationResult(recognizedText)
        },
        onError = { onDictationError(dictationFailedMsg) },
    )
    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer?.destroy() }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (speechRecognizer == null) {
                snackbarScope.launch { snackbarHostState.showSnackbar(dictationUnavailableMsg) }
            } else {
                speechRecognizer.startListening(hungarianSpeechIntent())
            }
        } else {
            snackbarScope.launch { snackbarHostState.showSnackbar(dictationPermissionMsg) }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.ExportFinished -> onPdfExported()
                is EditorEvent.ExportFailed -> {
                    val reason = event.reason
                    val msg = if (!reason.isNullOrBlank()) {
                        context.getString(R.string.editor_export_failed_reason, reason)
                    } else {
                        exportFailMsg
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry, html) {
        val savedStateHandle = currentEntry?.savedStateHandle ?: return@LaunchedEffect
        val pending = savedStateHandle.get<String>(Routes.RESULT_KEY_IMAGE_PATH)
        if (pending != null) {
            if (html.isBlank()) return@LaunchedEffect
            savedStateHandle.remove<String>(Routes.RESULT_KEY_IMAGE_PATH)
            if (isSafetyWalkthrough) {
                scrollToBottomToken += 1
            }
            viewModel.onPhotoCaptured(pending, null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = draft?.title ?: "",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.editor_title_hint))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_export_pdf)) },
                                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onExportPdf()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomActionBar(
                onAddPhoto = onTakePhoto,
                isDictating = isListening,
                onDictate = {
                    if (isListening) {
                        speechRecognizer?.stopListening()
                    } else if (!focusedEditableCell) {
                        snackbarScope.launch { snackbarHostState.showSnackbar(dictationNoCellMsg) }
                    } else if (speechRecognizer == null) {
                        snackbarScope.launch { snackbarHostState.showSnackbar(dictationUnavailableMsg) }
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        speechRecognizer.startListening(hungarianSpeechIntent())
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            EditorHtmlWebView(
                html = html,
                draftDir = viewModel.draftFolder(),
                onHtmlChanged = viewModel::onDocumentHtmlChanged,
                onEditableCellFocusedChanged = { focusedEditableCell = it },
                dictatedText = dictatedText,
                dictationToken = dictationToken,
                scrollToBottomToken = scrollToBottomToken,
                modifier = Modifier.fillMaxSize(),
            )
            if (isExporting) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.size(16.dp))
                        Text(stringResource(R.string.editor_export_in_progress))
                    }
                }
            }
        }
    }

    if (showRename) {
        AddTextDialog(
            title = stringResource(R.string.editor_title_hint),
            label = stringResource(R.string.editor_title_hint),
            confirmLabel = stringResource(R.string.action_save),
            initialValue = draft?.title.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { newTitle ->
                showRename = false
                viewModel.onTitleChange(newTitle)
            },
        )
    }

    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_confirm_delete_title),
            message = stringResource(R.string.dialog_confirm_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                showDelete = false
                viewModel.onDelete(onDeleted)
            },
            onDismiss = { showDelete = false },
        )
    }
}

@Composable
private fun BottomActionBar(
    onAddPhoto: () -> Unit,
    isDictating: Boolean,
    onDictate: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onAddPhoto,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.Camera, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                EditorButtonText(stringResource(R.string.editor_add_photo))
            }
            OutlinedButton(
                onClick = onDictate,
                colors = if (isDictating) {
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            ) {
                Icon(
                    if (isDictating) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                EditorButtonText(
                    stringResource(
                        if (isDictating) {
                            R.string.editor_dictation_listening
                        } else {
                            R.string.editor_dictate
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun rememberHungarianSpeechRecognizer(
    onReady: () -> Unit,
    onPartialText: (String) -> Unit,
    onFinalText: (String) -> Unit,
    onError: () -> Unit,
): SpeechRecognizer? {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnPartialText by rememberUpdatedState(onPartialText)
    val currentOnFinalText by rememberUpdatedState(onFinalText)
    val currentOnError by rememberUpdatedState(onError)
    return remember(context) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            null
        } else {
            runCatching {
                SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            currentOnReady()
                        }

                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit

                        override fun onError(error: Int) {
                            Log.w(DICTATION_LOG_TAG, "Speech recognition error: $error")
                            currentOnError()
                        }

                        override fun onResults(results: Bundle?) {
                            val text = bestSpeechResult(results)
                            Log.d(DICTATION_LOG_TAG, "Speech recognition result: $text")
                            currentOnFinalText(text)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val text = bestSpeechResult(partialResults)
                            if (text.isNotBlank()) {
                                Log.d(DICTATION_LOG_TAG, "Speech recognition partial: $text")
                                currentOnPartialText(text)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }
            }.getOrNull()
        }
    }
}

@Composable
private fun EditorButtonText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val DICTATION_LOG_TAG = "JegyzokonyvDictation"

private fun hungarianSpeechIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hu-HU")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
    }

private fun bestSpeechResult(results: Bundle?): String =
    results
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        .orEmpty()
