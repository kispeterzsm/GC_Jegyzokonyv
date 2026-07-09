package hu.gc.jegyzokonyv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val title: String,
    val templateId: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "")
    val folderPath: String,
)
