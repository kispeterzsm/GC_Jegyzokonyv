package hu.gc.jegyzokonyv.data.repo

import hu.gc.jegyzokonyv.data.db.TemplateDao
import hu.gc.jegyzokonyv.data.template.AssetTemplateProvider
import hu.gc.jegyzokonyv.domain.model.Template
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepositoryImpl @Inject constructor(
    private val templateDao: TemplateDao,
    private val assetTemplateProvider: AssetTemplateProvider,
) : TemplateRepository {

    override fun observeTemplates(): Flow<List<Template>> =
        templateDao.observeAll().map { list ->
            list.map { Template(id = it.id, name = it.name, isBuiltIn = it.isBuiltIn) }
        }

    override suspend fun loadHtml(templateId: String): String? {
        val entity = templateDao.getById(templateId) ?: return null
        val assetPath = entity.assetPath
        if (assetPath != null) return assetTemplateProvider.readTemplate(assetPath)
        val filePath = entity.filePath ?: return null
        return runCatching { File(filePath).readText(Charsets.UTF_8) }.getOrNull()
    }
}
