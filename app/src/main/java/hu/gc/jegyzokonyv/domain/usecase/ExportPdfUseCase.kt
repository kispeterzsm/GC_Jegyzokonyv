package hu.gc.jegyzokonyv.domain.usecase

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
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
        val documentName = sanitizeFileName(draft.title.ifBlank { "jegyzokonyv" })
        val output = draftRepository.exportPdfTarget(draftId, "$documentName.pdf")
        draftRepository.deleteExportedPdfs(draftId)

        return withTimeout(PDF_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                renderHtmlToPdf(
                    html = html,
                    baseUrl = "file://${draftDir.absolutePath}/",
                    output = output,
                )
            }
        }
    }

    private suspend fun renderHtmlToPdf(
        html: String,
        baseUrl: String,
        output: File,
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

        var done = false
        fun finishOnce(block: () -> Unit) {
            if (done) return
            done = true
            block()
            runCatching { webView.destroy() }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.postDelayed({
                    runCatching { drawToPdf(view, output) }
                        .onSuccess { finishOnce { cont.resume(it) } }
                        .onFailure { finishOnce { cont.resumeWithException(it) } }
                }, IMAGE_SETTLE_DELAY_MS)
            }
        }

        cont.invokeOnCancellation { finishOnce {} }
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
    }

    private fun drawToPdf(view: WebView, output: File): File {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val contentHeight = view.measuredHeight.coerceAtLeast(PAGE_HEIGHT_PX)
        view.layout(0, 0, PAGE_WIDTH_PX, contentHeight)

        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val pdf = PdfDocument()
        try {
            val pageCount = ((contentHeight + PAGE_HEIGHT_PX - 1) / PAGE_HEIGHT_PX).coerceAtLeast(1)
            for (i in 0 until pageCount) {
                val info = PdfDocument.PageInfo
                    .Builder(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, i + 1)
                    .create()
                val page = pdf.startPage(info)
                page.canvas.save()
                page.canvas.translate(0f, -(i * PAGE_HEIGHT_PX).toFloat())
                view.draw(page.canvas)
                page.canvas.restore()
                pdf.finishPage(page)
            }
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return output
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(64).ifBlank { "jegyzokonyv" }

    private companion object {
        const val PDF_TIMEOUT_MS = 30_000L
        const val IMAGE_SETTLE_DELAY_MS = 100L
        // A4 at 200 DPI: 8.27 in x 11.69 in
        const val PAGE_WIDTH_PX = 1654
        const val PAGE_HEIGHT_PX = 2338
    }
}
