package hu.gc.jegyzokonyv.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.gc.jegyzokonyv.data.repo.TemplateRepository
import hu.gc.jegyzokonyv.domain.model.Template
import hu.gc.jegyzokonyv.domain.usecase.CreateDraftFromTemplateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatePickerViewModel @Inject constructor(
    templateRepository: TemplateRepository,
    private val createDraft: CreateDraftFromTemplateUseCase,
) : ViewModel() {

    val templates: StateFlow<List<Template>> = templateRepository.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createDraft(templateId: String, title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = createDraft.invoke(templateId, title.trim().ifBlank { "Jegyzőkönyv" })
            onCreated(id)
        }
    }
}
