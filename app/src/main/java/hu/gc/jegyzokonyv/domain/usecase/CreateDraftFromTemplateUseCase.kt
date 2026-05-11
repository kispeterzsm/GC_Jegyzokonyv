package hu.gc.jegyzokonyv.domain.usecase

import hu.gc.jegyzokonyv.data.repo.DraftRepository
import javax.inject.Inject

class CreateDraftFromTemplateUseCase @Inject constructor(
    private val draftRepository: DraftRepository,
) {
    suspend operator fun invoke(templateId: String, title: String): String =
        draftRepository.createDraftFromTemplate(templateId, title)
}
