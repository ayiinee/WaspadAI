package id.waspadai.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.waspadai.app.feature.community.presentation.CommunityRoute
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import id.waspadai.app.feature.verification.presentation.VerificationRoute
import id.waspadai.app.feature.verification.presentation.VerificationViewModel
import id.waspadai.app.ui.theme.WaspadAITheme

private const val VerificationRouteName = "verification"
private const val CommunityRouteName = "community"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        val app = application as WaspadAIApplication
        setContent {
            WaspadAITheme {
                WaspadAiApp(app)
            }
        }
    }
}

@Composable
private fun WaspadAiApp(app: WaspadAIApplication) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavHost(navController = navController, startDestination = VerificationRouteName) {
        composable(VerificationRouteName) {
            val viewModel: VerificationViewModel = viewModel(
                factory = VerificationViewModel.Factory(
                    submitTextVerification = SubmitTextVerificationUseCase(app.verificationRepository),
                    isRemoteEnabled = BuildConfig.WASPADAI_REMOTE_ENABLED,
                ),
            )
            VerificationRoute(
                viewModel = viewModel,
                onDestinationSelected = { destination ->
                    when {
                        destination == "Koneksi" && currentRoute != CommunityRouteName ->
                            navController.navigate(CommunityRouteName)
                        destination == "Periksa" && currentRoute != VerificationRouteName ->
                            navController.navigate(VerificationRouteName) {
                                popUpTo(VerificationRouteName) { inclusive = false }
                                launchSingleTop = true
                            }
                    }
                },
            )
        }
        composable(CommunityRouteName) {
            CommunityRoute(
                repository = app.communityRepository,
                defaultBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
                defaultAccessToken = BuildConfig.WASPADAI_SUPABASE_ACCESS_TOKEN,
                onBack = { navController.popBackStack() },
                onDestinationSelected = { destination ->
                    if (destination == "Periksa" && currentRoute != VerificationRouteName) {
                        navController.navigate(VerificationRouteName) {
                            popUpTo(VerificationRouteName) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
    }
}
