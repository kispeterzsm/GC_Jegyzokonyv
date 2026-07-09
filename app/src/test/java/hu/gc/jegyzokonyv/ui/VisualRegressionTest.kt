package hu.gc.jegyzokonyv.ui

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import hu.gc.jegyzokonyv.data.profile.ProfileImageKind
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.data.template.DocxTemplateConverter
import hu.gc.jegyzokonyv.data.update.AppUpdateManager
import hu.gc.jegyzokonyv.domain.model.Draft
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.model.TemplateBlock
import hu.gc.jegyzokonyv.domain.model.TemplateContent
import hu.gc.jegyzokonyv.domain.usecase.CreateDraftFromTemplateUseCase
import hu.gc.jegyzokonyv.domain.usecase.DeleteTemplateUseCase
import hu.gc.jegyzokonyv.domain.usecase.DuplicateTemplateUseCase
import hu.gc.jegyzokonyv.domain.usecase.ImportDocxTemplateUseCase
import hu.gc.jegyzokonyv.ui.home.HomeScreen
import hu.gc.jegyzokonyv.ui.home.HomeViewModel
import hu.gc.jegyzokonyv.ui.home.UpdateDialog
import hu.gc.jegyzokonyv.ui.home.UpdateUiState
import hu.gc.jegyzokonyv.ui.settings.ProfileScreen
import hu.gc.jegyzokonyv.ui.settings.SettingsScreen
import hu.gc.jegyzokonyv.ui.settings.SettingsViewModel
import hu.gc.jegyzokonyv.ui.templates.TemplateEditorTopBar
import hu.gc.jegyzokonyv.ui.templates.TemplatePickerScreen
import hu.gc.jegyzokonyv.ui.templates.TemplatePickerViewModel
import hu.gc.jegyzokonyv.ui.theme.JegyzokonyvTheme
import java.io.File
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalRoborazziApi::class)
class VisualRegressionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var previousLocale: Locale
    private lateinit var previousTimeZone: TimeZone

    @Before
    fun setDeterministicLocale() {
        previousLocale = Locale.getDefault()
        previousTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale("hu", "HU"))
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Budapest"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun homeScreenWithDraftsMatchesBaseline() {
        val viewModel = HomeViewModel(
            draftRepository = FakeDraftRepository(
                drafts = listOf(
                    Draft("draft-1", "Daruzási jegyzőkönyv", "template-1", FIXED_TIME, FIXED_TIME),
                    Draft("draft-2", "Telephelyi bejárás", "template-2", FIXED_TIME, FIXED_TIME),
                    Draft("draft-3", "Villamos ellenőrzés", "template-3", FIXED_TIME, FIXED_TIME),
                ),
            ),
            appUpdateManager = appUpdateManager(),
        )

        setPhoneContent {
            HomeScreen(
                onNewDraft = {},
                onOpenDraft = {},
                onSettings = {},
                viewModel = viewModel,
            )
        }

        capturePhone("home_screen_with_drafts")
    }

    @Test
    fun templatePickerMatchesBaseline() {
        val templateRepository = FakeTemplateRepository(
            templates = listOf(
                Template("built-in-1", "Helyszíni szemle", "Helyszíni szemle", isBuiltIn = true),
                Template("built-in-2", "Munkavédelmi bejárás", "Munkavédelmi bejárás", isBuiltIn = true),
                Template("user-1", "Saját fotódokumentáció", "", isBuiltIn = false),
            ),
        )
        val viewModel = TemplatePickerViewModel(
            templateRepository = templateRepository,
            createDraft = CreateDraftFromTemplateUseCase(FakeDraftRepository(createResult = "created-draft")),
            deleteTemplate = DeleteTemplateUseCase(templateRepository),
            duplicateTemplate = DuplicateTemplateUseCase(templateRepository),
            importDocxTemplate = ImportDocxTemplateUseCase(
                context = context(),
                converter = DocxTemplateConverter(),
                templateRepository = templateRepository,
                io = Dispatchers.Unconfined,
            ),
        )

        setPhoneContent {
            TemplatePickerScreen(
                onDraftCreated = {},
                onNewTemplate = {},
                onEditTemplate = {},
                onBack = {},
                viewModel = viewModel,
            )
        }

        capturePhone("template_picker")
    }

    @Test
    fun settingsScreenMatchesBaseline() {
        val viewModel = SettingsViewModel(
            profileRepository = FakeProfileRepository(UserProfile()),
            appUpdateManager = appUpdateManager(),
        )

        setPhoneContent {
            SettingsScreen(
                onBack = {},
                onProfile = {},
                viewModel = viewModel,
            )
        }

        capturePhone("settings_screen")
    }

    @Test
    fun profileScreenMatchesBaseline() {
        val viewModel = SettingsViewModel(
            profileRepository = FakeProfileRepository(
                UserProfile(
                    name = "Kiss Péter",
                    companyName = "GC Mérnökiroda Kft.",
                    phone = "+36 30 123 4567",
                    email = "peter.kiss@example.com",
                ),
            ),
            appUpdateManager = appUpdateManager(),
        )

        setPhoneContent {
            ProfileScreen(
                onBack = {},
                onSaved = {},
                viewModel = viewModel,
            )
        }

        capturePhone("profile_screen")
    }

    @Test
    fun editorToolbarStatesMatchBaseline() {
        setPhoneContent {
            Column(modifier = Modifier.fillMaxSize()) {
                TemplateEditorTopBar(
                    title = "Sablon szerkesztése",
                    canSave = true,
                    onBack = {},
                    onSave = {},
                )
                TemplateEditorTopBar(
                    title = "Beépített sablon",
                    canSave = false,
                    onBack = {},
                    onSave = {},
                )
            }
        }

        capturePhone("editor_toolbar_states")
    }

    @Test
    fun updateErrorDialogMatchesBaseline() {
        setPhoneContent {
            UpdateDialog(
                state = UpdateUiState.Failed("Hálózati kapcsolat nem érhető el."),
                onInstall = {},
                onOpenSettings = {},
                onRetryInstall = {},
                onDismiss = {},
            )
        }

        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/update_error_dialog.png")
    }

    private fun setPhoneContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            JegyzokonyvTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(width = PHONE_WIDTH, height = PHONE_HEIGHT)
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                            .testTag(PHONE_TAG),
                    ) {
                        content()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun capturePhone(name: String) {
        composeRule.onNodeWithTag(PHONE_TAG)
            .captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun appUpdateManager(
        context: Context = context(),
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = AppUpdateManager(context, dispatcher)

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private class FakeDraftRepository(
        drafts: List<Draft> = emptyList(),
        private val createResult: String = "created-draft",
    ) : DraftRepository {
        private val draftsFlow = MutableStateFlow(drafts)

        override fun observeDrafts(): Flow<List<Draft>> = draftsFlow
        override fun observeDraft(draftId: String): Flow<Draft?> =
            MutableStateFlow(draftsFlow.value.firstOrNull { it.id == draftId })

        override suspend fun getDraft(draftId: String): Draft? =
            draftsFlow.value.firstOrNull { it.id == draftId }

        override suspend fun createDraftFromTemplate(templateId: String): String = createResult
        override suspend fun loadHtml(draftId: String): String = ""
        override suspend fun saveHtml(draftId: String, html: String) = Unit
        override suspend fun setTitle(draftId: String, title: String) = Unit
        override suspend fun appendPhotoBlock(draftId: String, relativeImagePath: String, caption: String?) = Unit
        override suspend fun deleteDraft(draftId: String) {
            draftsFlow.value = draftsFlow.value.filterNot { it.id == draftId }
        }
        override fun draftDir(draftId: String): File = error("unused")
        override fun newImageFile(draftId: String): File = error("unused")
        override fun exportPdfTarget(draftId: String, filename: String): File = error("unused")
        override fun latestExportedPdf(draftId: String): File? = null
        override fun deleteExportedPdfs(draftId: String) = Unit
    }

    private class FakeTemplateRepository(
        templates: List<Template> = emptyList(),
        private val starter: TemplateContent = TemplateContent(
            title = "",
            blocks = listOf(TemplateBlock.Images("template-images")),
        ),
    ) : TemplateRepository {
        private val templatesFlow = MutableStateFlow(templates)
        private val contentById = templates.associate {
            it.id to TemplateContent(
                title = it.title,
                blocks = listOf(TemplateBlock.Text("text-${it.id}", it.name), TemplateBlock.Images("images-${it.id}")),
            )
        }.toMutableMap()

        override fun observeTemplates(): Flow<List<Template>> = templatesFlow
        override suspend fun getTemplate(id: String): Template? = templatesFlow.value.firstOrNull { it.id == id }
        override suspend fun loadContent(templateId: String): TemplateContent? = contentById[templateId]
        override suspend fun starterContent(): TemplateContent = starter
        override suspend fun createUserTemplate(name: String, content: TemplateContent): String {
            val id = "template-${templatesFlow.value.size + 1}"
            templatesFlow.value = templatesFlow.value + Template(id, name, content.title, isBuiltIn = false)
            contentById[id] = content
            return id
        }
        override suspend fun updateUserTemplate(id: String, name: String, content: TemplateContent) {
            templatesFlow.value = templatesFlow.value.map {
                if (it.id == id) it.copy(name = name, title = content.title) else it
            }
            contentById[id] = content
        }
        override suspend fun deleteUserTemplate(id: String) {
            templatesFlow.value = templatesFlow.value.filterNot { it.id == id }
            contentById.remove(id)
        }
    }

    private class FakeProfileRepository(profile: UserProfile) : ProfileRepository {
        override val profile = MutableStateFlow(profile)

        override suspend fun save(profile: UserProfile) {
            this.profile.value = profile
        }
        override suspend fun saveImage(uri: Uri, kind: ProfileImageKind): Pair<String, String> = error("unused")
        override suspend fun editImage(kind: ProfileImageKind, tolerance: Float): String = error("unused")
        override fun imageFile(path: String): File? = null
    }

    private companion object {
        const val PHONE_TAG = "phone-root"
        val PHONE_WIDTH = 411.dp
        val PHONE_HEIGHT = 891.dp
        const val FIXED_TIME = 1_704_109_200_000L
    }
}
