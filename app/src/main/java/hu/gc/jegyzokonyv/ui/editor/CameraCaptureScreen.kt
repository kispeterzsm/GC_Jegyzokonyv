package hu.gc.jegyzokonyv.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import hu.gc.jegyzokonyv.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    draftId: String,
    onCaptured: (relativePath: String) -> Unit,
    onCancel: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted },
    )

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(85)
            .build()
    }
    val executor: Executor = remember { Executors.newSingleThreadExecutor() }

    var error by remember { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_add_photo)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        CameraButtonText(stringResource(R.string.action_cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            if (!hasPermission) {
                PermissionRationale(onRequest = { launcher.launch(Manifest.permission.CAMERA) })
            } else {
                CameraPreviewBox(imageCapture, lifecycleOwner)
                ShutterRow(
                    enabled = !capturing,
                    onShutter = {
                        if (capturing) return@ShutterRow
                        capturing = true
                        val outputFile = viewModel.allocateImageFile()
                        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                        scope.launch {
                            runCatching { takePicture(imageCapture, options, executor) }
                                .onSuccess { onCaptured(viewModel.relativePath(outputFile)) }
                                .onFailure {
                                    capturing = false
                                    error = it.message
                                }
                        }
                    },
                    paddingValues = padding,
                )
            }

            error?.let {
                Text(
                    text = it,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionRationale(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.camera_permission_required),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRequest,
            modifier = Modifier
                .padding(top = 16.dp)
                .heightIn(min = 56.dp),
        ) {
            CameraButtonText(stringResource(R.string.camera_permission_grant))
        }
    }
}

@Composable
private fun CameraButtonText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CameraPreviewBox(
    imageCapture: ImageCapture,
    lifecycleOwner: LifecycleOwner,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

@Composable
private fun BoxScope.ShutterRow(
    enabled: Boolean,
    onShutter: () -> Unit,
    paddingValues: PaddingValues,
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = paddingValues.calculateBottomPadding() + 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            onClick = onShutter,
            enabled = enabled,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
            modifier = Modifier.size(80.dp),
        ) {
            Icon(
                Icons.Filled.Camera,
                contentDescription = stringResource(R.string.camera_capture),
            )
        }
    }
}

private suspend fun takePicture(
    imageCapture: ImageCapture,
    options: ImageCapture.OutputFileOptions,
    executor: Executor,
) = suspendCancellableCoroutine { cont ->
    imageCapture.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                cont.resume(Unit)
            }
            override fun onError(exc: ImageCaptureException) {
                cont.resumeWithException(exc)
            }
        },
    )
}
