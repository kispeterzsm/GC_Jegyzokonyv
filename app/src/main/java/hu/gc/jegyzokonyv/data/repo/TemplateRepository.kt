package hu.gc.jegyzokonyv.data.repo

import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun observeTemplates(): Flow<List<Template>>
    suspend fun getTemplate(id: String): Template?
    suspend fun loadContent(templateId: String): TemplateContent?
    suspend fun starterContent(): TemplateContent
    suspend fun createUserTemplate(name: String, content: TemplateContent): String
    suspend fun updateUserTemplate(id: String, name: String, content: TemplateContent)
    suspend fun deleteUserTemplate(id: String)
}
