package hu.gc.jegyzokonyv.data.file

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileStorage {

    override val draftsRoot: File
        get() = File(context.filesDir, "drafts").also { it.mkdirs() }

    override fun draftDir(draftId: String): File =
        File(draftsRoot, draftId).also { it.mkdirs() }

    override fun imagesDir(draftId: String): File =
        File(draftDir(draftId), "images").also { it.mkdirs() }

    override fun documentHtml(draftId: String): File =
        File(draftDir(draftId), "document.html")

    override fun metadataJson(draftId: String): File =
        File(draftDir(draftId), "metadata.json")

    override fun exportPdf(draftId: String): File =
        File(draftDir(draftId), "export.pdf")

    override fun newImageFile(draftId: String): File {
        val dir = imagesDir(draftId)
        val highest = dir.listFiles()
            ?.mapNotNull { IMAGE_REGEX.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            ?.maxOrNull()
            ?: 0
        val next = highest + 1
        return File(dir, "img_%03d.jpg".format(next))
    }

    override fun deleteDraft(draftId: String) {
        draftDir(draftId).deleteRecursively()
    }

    private companion object {
        val IMAGE_REGEX = Regex("""img_(\d+)\.jpg""")
    }
}
