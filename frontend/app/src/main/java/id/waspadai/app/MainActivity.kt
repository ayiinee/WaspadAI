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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.waspadai.app.feature.community.presentation.CommunityRoute
import id.waspadai.app.feature.community.presentation.CommunityViewModel
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
import id.waspadai.app.feature.community.presentation.CommunityAction

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
        val sharedCaseId = intent?.data
            ?.takeIf { it.scheme == "waspadai" && it.host == "community" }
            ?.lastPathSegment
        setContent {
            WaspadAITheme {
                WaspadAiApp(app, sharedCaseId)
            }
        }
    }
}

@Composable
private fun WaspadAiApp(app: WaspadAIApplication, sharedCaseId: String? = null) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val communityViewModel: CommunityViewModel = viewModel(
        factory = CommunityViewModel.Factory(
            repository = app.communityRepository,
            accessTokenProvider = app.authRepository,
            communityBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
            defaultBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
            defaultAccessToken = BuildConfig.WASPADAI_SUPABASE_ACCESS_TOKEN,
        ),
    )
    val navigateToTopLevel: (String) -> Unit = { destination ->
        val targetRoute = when (destination) {
            "Periksa" -> VerificationRouteName
            "Koneksi" -> CommunityRouteName
            "Pelajari" -> LearningRouteName
            else -> null
        }
        if (targetRoute != null && targetRoute != currentRoute) {
            if (targetRoute == VerificationRouteName) {
                val returnedToVerification = navController.popBackStack(
                    route = VerificationRouteName,
                    inclusive = false,
                )
                if (!returnedToVerification) {
                    navController.navigate(VerificationRouteName) {
                        launchSingleTop = true
                    }
                }
            } else {
                navController.navigate(targetRoute) {
                    popUpTo(VerificationRouteName) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            VerificationRouteName -> communityViewModel.onAction(CommunityAction.PrefetchBackend)
            CommunityRouteName -> communityViewModel.onAction(CommunityAction.InitScreen)
        }
    }

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
                    navController.navigate(if (sharedCaseId != null) CommunityRouteName else VerificationRouteName) {
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
                    communityRepository = app.communityRepository,
                    communityBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
                    accessTokenProvider = app.authRepository,
                    isRemoteEnabled = BuildConfig.WASPADAI_REMOTE_ENABLED,
                ),
            )
            VerificationRoute(
                viewModel = viewModel,
                onDestinationSelected = navigateToTopLevel,
                onCommunityPublished = { communityId ->
                    communityViewModel.onAction(CommunityAction.OpenPublishedPost(communityId))
                    navigateToTopLevel("Koneksi")
                },
            )
        }
        composable(CommunityRouteName) {
            CommunityRoute(
                repository = app.communityRepository,
                defaultBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
                defaultAccessToken = BuildConfig.WASPADAI_SUPABASE_ACCESS_TOKEN,
                onBack = { navController.popBackStack() },
                viewModel = communityViewModel,
                initialPostId = sharedCaseId,
                onDestinationSelected = navigateToTopLevel,
            )
        }
        composable(LearningRouteName) {
            LearningScreen(
                onDestinationSelected = navigateToTopLevel,
            )
        }
    }
}
