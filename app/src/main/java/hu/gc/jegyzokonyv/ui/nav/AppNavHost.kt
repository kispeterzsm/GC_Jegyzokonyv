package hu.gc.jegyzokonyv.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import hu.gc.jegyzokonyv.ui.editor.CameraCaptureScreen
import hu.gc.jegyzokonyv.ui.editor.DocumentEditorScreen
import hu.gc.jegyzokonyv.ui.editor.PdfPreviewScreen
import hu.gc.jegyzokonyv.ui.home.HomeScreen
import hu.gc.jegyzokonyv.ui.settings.ProfileScreen
import hu.gc.jegyzokonyv.ui.settings.SettingsScreen
import hu.gc.jegyzokonyv.ui.templates.TemplateEditorScreen
import hu.gc.jegyzokonyv.ui.templates.TemplatePickerScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onNewDraft = { navController.navigate(Routes.TEMPLATES) },
                onOpenDraft = { id -> navController.navigate(Routes.editor(id)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onProfile = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack(Routes.SETTINGS, inclusive = false) },
            )
        }

        composable(Routes.TEMPLATES) {
            TemplatePickerScreen(
                onDraftCreated = { draftId ->
                    navController.navigate(Routes.editor(draftId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onNewTemplate = { navController.navigate(Routes.TEMPLATE_NEW) },
                onEditTemplate = { id -> navController.navigate(Routes.templateEdit(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TEMPLATE_NEW) {
            TemplateEditorScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.TEMPLATE_EDIT,
            arguments = listOf(navArgument(Routes.ARG_TEMPLATE_ID) { type = NavType.StringType }),
        ) {
            TemplateEditorScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.EDITOR,
            arguments = listOf(navArgument(Routes.ARG_DRAFT_ID) { type = NavType.StringType }),
        ) { entry ->
            val draftId = entry.arguments?.getString(Routes.ARG_DRAFT_ID).orEmpty()
            DocumentEditorScreen(
                draftId = draftId,
                onTakePhoto = {
                    navController.navigate(Routes.camera(draftId))
                },
                onBack = { navController.popBackStack() },
                onDeleted = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onPdfExported = {
                    navController.navigate(Routes.pdfPreview(draftId))
                },
                navController = navController,
            )
        }

        composable(
            Routes.PDF_PREVIEW,
            arguments = listOf(navArgument(Routes.ARG_DRAFT_ID) { type = NavType.StringType }),
        ) {
            PdfPreviewScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            Routes.CAMERA,
            arguments = listOf(navArgument(Routes.ARG_DRAFT_ID) { type = NavType.StringType }),
        ) { entry ->
            val draftId = entry.arguments?.getString(Routes.ARG_DRAFT_ID).orEmpty()
            CameraCaptureScreen(
                draftId = draftId,
                onCaptured = { relativePath ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Routes.RESULT_KEY_IMAGE_PATH, relativePath)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
