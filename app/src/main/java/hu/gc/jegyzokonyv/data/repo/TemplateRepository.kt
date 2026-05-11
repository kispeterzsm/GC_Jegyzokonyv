package hu.gc.jegyzokonyv.data.repo

import hu.gc.jegyzokonyv.domain.model.Template
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun observeTemplates(): Flow<List<Template>>
    suspend fun loadHtml(templateId: String): String?
}
