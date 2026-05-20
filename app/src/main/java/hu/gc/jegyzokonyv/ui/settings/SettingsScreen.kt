package hu.gc.jegyzokonyv.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.gc.jegyzokonyv.R
import hu.gc.jegyzokonyv.data.profile.ProfileImageKind
import coil.compose.AsyncImage
import coil.request.ImageRequest
import hu.gc.jegyzokonyv.ui.home.UpdateDialog
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProfile: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onProfile, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_title))
            }

            TextButton(onClick = viewModel::checkForUpdate, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_check_update))
            }
        }
    }

    UpdateDialog(
        state = updateState,
        onInstall = viewModel::downloadAndInstallUpdate,
        onOpenSettings = { viewModel.openInstallSettings(context::startActivity) },
        onRetryInstall = viewModel::retryInstall,
        onDismiss = viewModel::dismissUpdateMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val storedProfile by viewModel.profile.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(storedProfile) }
    var picking by remember { mutableStateOf(ProfileImageKind.Signature) }
    var editing by remember { mutableStateOf<ProfileImageKind?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.saveImage(uri, picking, draft)
    }

    LaunchedEffect(storedProfile) { draft = storedProfile }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileField(draft.name, R.string.profile_name) { draft = draft.copy(name = it) }
            ProfileField(draft.companyName, R.string.profile_company) { draft = draft.copy(companyName = it) }
            ProfileField(draft.phone, R.string.profile_phone) { draft = draft.copy(phone = it) }
            ProfileField(draft.email, R.string.profile_email) { draft = draft.copy(email = it) }
            ProfileImageRow(
                title = stringResource(R.string.profile_signature),
                imagePath = draft.signaturePath,
                canEdit = draft.signatureOriginalPath.isNotBlank(),
                onSelect = {
                    picking = ProfileImageKind.Signature
                    imagePicker.launch("image/*")
                },
                onEdit = { editing = ProfileImageKind.Signature },
            )
            ProfileImageRow(
                title = stringResource(R.string.profile_stamp),
                imagePath = draft.stampPath,
                canEdit = draft.stampOriginalPath.isNotBlank(),
                onSelect = {
                    picking = ProfileImageKind.Stamp
                    imagePicker.launch("image/*")
                },
                onEdit = { editing = ProfileImageKind.Stamp },
            )
            Button(onClick = { viewModel.save(draft, onSaved) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    editing?.let { kind ->
        ImageEditDialog(
            kind = kind,
            imagePath = if (kind == ProfileImageKind.Signature) draft.signaturePath else draft.stampPath,
            initialTolerance = if (kind == ProfileImageKind.Signature) draft.signatureTransparency else draft.stampTransparency,
            onValueChange = { tolerance -> viewModel.editImage(kind, tolerance, draft) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ProfileImageRow(
    title: String,
    imagePath: String,
    canEdit: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title)
        ProfileImagePreview(imagePath = imagePath, modifier = Modifier.fillMaxWidth().height(140.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSelect) { Text(stringResource(R.string.profile_select_image)) }
            Button(onClick = onEdit, enabled = canEdit) { Text(stringResource(R.string.profile_edit_image)) }
        }
    }
}

@Composable
private fun ProfileImagePreview(imagePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = imagePath.takeIf { it.isNotBlank() }?.let(::File)
    Box(
        modifier = modifier
            .background(Color(0xFFEFEFEF))
            .border(1.dp, Color(0xFFCCCCCC)),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null && file.isFile) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    .memoryCacheKey("${file.absolutePath}:${file.lastModified()}")
                    .diskCacheKey("${file.absolutePath}:${file.lastModified()}")
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = stringResource(R.string.profile_not_selected),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ImageEditDialog(
    kind: ProfileImageKind,
    imagePath: String,
    initialTolerance: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var tolerance by remember(kind) { mutableStateOf(initialTolerance.coerceIn(0f, 100f)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onValueChange(tolerance)
                onDismiss()
            }) { Text("OK") }
        },
        title = { Text(stringResource(R.string.profile_edit_image)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileImagePreview(imagePath = imagePath, modifier = Modifier.fillMaxWidth().height(220.dp))
                Text(stringResource(R.string.profile_transparency_slider))
                LaunchedEffect(tolerance) {
                    delay(150)
                    onValueChange(tolerance)
                }
                Slider(
                    value = tolerance,
                    onValueChange = { tolerance = it },
                    onValueChangeFinished = { onValueChange(tolerance) },
                    valueRange = 0f..100f,
                )
                Text("${tolerance.toInt()}%")
            }
        },
    )
}

@Composable
private fun ProfileField(value: String, labelRes: Int, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}
