package hu.gc.jegyzokonyv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.repo.DraftRepository
import hu.gc.jegyzokonyv.data.update.AppUpdateManager
import hu.gc.jegyzokonyv.data.update.GithubRelease
import hu.gc.jegyzokonyv.data.update.UpdateCheckResult
import hu.gc.jegyzokonyv.domain.model.Draft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.io.File
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val draftRepository: DraftRepository,
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {

    val drafts: StateFlow<List<Draft>> = draftRepository.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    fun deleteDraft(id: String) {
        viewModelScope.launch { draftRepository.deleteDraft(id) }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            _updateState.value = runCatching { appUpdateManager.checkForUpdate() }
                .fold(
                    onSuccess = { result ->
                        when (result) {
                            UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                            is UpdateCheckResult.UpdateAvailable -> UpdateUiState.Available(result.release)
                        }
                    },
                    onFailure = { UpdateUiState.Failed(it.localizedMessage ?: it.toString()) },
                )
        }
    }

    fun downloadAndInstallUpdate(release: GithubRelease) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading
            _updateState.value = runCatching { appUpdateManager.downloadUpdate(release) }
                .fold(
                    onSuccess = { apkFile ->
                        if (appUpdateManager.canInstallPackages()) {
                            appUpdateManager.installApk(apkFile)
                            UpdateUiState.Idle
                        } else {
                            UpdateUiState.NeedsInstallPermission(apkFile)
                        }
                    },
                    onFailure = { UpdateUiState.Failed(it.localizedMessage ?: it.toString()) },
                )
        }
    }

    fun openInstallSettings(startActivity: (android.content.Intent) -> Unit) {
        startActivity(appUpdateManager.createInstallPermissionIntent())
    }

    fun retryInstall(apkFile: File) {
        if (appUpdateManager.canInstallPackages()) {
            appUpdateManager.installApk(apkFile)
            _updateState.value = UpdateUiState.Idle
        } else {
            _updateState.value = UpdateUiState.NeedsInstallPermission(apkFile)
        }
    }

    fun dismissUpdateMessage() {
        _updateState.value = UpdateUiState.Idle
    }
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object Downloading : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: GithubRelease) : UpdateUiState
    data class NeedsInstallPermission(val apkFile: File) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}
