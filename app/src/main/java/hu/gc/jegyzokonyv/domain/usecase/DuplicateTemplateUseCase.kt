package hu.gc.jegyzokonyv.domain.usecase

import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import java.util.UUID
import javax.inject.Inject

class DuplicateTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository,
) {
    suspend operator fun invoke(sourceId: String, copySuffix: String): String {
        val source = templateRepository.getTemplate(sourceId)
            ?: error("Template not found: $sourceId")
        val content = templateRepository.loadContent(sourceId)
            ?: error("Template content not found: $sourceId")
        val rebuiltBlocks = content.blocks.map { block ->
            val newId = UUID.randomUUID().toString()
            when (block) {
                is TemplateBlock.Text -> TemplateBlock.Text(id = newId, text = block.text)
                is TemplateBlock.Date -> TemplateBlock.Date(id = newId)
                is TemplateBlock.Table -> block.copy(id = newId)
                is TemplateBlock.Signature -> TemplateBlock.Signature(id = newId)
                is TemplateBlock.Stamp -> TemplateBlock.Stamp(id = newId)
            }
        }
        val newName = "${source.name} $copySuffix".trim()
        return templateRepository.createUserTemplate(
            name = newName,
            content = content.copy(blocks = rebuiltBlocks),
        )
    }
}
