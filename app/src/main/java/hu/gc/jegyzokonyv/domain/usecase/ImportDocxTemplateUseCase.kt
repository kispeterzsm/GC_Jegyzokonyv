package hu.gc.jegyzokonyv.domain.usecase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.data.template.DocxTemplateConverter
import hu.gc.jegyzokonyv.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ImportDocxTemplateUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val converter: DocxTemplateConverter,
    private val templateRepository: TemplateRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend operator fun invoke(uri: Uri): String = withContext(io) {
        val name = displayName(uri).substringBeforeLast('.').trim().ifBlank { "Importált sablon" }
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            converter.convert(stream, title = name)
        } ?: error("A DOCX fájl nem nyitható meg.")
        templateRepository.createUserTemplate(name = name, content = content)
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index).orEmpty()
            }
        }
        return uri.lastPathSegment.orEmpty()
    }
}
