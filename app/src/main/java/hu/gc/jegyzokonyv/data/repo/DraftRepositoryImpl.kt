package hu.gc.jegyzokonyv.data.repo

import hu.gc.jegyzokonyv.data.db.DraftDao
import hu.gc.jegyzokonyv.data.db.DraftEntity
import hu.gc.jegyzokonyv.data.file.FileStorage
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.di.IoDispatcher
import hu.gc.jegyzokonyv.domain.html.HtmlEngine
import hu.gc.jegyzokonyv.domain.model.Draft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepositoryImpl @Inject constructor(
    private val draftDao: DraftDao,
    private val fileStorage: FileStorage,
    private val htmlEngine: HtmlEngine,
    private val templateRepository: TemplateRepository,
    private val profileRepository: ProfileRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DraftRepository {

    private val writeMutex = Mutex()

    override fun observeDrafts(): Flow<List<Draft>> =
        draftDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeDraft(draftId: String): Flow<Draft?> =
        draftDao.observeById(draftId).map { it?.toDomain() }

    override suspend fun getDraft(draftId: String): Draft? = withContext(io) {
        draftDao.getById(draftId)?.toDomain()
    }

    override suspend fun createDraftFromTemplate(templateId: String): String =
        withContext(io) {
            val content = templateRepository.loadContent(templateId)
                ?: error("Template not found: $templateId")
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val todayIso = dateFormatter().format(Date(now))
            val title = buildDraftTitle(content.title, todayIso)
            val profile = profileRepository.profile.value
            val rendered = htmlEngine.renderTemplate(content, title, todayIso, profile)
            val initial = rendered
                .replace("Generálkivitelező: Gépész Centrál Kft.", "Generálkivitelező: ${profile.companyName}")
                .replace("Kispéter Ákosné", profile.name)

            fileStorage.draftDir(id)
            fileStorage.imagesDir(id)
            fileStorage.documentHtml(id).writeText(initial, Charsets.UTF_8)
            writeMetadata(id, title, templateId, now, now)

            draftDao.upsert(
                DraftEntity(
                    id = id,
                    title = title,
                    templateId = templateId,
                    createdAt = now,
                    updatedAt = now,
                    folderPath = fileStorage.draftDir(id).absolutePath,
                )
            )
            id
        }

    override suspend fun loadHtml(draftId: String): String = withContext(io) {
        val file = fileStorage.documentHtml(draftId)
        if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    override suspend fun saveHtml(draftId: String, html: String) = withContext(io) {
        writeMutex.withLock {
            fileStorage.documentHtml(draftId).writeText(html, Charsets.UTF_8)
            touch(draftId)
        }
    }

    override suspend fun setTitle(draftId: String, title: String) = withContext(io) {
        writeMutex.withLock {
            val current = readHtml(draftId)
            val updated = htmlEngine.setTitle(current, title)
            fileStorage.documentHtml(draftId).writeText(updated, Charsets.UTF_8)
            val entity = draftDao.getById(draftId) ?: return@withLock
            val now = System.currentTimeMillis()
            draftDao.update(entity.copy(title = title, updatedAt = now))
            writeMetadata(draftId, title, entity.templateId, entity.createdAt, now)
        }
    }

    override suspend fun appendPhotoBlock(
        draftId: String,
        relativeImagePath: String,
        caption: String?,
    ) = withContext(io) {
        writeMutex.withLock {
            val current = readHtml(draftId)
            val updated = htmlEngine.appendPhotoBlock(current, relativeImagePath, caption)
            fileStorage.documentHtml(draftId).writeText(updated, Charsets.UTF_8)
            touch(draftId)
        }
    }

    override suspend fun appendTextBlock(draftId: String, text: String) = withContext(io) {
        writeMutex.withLock {
            val current = readHtml(draftId)
            val updated = htmlEngine.appendTextBlock(current, text)
            fileStorage.documentHtml(draftId).writeText(updated, Charsets.UTF_8)
            touch(draftId)
        }
    }

    override suspend fun deleteDraft(draftId: String) = withContext(io) {
        writeMutex.withLock {
            draftDao.deleteById(draftId)
            fileStorage.deleteDraft(draftId)
        }
    }

    override fun draftDir(draftId: String): File = fileStorage.draftDir(draftId)
    override fun newImageFile(draftId: String): File = fileStorage.newImageFile(draftId)
    override fun exportPdfTarget(draftId: String, filename: String): File =
        fileStorage.exportPdf(draftId, filename)
    override fun latestExportedPdf(draftId: String): File? =
        fileStorage.latestExportedPdf(draftId)
    override fun deleteExportedPdfs(draftId: String) =
        fileStorage.deleteExportedPdfs(draftId)

    private fun readHtml(draftId: String): String {
        val file = fileStorage.documentHtml(draftId)
        return if (file.exists()) file.readText(Charsets.UTF_8) else ""
    }

    private suspend fun touch(draftId: String) {
        val entity = draftDao.getById(draftId) ?: return
        val now = System.currentTimeMillis()
        draftDao.update(entity.copy(updatedAt = now))
        writeMetadata(draftId, entity.title, entity.templateId, entity.createdAt, now)
    }

    private fun writeMetadata(
        draftId: String,
        title: String,
        templateId: String,
        createdAt: Long,
        updatedAt: Long,
    ) {
        val json = JSONObject().apply {
            put("id", draftId)
            put("title", title)
            put("templateId", templateId)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("version", 1)
        }
        fileStorage.metadataJson(draftId).writeText(json.toString(2), Charsets.UTF_8)
    }

    private fun buildDraftTitle(templateTitle: String, todayIso: String): String {
        val base = templateTitle.trim().ifBlank { DEFAULT_TITLE }
        return "$base $todayIso"
    }

    private fun dateFormatter() = SimpleDateFormat(DATE_PATTERN, Locale("hu", "HU"))

    private fun DraftEntity.toDomain() = Draft(
        id = id,
        title = title,
        templateId = templateId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val DATE_PATTERN = "yyyy-MM-dd"
        const val DEFAULT_TITLE = "Jegyzőkönyv"
    }
}
