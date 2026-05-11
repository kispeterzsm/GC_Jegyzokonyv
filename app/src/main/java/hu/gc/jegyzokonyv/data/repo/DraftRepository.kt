package hu.gc.jegyzokonyv.data.repo

import hu.gc.jegyzokonyv.domain.model.Draft
import kotlinx.coroutines.flow.Flow
import java.io.File

interface DraftRepository {
    fun observeDrafts(): Flow<List<Draft>>
    fun observeDraft(draftId: String): Flow<Draft?>
    suspend fun getDraft(draftId: String): Draft?

    suspend fun createDraftFromTemplate(templateId: String, title: String): String

    suspend fun loadHtml(draftId: String): String
    suspend fun saveHtml(draftId: String, html: String)

    suspend fun setTitle(draftId: String, title: String)
    suspend fun appendPhotoBlock(draftId: String, relativeImagePath: String, caption: String?)
    suspend fun appendTextBlock(draftId: String, text: String)

    suspend fun deleteDraft(draftId: String)

    fun draftDir(draftId: String): File
    fun newImageFile(draftId: String): File
    fun exportPdfFile(draftId: String): File
}
