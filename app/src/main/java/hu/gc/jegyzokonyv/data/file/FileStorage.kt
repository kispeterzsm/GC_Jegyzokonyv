package hu.gc.jegyzokonyv.data.file

import java.io.File

interface FileStorage {
    val draftsRoot: File
    fun draftDir(draftId: String): File
    fun imagesDir(draftId: String): File
    fun documentHtml(draftId: String): File
    fun metadataJson(draftId: String): File
    fun exportPdf(draftId: String): File
    fun newImageFile(draftId: String): File
    fun deleteDraft(draftId: String)
}
