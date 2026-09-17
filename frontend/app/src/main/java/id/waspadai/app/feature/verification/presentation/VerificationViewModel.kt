package id.waspadai.app.feature.verification.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryDetailUseCase
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryUseCase
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VerificationViewModel(
    private val submitTextVerification: SubmitTextVerificationUseCase,
    private val loadHistory: LoadVerificationHistoryUseCase,
    private val loadHistoryDetail: LoadVerificationHistoryDetailUseCase,
    isRemoteEnabled: Boolean
) : ViewModel() {
    private val _state = MutableStateFlow(VerificationUiState(isRemoteEnabled = isRemoteEnabled))
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    fun onAction(action: VerificationAction) {
        when (action) {
            is VerificationAction.InputChanged -> updateInput(action.value)
            VerificationAction.SubmitText -> submitText()
            VerificationAction.RequestImageCapture -> showImageUnavailable()
            VerificationAction.DismissFailure -> dismissFailure()
            VerificationAction.ToggleHistory -> toggleHistory()
            VerificationAction.RefreshHistory -> refreshHistory()
            is VerificationAction.OpenHistory -> openHistory(action.caseId)
        }
    }

    private fun updateInput(value: String) {
        _state.update { current -> current.copy(draft = value, phase = VerificationPhase.Idle) }
    }

    private fun submitText() {
        val text = state.value.draft.trim()
        if (text.length < MINIMUM_TEXT_LENGTH) {
            _state.update { current ->
                current.copy(phase = VerificationPhase.Failure("Masukkan minimal 10 karakter untuk diperiksa."))
            }
            return
        }
        _state.update { current -> current.copy(phase = VerificationPhase.Validating) }
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    conversation = current.conversation + VerificationConversationItem.UserMessage(text),
                    phase = VerificationPhase.Submitting
                )
            }
            when (val result = submitTextVerification(text)) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(
                        draft = "",
                        conversation = current.conversation + VerificationConversationItem.Analysis(result.value),
                        phase = VerificationPhase.Success(result.value)
                    )
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(phase = VerificationPhase.Failure(result.message))
                }
            }
        }
    }

    private fun showImageUnavailable() {
        _state.update { current ->
            current.copy(
                phase = VerificationPhase.Failure(
                    "Pemeriksaan gambar belum diimplementasikan pada scaffold ini. Gunakan teks terlebih dahulu."
                )
            )
        }
    }

    private fun dismissFailure() {
        _state.update { current -> current.copy(phase = VerificationPhase.Idle) }
    }

    private fun toggleHistory() {
        val shouldLoad = !state.value.isHistoryVisible && state.value.history.isEmpty()
        _state.update { current -> current.copy(isHistoryVisible = !current.isHistoryVisible) }
        if (shouldLoad) {
            refreshHistory()
        }
    }

    private fun refreshHistory() {
        if (state.value.isHistoryLoading) return
        _state.update { current -> current.copy(isHistoryLoading = true, phase = VerificationPhase.Idle) }
        viewModelScope.launch {
            when (val result = loadHistory()) {
                is AppResult.Success -> _state.update { current ->
                    current.copy(history = result.value, isHistoryLoading = false)
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(
                        isHistoryLoading = false,
                        phase = VerificationPhase.Failure(result.message)
                    )
                }
            }
        }
    }

    private fun openHistory(caseId: String) {
        _state.update { current -> current.copy(phase = VerificationPhase.Validating) }
        viewModelScope.launch {
            when (val result = loadHistoryDetail(caseId)) {
                is AppResult.Success -> _state.update { current ->
                    val restored = buildList {
                        result.value.inputText?.takeIf(String::isNotBlank)?.let { text ->
                            add(VerificationConversationItem.UserMessage(text))
                        }
                        add(VerificationConversationItem.Analysis(result.value.result))
                    }
                    current.copy(
                        conversation = restored,
                        draft = "",
                        isHistoryVisible = false,
                        phase = VerificationPhase.Idle
                    )
                }
                is AppResult.Failure -> _state.update { current ->
                    current.copy(phase = VerificationPhase.Failure(result.message))
                }
            }
        }
    }

    class Factory(
        private val submitTextVerification: SubmitTextVerificationUseCase,
        private val loadHistory: LoadVerificationHistoryUseCase,
        private val loadHistoryDetail: LoadVerificationHistoryDetailUseCase,
        private val isRemoteEnabled: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(VerificationViewModel::class.java))
            return VerificationViewModel(
                submitTextVerification,
                loadHistory,
                loadHistoryDetail,
                isRemoteEnabled
            ) as T
        }
    }

    private companion object {
        const val MINIMUM_TEXT_LENGTH = 10
    }
}
