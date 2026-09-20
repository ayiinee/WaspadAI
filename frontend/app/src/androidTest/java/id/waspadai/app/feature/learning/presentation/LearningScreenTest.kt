package id.waspadai.app.feature.learning.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class LearningScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun materialListDetailAndQuizAreVisible() {
        composeRule.setContent { LearningScreen() }

        composeRule.onNodeWithText("Misi harian").assertIsDisplayed()
        composeRule.onNodeWithText("Jadi detektif hoaks hari ini").assertIsDisplayed()
        composeRule.onNodeWithText("Misi cek sumber selesai").assertIsDisplayed()
        composeRule.onNodeWithText("LANJUTKAN BELAJAR").assertDoesNotExist()
        composeRule.onNodeWithText("Materi untukmu").assertIsDisplayed()
        composeRule.onNodeWithText("Kenali ciri-ciri hoaks").assertIsDisplayed()
        composeRule.onNodeWithText("Jadi detektif hoaks hari ini").performClick()
        composeRule.onNodeWithText("URUTAN MATERI").assertIsDisplayed()
        composeRule.onNodeWithText("Visual materi").assertIsDisplayed()

        repeat(2) {
            composeRule.onNodeWithText("Lanjut ke tahap berikutnya").performScrollTo().performClick()
        }
        composeRule.onNodeWithText("Selesai membaca").performScrollTo().performClick()
        composeRule.onNodeWithText("Mulai latihan soal").performClick()

        composeRule.onNodeWithText("Kalimat mana yang paling perlu dicurigai?").assertIsDisplayed()
        composeRule.onNodeWithText("Sebarkan sekarang juga sebelum dihapus!").assertIsDisplayed()
    }
}
