package hu.gc.jegyzokonyv.ui.nav

object Routes {
    const val HOME = "home"
    const val TEMPLATES = "templates"
    const val EDITOR = "editor/{draftId}"
    const val CAMERA = "camera/{draftId}"

    fun editor(draftId: String) = "editor/$draftId"
    fun camera(draftId: String) = "camera/$draftId"

    const val ARG_DRAFT_ID = "draftId"
    const val RESULT_KEY_IMAGE_PATH = "pendingPhotoPath"
}
