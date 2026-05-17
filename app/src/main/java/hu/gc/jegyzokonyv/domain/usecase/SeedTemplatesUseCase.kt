package hu.gc.jegyzokonyv.domain.usecase

import hu.gc.jegyzokonyv.data.db.TemplateDao
import hu.gc.jegyzokonyv.data.db.TemplateEntity
import javax.inject.Inject

class SeedTemplatesUseCase @Inject constructor(
    private val templateDao: TemplateDao,
) {
    suspend operator fun invoke() {
        builtInTemplates.forEach { templateDao.upsert(it) }
    }

    companion object {
        const val BUILTIN_INSPECTION_ID = "builtin-inspection"
        const val BUILTIN_WORK_SAFETY_WALKTHROUGH_ID = "builtin-work-safety-walkthrough"

        private val builtInTemplates = listOf(
            TemplateEntity(
                id = BUILTIN_INSPECTION_ID,
                name = "Helyszíni szemle",
                title = "Helyszíni szemle",
                assetPath = "templates/inspection.json",
                filePath = null,
                isBuiltIn = true,
            ),
            TemplateEntity(
                id = BUILTIN_WORK_SAFETY_WALKTHROUGH_ID,
                name = "Munkavédelmi bejárás",
                title = "Munkavédelmi bejárás",
                assetPath = "templates/work_safety_walkthrough.json",
                filePath = null,
                isBuiltIn = true,
            ),
        )
    }
}
