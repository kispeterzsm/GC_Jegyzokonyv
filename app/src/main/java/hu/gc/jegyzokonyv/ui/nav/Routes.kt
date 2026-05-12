package hu.gc.jegyzokonyv.ui.nav

object Routes {
    const val HOME = "home"
    const val TEMPLATES = "templates"
    const val EDITOR = "editor/{draftId}"
    const val CAMERA = "camera/{draftId}"
    const val TEMPLATE_NEW = "template/new"
    const val TEMPLATE_EDIT = "template/{templateId}/edit"

    fun editor(draftId: String) = "editor/$draftId"
    fun camera(draftId: String) = "camera/$draftId"
    fun templateEdit(templateId: String) = "template/$templateId/edit"

    const val ARG_DRAFT_ID = "draftId"
    const val ARG_TEMPLATE_ID = "templateId"
    const val RESULT_KEY_IMAGE_PATH = "pendingPhotoPath"
}
