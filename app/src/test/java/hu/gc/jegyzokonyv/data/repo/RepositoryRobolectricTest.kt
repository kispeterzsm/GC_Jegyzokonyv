package hu.gc.jegyzokonyv.data.repo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import hu.gc.jegyzokonyv.data.db.AppDatabase
import hu.gc.jegyzokonyv.data.file.FileStorage
import hu.gc.jegyzokonyv.data.profile.ProfileImageKind
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.data.template.AssetTemplateProvider
import hu.gc.jegyzokonyv.domain.html.HtmlEngine
import hu.gc.jegyzokonyv.domain.model.Draft
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RepositoryRobolectricTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun templateRepositoryPersistsObservesUpdatesAndDeletesUserTemplates() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = inMemoryDatabase(context)
        val storage = TestFileStorage(temporaryFolder.newFolder("files"))
        val repository = TemplateRepositoryImpl(
            templateDao = db.templateDao(),
            assetTemplateProvider = AssetTemplateProvider(context),
            fileStorage = storage,
            io = StandardTestDispatcher(testScheduler),
        )

        try {
            repository.observeTemplates().test {
                assertThat(awaitItem()).isEmpty()

                val id = repository.createUserTemplate(
                    name = "Felhasználói",
                    content = TemplateContent(title = "Cím", blocks = listOf(TemplateBlock.Text("text", "body"))),
                )

                assertThat(awaitItem().map { it.id }).containsExactly(id)
                assertThat(repository.getTemplate(id)).isEqualTo(Template(id, "Felhasználói", "Cím", isBuiltIn = false))
                assertThat(repository.loadContent(id)).isEqualTo(
                    TemplateContent(title = "Cím", blocks = listOf(TemplateBlock.Text("text", "body"))),
                )
                assertThat(storage.userTemplateFile(id).isFile).isTrue()

                repository.updateUserTemplate(
                    id = id,
                    name = "Átnevezett",
                    content = TemplateContent(title = "Új cím", blocks = listOf(TemplateBlock.Date("date"))),
                )

                assertThat(awaitItem().single()).isEqualTo(Template(id, "Átnevezett", "Új cím", isBuiltIn = false))
                assertThat(repository.loadContent(id)).isEqualTo(
                    TemplateContent(title = "Új cím", blocks = listOf(TemplateBlock.Date("date"))),
                )

                repository.deleteUserTemplate(id)

                assertThat(awaitItem()).isEmpty()
                assertThat(repository.getTemplate(id)).isNull()
                assertThat(storage.userTemplateFile(id).exists()).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun draftRepositoryCreatesDraftFilesUpdatesHtmlAppendsPhotosAndDeletesDrafts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = inMemoryDatabase(context)
        val storage = TestFileStorage(temporaryFolder.newFolder("draft-files"))
        val htmlEngine = RecordingHtmlEngine()
        val templateRepository = FakeTemplateRepository(
            template = Template("template", "Szemle", "Szemle", isBuiltIn = false),
            content = TemplateContent(title = "Template title", blocks = listOf(TemplateBlock.Text("text", "body"))),
        )
        val repository = DraftRepositoryImpl(
            draftDao = db.draftDao(),
            fileStorage = storage,
            htmlEngine = htmlEngine,
            templateRepository = templateRepository,
            profileRepository = FakeProfileRepository(UserProfile(name = "Tester")),
            io = StandardTestDispatcher(testScheduler),
        )

        try {
            val draftId = repository.createDraftFromTemplate("template")
            val draft = repository.getDraft(draftId)!!

            assertThat(draft.templateId).isEqualTo("template")
            assertThat(draft.title).startsWith("Szemle ")
            assertThat(storage.documentHtml(draftId).readText()).contains("rendered:Szemle")
            assertThat(storage.metadataJson(draftId).isFile).isTrue()
            assertThat(htmlEngine.lastRenderedContent!!.title).isEmpty()

            repository.saveHtml(draftId, "<html>saved</html>")
            assertThat(repository.loadHtml(draftId)).isEqualTo("<html>saved</html>")

            repository.setTitle(draftId, "Új cím")
            assertThat(repository.getDraft(draftId)!!.title).isEqualTo("Új cím")
            assertThat(repository.loadHtml(draftId)).contains("title:Új cím")

            repository.appendPhotoBlock(draftId, "images/img_001.jpg", "Felirat")
            assertThat(repository.loadHtml(draftId)).contains("photo:images/img_001.jpg:Felirat")

            repository.deleteDraft(draftId)
            assertThat(repository.getDraft(draftId)).isNull()
            assertThat(storage.draftDirRaw(draftId).exists()).isFalse()
        } finally {
            db.close()
        }
    }

    @Test
    fun migrationFromVersionOnePreservesDraftsAndAddsFolderPath() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test-${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(name)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE drafts (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    templateId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE templates (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    assetPath TEXT,
                    filePath TEXT,
                    isBuiltIn INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO drafts (id, title, templateId, createdAt, updatedAt) VALUES ('draft-1', 'Régi', 'template-1', 10, 20)",
            )
            db.version = 1
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val draft = migrated.draftDao().getByIdBlocking("draft-1")
            assertThat(draft!!.title).isEqualTo("Régi")
            assertThat(draft.folderPath).isEmpty()
        } finally {
            migrated.close()
            dbFile.delete()
        }
    }

    private fun inMemoryDatabase(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private suspend fun hu.gc.jegyzokonyv.data.db.DraftDao.getByIdBlocking(id: String) = getById(id)

    private class RecordingHtmlEngine : HtmlEngine {
        var lastRenderedContent: TemplateContent? = null

        override fun renderTemplate(content: TemplateContent, title: String, todayIso: String, profile: UserProfile?): String {
            lastRenderedContent = content
            return "<html>rendered:$title:$todayIso:${profile?.name}</html>"
        }

        override fun setTitle(html: String, title: String): String = "$html title:$title"
        override fun appendPhotoBlock(html: String, relativeImagePath: String, caption: String?): String =
            "$html photo:$relativeImagePath:$caption"
    }

    private class FakeTemplateRepository(
        private val template: Template,
        private val content: TemplateContent,
    ) : TemplateRepository {
        override fun observeTemplates(): Flow<List<Template>> = flowOf(listOf(template))
        override suspend fun getTemplate(id: String): Template? = template.takeIf { it.id == id }
        override suspend fun loadContent(templateId: String): TemplateContent? = content.takeIf { templateId == template.id }
        override suspend fun starterContent(): TemplateContent = TemplateContent(title = "", blocks = emptyList())
        override suspend fun createUserTemplate(name: String, content: TemplateContent): String = error("unused")
        override suspend fun updateUserTemplate(id: String, name: String, content: TemplateContent) = error("unused")
        override suspend fun deleteUserTemplate(id: String) = error("unused")
    }

    private class FakeProfileRepository(profile: UserProfile = UserProfile()) : ProfileRepository {
        override val profile = MutableStateFlow(profile)
        override suspend fun save(profile: UserProfile) {
            this.profile.value = profile
        }
        override suspend fun saveImage(uri: android.net.Uri, kind: ProfileImageKind): Pair<String, String> = error("unused")
        override suspend fun editImage(kind: ProfileImageKind, tolerance: Float): String = error("unused")
        override fun imageFile(path: String): File? = path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf(File::isFile)
    }

    private class TestFileStorage(private val root: File) : FileStorage {
        override val draftsRoot: File
            get() = File(root, "drafts").also { it.mkdirs() }
        override val templatesRoot: File
            get() = File(root, "templates").also { it.mkdirs() }

        override fun draftDir(draftId: String): File = draftDirRaw(draftId).also { it.mkdirs() }
        fun draftDirRaw(draftId: String): File = File(draftsRoot, draftId)
        override fun imagesDir(draftId: String): File = File(draftDir(draftId), "images").also { it.mkdirs() }
        override fun documentHtml(draftId: String): File = File(draftDir(draftId), "document.html")
        override fun metadataJson(draftId: String): File = File(draftDir(draftId), "metadata.json")
        override fun exportPdf(draftId: String, filename: String): File = File(draftDir(draftId), filename)
        override fun latestExportedPdf(draftId: String): File? =
            draftDir(draftId).listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }?.maxByOrNull { it.lastModified() }
        override fun deleteExportedPdfs(draftId: String) {
            draftDir(draftId).listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }?.forEach { it.delete() }
        }
        override fun newImageFile(draftId: String): File = File(imagesDir(draftId), "img_001.jpg")
        override fun deleteDraft(draftId: String) {
            draftDirRaw(draftId).deleteRecursively()
        }
        override fun userTemplateFile(templateId: String): File = File(templatesRoot, "$templateId.json")
        override fun deleteUserTemplateFile(templateId: String) {
            userTemplateFile(templateId).delete()
        }
    }
}
