package hu.gc.jegyzokonyv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DraftEntity::class, TemplateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "jegyzokonyv.db"
    }
}
