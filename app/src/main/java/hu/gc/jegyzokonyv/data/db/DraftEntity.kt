package hu.gc.jegyzokonyv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val title: String,
    val templateId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val folderPath: String,
)
