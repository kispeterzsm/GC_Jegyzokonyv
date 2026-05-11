package hu.gc.jegyzokonyv.domain.usecase

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class SharePdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(pdf: File, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(context, AUTHORITY, pdf)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val AUTHORITY = "hu.gc.jegyzokonyv.fileprovider"
    }
}
