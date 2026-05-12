package hu.gc.jegyzokonyv.data.repo

import hu.gc.jegyzokonyv.data.db.TemplateDao
import hu.gc.jegyzokonyv.data.db.TemplateEntity
import hu.gc.jegyzokonyv.data.file.FileStorage
import hu.gc.jegyzokonyv.data.template.AssetTemplateProvider
import hu.gc.jegyzokonyv.data.template.TemplateContentCodec
import hu.gc.jegyzokonyv.di.IoDispatcher
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val templateDao: TemplateDao,
    private val assetTemplateProvider: AssetTemplateProvider,
    private val fileStorage: FileStorage,
    @IoDispatcher private val io: CoroutineDispatcher,
) : TemplateRepository {

    override fun observeTemplates(): Flow<List<Template>> =
        templateDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTemplate(id: String): Template? = withContext(io) {
        templateDao.getById(id)?.toDomain()
    }

    override suspend fun loadContent(templateId: String): TemplateContent? = withContext(io) {
        val entity = templateDao.getById(templateId) ?: return@withContext null
        val raw = readTemplateJson(entity) ?: return@withContext null
        runCatching { TemplateContentCodec.decode(raw) }.getOrNull()
    }

    override suspend fun starterContent(): TemplateContent = withContext(io) {
        TemplateContent(title = "", blocks = emptyList())
    }

    override suspend fun createUserTemplate(name: String, content: TemplateContent): String =
        withContext(io) {
            val id = UUID.randomUUID().toString()
            val file = fileStorage.userTemplateFile(id)
            file.parentFile?.mkdirs()
            file.writeText(TemplateContentCodec.encode(content), Charsets.UTF_8)
            templateDao.upsert(
                TemplateEntity(
                    id = id,
                    name = name,
                    title = content.title,
                    assetPath = null,
                    filePath = file.absolutePath,
                    isBuiltIn = false,
                )
            )
            id
        }

    override suspend fun updateUserTemplate(id: String, name: String, content: TemplateContent) =
        withContext(io) {
            val existing = templateDao.getById(id) ?: error("Template not found: $id")
            require(!existing.isBuiltIn) { "Cannot edit built-in template: $id" }
            val file = existing.filePath?.let(::File) ?: fileStorage.userTemplateFile(id)
            file.parentFile?.mkdirs()
            file.writeText(TemplateContentCodec.encode(content), Charsets.UTF_8)
            templateDao.upsert(
                existing.copy(
                    name = name,
                    title = content.title,
                    filePath = file.absolutePath,
                )
            )
        }

    override suspend fun deleteUserTemplate(id: String) = withContext(io) {
        val existing = templateDao.getById(id) ?: return@withContext
        require(!existing.isBuiltIn) { "Cannot delete built-in template: $id" }
        templateDao.deleteUserTemplate(id)
        fileStorage.deleteUserTemplateFile(id)
    }

    private fun readTemplateJson(entity: TemplateEntity): String? {
        val assetPath = entity.assetPath
        if (assetPath != null) return assetTemplateProvider.readTemplate(assetPath)
        val filePath = entity.filePath ?: return null
        return runCatching { File(filePath).readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun TemplateEntity.toDomain() = Template(
        id = id,
        name = name,
        title = title,
        isBuiltIn = isBuiltIn,
    )
}
