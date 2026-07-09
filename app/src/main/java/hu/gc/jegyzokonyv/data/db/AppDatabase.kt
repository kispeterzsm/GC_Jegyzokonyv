package hu.gc.jegyzokonyv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DraftEntity::class, TemplateEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun draftDao(): DraftDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "jegyzokonyv.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE drafts ADD COLUMN folderPath TEXT NOT NULL DEFAULT ''")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
    }
}
