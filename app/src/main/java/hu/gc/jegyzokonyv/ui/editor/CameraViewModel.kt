package hu.gc.jegyzokonyv.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.ui.nav.Routes
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draftRepository: DraftRepository,
) : ViewModel() {

    val draftId: String = savedStateHandle.get<String>(Routes.ARG_DRAFT_ID).orEmpty()

    fun allocateImageFile(): File = draftRepository.newImageFile(draftId)

    fun relativePath(file: File): String = "images/${file.name}"
}
