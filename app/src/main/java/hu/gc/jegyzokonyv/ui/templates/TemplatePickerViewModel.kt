package hu.gc.jegyzokonyv.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.usecase.CreateDraftFromTemplateUseCase
import hu.gc.jegyzokonyv.domain.usecase.DeleteTemplateUseCase
import hu.gc.jegyzokonyv.domain.usecase.DuplicateTemplateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatePickerViewModel @Inject constructor(
    templateRepository: TemplateRepository,
    private val createDraft: CreateDraftFromTemplateUseCase,
    private val deleteTemplate: DeleteTemplateUseCase,
    private val duplicateTemplate: DuplicateTemplateUseCase,
) : ViewModel() {

    val templates: StateFlow<List<Template>> = templateRepository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createDraft(templateId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = createDraft.invoke(templateId)
            onCreated(id)
        }
    }

    fun duplicate(sourceId: String, copySuffix: String, onDuplicated: (String) -> Unit) {
        viewModelScope.launch {
            val newId = duplicateTemplate(sourceId, copySuffix)
            onDuplicated(newId)
        }
    }

    fun delete(templateId: String) {
        viewModelScope.launch {
            deleteTemplate(templateId)
        }
    }
}
