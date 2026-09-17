package id.waspadai.app.feature.verification.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.waspadai.app.core.common.AppResult
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VerificationViewModel(
    private val submitTextVerification: SubmitTextVerificationUseCase,
    isRemoteEnabled: Boolean
) : ViewModel() {
    private val _state = MutableStateFlow(VerificationUiState.initial(isRemoteEnabled))
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    fun onAction(action: VerificationAction) {
        when (action) {
            is VerificationAction.InputChanged -> updateInput(action.value)
            VerificationAction.SubmitText -> submitText()
            VerificationAction.RequestImageCapture -> showImageUnavailable()
            VerificationAction.DismissFailure -> dismissFailure()
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

    class Factory(
        private val submitTextVerification: SubmitTextVerificationUseCase,
        private val isRemoteEnabled: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(VerificationViewModel::class.java))
            return VerificationViewModel(submitTextVerification, isRemoteEnabled) as T
        }
    }

    private companion object {
        const val MINIMUM_TEXT_LENGTH = 10
    }
}
