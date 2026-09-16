package id.waspadai.app.feature.verification.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.waspadai.app.feature.verification.presentation.component.AnalysisCard
import id.waspadai.app.feature.verification.presentation.component.BottomNavigation
import id.waspadai.app.feature.verification.presentation.component.FailureNotice
import id.waspadai.app.feature.verification.presentation.component.ThinkingBubble
import id.waspadai.app.feature.verification.presentation.component.UserMessage
import id.waspadai.app.feature.verification.presentation.component.VerificationComposer
import id.waspadai.app.feature.verification.presentation.component.WaspadAiHeader
import id.waspadai.app.ui.theme.WaspadAITheme

@Composable
fun VerificationRoute(viewModel: VerificationViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VerificationScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun VerificationScreen(
    state: VerificationUiState,
    onAction: (VerificationAction) -> Unit
) {
    val listState = rememberLazyListState()
    var activeTab by rememberSaveable { mutableStateOf("Periksa") }
    val isSubmitting = state.phase is VerificationPhase.Validating || state.phase is VerificationPhase.Submitting

    LaunchedEffect(state.conversation.size, state.phase) {
        if (state.conversation.isNotEmpty()) {
            listState.animateScrollToItem(state.conversation.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
    ) {
        WaspadAiHeader()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(state.conversation) { item ->
                when (item) {
                    is VerificationConversationItem.UserMessage -> UserMessage(item.text, item.hasAttachment)
                    is VerificationConversationItem.Analysis -> AnalysisCard(item.result, item.isSample)
                }
            }
            when (val phase = state.phase) {
                VerificationPhase.Validating -> item { ThinkingBubble("Menyiapkan pemeriksaan…") }
                VerificationPhase.Submitting -> item { ThinkingBubble("WaspadAI sedang memeriksa…") }
                is VerificationPhase.Failure -> item {
                    FailureNotice(phase.message) { onAction(VerificationAction.DismissFailure) }
                }
                else -> Unit
            }
            item { Spacer(Modifier.padding(bottom = 1.dp)) }
        }
        VerificationComposer(
            value = state.draft,
            enabled = !isSubmitting,
            onValueChange = { onAction(VerificationAction.InputChanged(it)) },
            onSubmit = { onAction(VerificationAction.SubmitText) },
            onRequestImageCapture = { onAction(VerificationAction.RequestImageCapture) }
        )
        BottomNavigation(activeTab = activeTab) { activeTab = it }
    }
}

@Preview(showBackground = true, heightDp = 900, widthDp = 412)
@Composable
private fun VerificationScreenPreview() {
    WaspadAITheme(dynamicColor = false) {
        VerificationScreen(VerificationUiState(), onAction = {})
    }
}
