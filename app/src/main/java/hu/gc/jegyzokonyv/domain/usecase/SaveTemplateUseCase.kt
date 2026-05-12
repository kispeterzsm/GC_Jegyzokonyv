package hu.gc.jegyzokonyv.domain.usecase

import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import javax.inject.Inject

class SaveTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository,
) {
    suspend operator fun invoke(
        existingId: String?,
        name: String,
        content: TemplateContent,
        fallbackName: String,
    ): String {
        val finalName = name.trim().ifBlank { fallbackName }
        val normalized = content.copy(title = content.title.trim())
        return if (existingId.isNullOrBlank()) {
            templateRepository.createUserTemplate(finalName, normalized)
        } else {
            templateRepository.updateUserTemplate(existingId, finalName, normalized)
            existingId
        }
    }
}
