package hu.gc.jegyzokonyv.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val title: String,
    val assetPath: String?,
    val filePath: String?,
    val isBuiltIn: Boolean,
)
