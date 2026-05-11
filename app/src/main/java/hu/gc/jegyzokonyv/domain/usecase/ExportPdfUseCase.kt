package hu.gc.jegyzokonyv.domain.usecase

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ExportPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val draftRepository: DraftRepository,
) {

    suspend operator fun invoke(draftId: String): File {
        val draft = draftRepository.getDraft(draftId)
            ?: error("Draft not found: $draftId")
        val html = draftRepository.loadHtml(draftId)
        val draftDir = draftRepository.draftDir(draftId)
        val output = draftRepository.exportPdfFile(draftId)

        return withTimeout(PDF_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                renderHtmlToPdf(
                    html = html,
                    baseUrl = "file://${draftDir.absolutePath}/",
                    output = output,
                    documentName = sanitizeFileName(draft.title.ifBlank { "jegyzokonyv" }),
                )
            }
        }
    }

    private suspend fun renderHtmlToPdf(
        html: String,
        baseUrl: String,
        output: File,
        documentName: String,
    ): File = suspendCancellableCoroutine { cont ->
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = false
            @Suppress("DEPRECATION")
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = false
            settings.loadsImagesAutomatically = true
            settings.blockNetworkLoads = true
        }

        val state = ExportState(cont, webView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.postDelayed({
                    runCatching { startPrint(view, output, documentName, state) }
                        .onFailure { state.finishFailure(it) }
                }, 250)
            }
        }

        cont.invokeOnCancellation { state.cancel() }
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
    }

    private fun startPrint(
        webView: WebView,
        output: File,
        documentName: String,
        state: ExportState,
    ) {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val adapter: PrintDocumentAdapter = webView.createPrintDocumentAdapter(documentName)
        state.adapter = adapter
        adapter.onStart()

        adapter.onLayout(
            null,
            attributes,
            state.cancellation,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    runCatching { writePdf(adapter, output, state) }
                        .onFailure { state.finishFailure(it) }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    state.finishFailure(IllegalStateException("Layout failed: $error"))
                }

                override fun onLayoutCancelled() {
                    state.finishFailure(IllegalStateException("Layout cancelled"))
                }
            },
            Bundle(),
        )
    }

    private fun writePdf(
        adapter: PrintDocumentAdapter,
        output: File,
        state: ExportState,
    ) {
        val pfd = ParcelFileDescriptor.open(
            output,
            ParcelFileDescriptor.MODE_READ_WRITE
                or ParcelFileDescriptor.MODE_CREATE
                or ParcelFileDescriptor.MODE_TRUNCATE,
        )
        state.pfd = pfd

        adapter.onWrite(
            arrayOf(PageRange.ALL_PAGES),
            pfd,
            state.cancellation,
            object : PrintDocumentAdapter.WriteResultCallback() {
                override fun onWriteFinished(pages: Array<out PageRange>?) {
                    state.finishSuccess(output)
                }

                override fun onWriteFailed(error: CharSequence?) {
                    state.finishFailure(IllegalStateException("Write failed: $error"))
                }

                override fun onWriteCancelled() {
                    state.finishFailure(IllegalStateException("Write cancelled"))
                }
            },
        )
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(64).ifBlank { "jegyzokonyv" }

    private class ExportState(
        private val cont: kotlinx.coroutines.CancellableContinuation<File>,
        private val webView: WebView,
    ) {
        val cancellation = CancellationSignal()
        var adapter: PrintDocumentAdapter? = null
        var pfd: ParcelFileDescriptor? = null
        private var done = false

        fun finishSuccess(file: File) {
            if (done) return
            done = true
            cleanup()
            cont.resume(file)
        }

        fun finishFailure(error: Throwable) {
            if (done) return
            done = true
            cleanup()
            cont.resumeWithException(error)
        }

        fun cancel() {
            if (done) return
            done = true
            cancellation.cancel()
            cleanup()
        }

        private fun cleanup() {
            runCatching { adapter?.onFinish() }
            runCatching { pfd?.close() }
            runCatching { webView.destroy() }
        }
    }

    private companion object {
        const val PDF_TIMEOUT_MS = 30_000L
    }
}
