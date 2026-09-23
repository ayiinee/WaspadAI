package id.waspadai.app.feature.learning.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import id.waspadai.app.feature.learning.domain.LearningModuleItem

class LearningScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun backendMaterialListIsVisibleAndDispatchesOpen() {
        var openedModule: String? = null
        val module = LearningModuleItem("module-1", "phishing", "Phishing, OTP, dan PIN", "Materi dari backend", 1, 1, 2, 0, 0.0)
        composeRule.setContent {
            LearningScreen(
                uiState = LearningUiState(modules = listOf(module), loading = false),
                onAction = { if (it is LearningAction.OpenModule) openedModule = it.moduleId },
            )
        }

        composeRule.onNodeWithText("Materi untukmu").assertIsDisplayed()
        composeRule.onNodeWithText("Phishing, OTP, dan PIN").performClick()
        composeRule.runOnIdle { assert(openedModule == "module-1") }
    }
}
