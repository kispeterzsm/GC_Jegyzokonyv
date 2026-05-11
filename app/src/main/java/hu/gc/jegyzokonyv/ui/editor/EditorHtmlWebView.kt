package hu.gc.jegyzokonyv.ui.editor

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun EditorHtmlWebView(
    html: String,
    draftDir: File,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                @Suppress("DEPRECATION")
                settings.allowFileAccess = true
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = false
                settings.blockNetworkLoads = true
                webViewClient = WebViewClient()
                setBackgroundColor(0x00000000)
            }
        },
        update = { view ->
            val baseUrl = "file://${draftDir.absolutePath}/"
            view.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
        },
    )
}
