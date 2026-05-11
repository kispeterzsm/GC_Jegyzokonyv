package hu.gc.jegyzokonyv.domain.usecase

import hu.gc.jegyzokonyv.data.db.TemplateDao
import hu.gc.jegyzokonyv.data.db.TemplateEntity
import javax.inject.Inject

class SeedTemplatesUseCase @Inject constructor(
    private val templateDao: TemplateDao,
) {
    suspend operator fun invoke() {
        if (templateDao.countBuiltIn() > 0) return
        templateDao.upsert(
            TemplateEntity(
                id = BUILTIN_INSPECTION_ID,
                name = "Helyszíni szemle",
                assetPath = "templates/inspection.html",
                filePath = null,
                isBuiltIn = true,
            )
        )
    }

    companion object {
        const val BUILTIN_INSPECTION_ID = "builtin-inspection"
    }
}
