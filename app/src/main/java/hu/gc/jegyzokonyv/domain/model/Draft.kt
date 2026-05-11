package hu.gc.jegyzokonyv.domain.model

data class Draft(
    val id: String,
    val title: String,
    val templateId: String,
    val createdAt: Long,
    val updatedAt: Long,
)
