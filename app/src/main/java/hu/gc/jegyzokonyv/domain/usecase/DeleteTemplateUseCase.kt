package hu.gc.jegyzokonyv.domain.usecase

import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import javax.inject.Inject

class DeleteTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository,
) {
    suspend operator fun invoke(templateId: String) {
        templateRepository.deleteUserTemplate(templateId)
    }
}
