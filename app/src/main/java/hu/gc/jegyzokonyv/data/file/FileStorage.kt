package hu.gc.jegyzokonyv.data.file

import java.io.File

interface FileStorage {
    val draftsRoot: File
    val templatesRoot: File
    fun draftDir(draftId: String): File
    fun imagesDir(draftId: String): File
    fun documentHtml(draftId: String): File
    fun metadataJson(draftId: String): File
    fun exportPdf(draftId: String, filename: String): File
    fun latestExportedPdf(draftId: String): File?
    fun deleteExportedPdfs(draftId: String)
    fun newImageFile(draftId: String): File
    fun deleteDraft(draftId: String)
    fun userTemplateFile(templateId: String): File
    fun deleteUserTemplateFile(templateId: String)
}
