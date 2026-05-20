package hu.gc.jegyzokonyv.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.profile.ProfileImageKind
import hu.gc.jegyzokonyv.data.profile.ProfileRepository
import hu.gc.jegyzokonyv.data.profile.UserProfile
import hu.gc.jegyzokonyv.data.update.AppUpdateManager
import hu.gc.jegyzokonyv.data.update.GithubRelease
import hu.gc.jegyzokonyv.data.update.UpdateCheckResult
import hu.gc.jegyzokonyv.ui.home.UpdateUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {
    val profile: StateFlow<UserProfile> = profileRepository.profile

    private var imageEditJob: Job? = null

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    fun save(profile: UserProfile, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            profileRepository.save(profile)
            onSaved()
        }
    }

    fun saveImage(uri: Uri, kind: ProfileImageKind, current: UserProfile) {
        viewModelScope.launch {
            val (originalPath, editedPath) = profileRepository.saveImage(uri, kind)
            val updated = when (kind) {
                ProfileImageKind.Signature -> current.copy(
                    signaturePath = editedPath,
                    signatureOriginalPath = originalPath,
                )
                ProfileImageKind.Stamp -> current.copy(
                    stampPath = editedPath,
                    stampOriginalPath = originalPath,
                )
            }
            profileRepository.save(updated)
        }
    }

    fun editImage(kind: ProfileImageKind, tolerance: Float, current: UserProfile) {
        imageEditJob?.cancel()
        imageEditJob = viewModelScope.launch {
            runCatching { profileRepository.editImage(kind, tolerance) }
                .onSuccess { editedPath ->
                    val latest = profileRepository.profile.value
                    val updated = when (kind) {
                        ProfileImageKind.Signature -> latest.copy(signaturePath = editedPath)
                        ProfileImageKind.Stamp -> latest.copy(stampPath = editedPath)
                    }
                    profileRepository.save(updated)
                }
        }
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

    fun dismissUpdateMessage() { _updateState.value = UpdateUiState.Idle }
}
