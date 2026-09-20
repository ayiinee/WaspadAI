package id.waspadai.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.waspadai.app.feature.community.presentation.CommunityRoute
import id.waspadai.app.feature.auth.presentation.AuthLandingScreen
import id.waspadai.app.feature.learning.presentation.LearningScreen
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryDetailUseCase
import id.waspadai.app.feature.verification.domain.LoadVerificationHistoryUseCase
import id.waspadai.app.feature.verification.domain.SubmitImageVerificationUseCase
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import id.waspadai.app.feature.community.domain.PublishCommunityCaseUseCase
import id.waspadai.app.feature.community.domain.RequestCommunityPreviewUseCase
import id.waspadai.app.feature.verification.presentation.VerificationRoute
import id.waspadai.app.feature.verification.presentation.VerificationViewModel
import id.waspadai.app.ui.theme.WaspadAITheme

private const val VerificationRouteName = "verification"
private const val CommunityRouteName = "community"
private const val LearningRouteName = "learning"
private const val WelcomeRouteName = "welcome"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
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

    NavHost(navController = navController, startDestination = WelcomeRouteName) {
        composable(WelcomeRouteName) {
            AuthLandingScreen(
                onAuthenticate = { email, password, isSignUp ->
                    runCatching {
                        if (isSignUp) {
                            app.authRepository.signUp(email, password)
                        } else {
                            app.authRepository.signIn(email, password)
                        }
                    }
                },
                onAuthenticated = {
                    navController.navigate(VerificationRouteName) {
                        popUpTo(WelcomeRouteName) { inclusive = true }
                    }
                },
            )
        }
        composable(VerificationRouteName) {
            val viewModel: VerificationViewModel = viewModel(
                factory = VerificationViewModel.Factory(
                    submitTextVerification = SubmitTextVerificationUseCase(app.verificationRepository),
                    submitImageVerification = SubmitImageVerificationUseCase(app.verificationRepository),
                    loadHistory = LoadVerificationHistoryUseCase(app.verificationRepository),
                    loadHistoryDetail = LoadVerificationHistoryDetailUseCase(app.verificationRepository),
                    requestCommunityPreview = RequestCommunityPreviewUseCase(app.communityRepository),
                    publishCommunityCase = PublishCommunityCaseUseCase(app.communityRepository),
                    communityBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
                    accessTokenProvider = app.authRepository,
                    isRemoteEnabled = BuildConfig.WASPADAI_REMOTE_ENABLED,
                ),
            )
            VerificationRoute(
                viewModel = viewModel,
                onDestinationSelected = { destination ->
                    when {
                        destination == "Koneksi" && currentRoute != CommunityRouteName ->
                            navController.navigate(CommunityRouteName)
                        destination == "Pelajari" && currentRoute != LearningRouteName ->
                            navController.navigate(LearningRouteName)
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
            var accessToken by remember { mutableStateOf(BuildConfig.WASPADAI_SUPABASE_ACCESS_TOKEN) }
            LaunchedEffect(Unit) {
                accessToken = app.authRepository.currentAccessToken().orEmpty()
            }
            CommunityRoute(
                repository = app.communityRepository,
                defaultBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
                defaultAccessToken = accessToken,
                onBack = { navController.popBackStack() },
                onDestinationSelected = { destination ->
                    when (destination) {
                        "Periksa" -> if (currentRoute != VerificationRouteName) {
                            navController.navigate(VerificationRouteName) {
                                popUpTo(VerificationRouteName) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        "Pelajari" -> if (currentRoute != LearningRouteName) navController.navigate(LearningRouteName)
                    }
                },
            )
        }
        composable(LearningRouteName) {
            LearningScreen(
                onDestinationSelected = { destination ->
                    when (destination) {
                        "Periksa" -> if (currentRoute != VerificationRouteName) {
                            navController.navigate(VerificationRouteName) {
                                popUpTo(VerificationRouteName) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        "Koneksi" -> if (currentRoute != CommunityRouteName) navController.navigate(CommunityRouteName)
                    }
                },
            )
        }
    }
}
