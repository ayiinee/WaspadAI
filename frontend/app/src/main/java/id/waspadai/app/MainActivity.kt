package id.waspadai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import id.waspadai.app.feature.verification.presentation.VerificationRoute
import id.waspadai.app.feature.verification.presentation.VerificationViewModel
import id.waspadai.app.ui.theme.WaspadAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as WaspadAIApplication
        setContent {
            WaspadAITheme(dynamicColor = false) {
                val viewModel: VerificationViewModel = viewModel(
                    factory = VerificationViewModel.Factory(
                        submitTextVerification = SubmitTextVerificationUseCase(app.verificationRepository),
                        isRemoteEnabled = BuildConfig.WASPADAI_REMOTE_ENABLED
                    )
                )
                VerificationRoute(viewModel)
            }
        }
    }
}
