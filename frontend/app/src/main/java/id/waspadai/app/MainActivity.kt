package id.waspadai.app

import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.waspadai.app.feature.community.presentation.CommunityRoute
import id.waspadai.app.feature.community.presentation.CommunityViewModel
import id.waspadai.app.feature.auth.presentation.AuthLandingScreen
import id.waspadai.app.feature.home.presentation.HomeRoute
import id.waspadai.app.feature.home.presentation.HomeViewModel
import id.waspadai.app.feature.home.presentation.HomeAction
import id.waspadai.app.feature.home.domain.LoadHomeUseCase
import id.waspadai.app.feature.learning.presentation.LearningScreen
import id.waspadai.app.feature.learning.presentation.LearningViewModel
import id.waspadai.app.feature.learning.presentation.LearningAction
import id.waspadai.app.feature.profile.presentation.ProfileRoute
import id.waspadai.app.feature.profile.presentation.ProfileAction
import id.waspadai.app.feature.profile.presentation.ProfileViewModel
import id.waspadai.app.feature.verification.domain.LoadVerificationConversationDetailUseCase
import id.waspadai.app.feature.verification.domain.LoadVerificationConversationsUseCase
import id.waspadai.app.feature.verification.domain.SubmitImageVerificationUseCase
import id.waspadai.app.feature.verification.domain.SubmitTextVerificationUseCase
import id.waspadai.app.feature.community.domain.PublishCommunityCaseUseCase
import id.waspadai.app.feature.community.domain.RequestCommunityPreviewUseCase
import id.waspadai.app.feature.verification.presentation.VerificationRoute
import id.waspadai.app.feature.verification.presentation.VerificationAction
import id.waspadai.app.feature.verification.presentation.VerificationPhase
import id.waspadai.app.feature.verification.presentation.VerificationViewModel
import id.waspadai.app.ui.theme.WaspadAITheme
import id.waspadai.app.feature.community.presentation.CommunityAction
import id.waspadai.app.core.ui.CommunityNotificationState
import id.waspadai.app.core.ui.LocalCommunityNotification
import id.waspadai.app.core.overlay.FloatingVerifyService
import id.waspadai.app.core.trigger.CapturedContext
import id.waspadai.app.core.trigger.IncomingTriggerParser
import id.waspadai.app.core.trigger.PendingVerificationTrigger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.widget.Toast

private const val VerificationRouteName = "verification/list"
private const val VerificationChatNewRouteName = "verification/chat/new"
private const val VerificationChatRouteName = "verification/chat/{conversationId}"
private const val HomeRouteName = "home"
private const val CommunityRouteName = "community"
private const val LearningRouteName = "learning"
private const val ProfileRouteName = "profile"
private const val WelcomeRouteName = "welcome"

class MainActivity : ComponentActivity() {
    private val openTanyaAreaFull = MutableStateFlow(false)
    private val pendingTrigger = MutableStateFlow<PendingVerificationTrigger?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        val app = application as WaspadAIApplication
        val sharedCaseId = intent?.data
            ?.takeIf { it.scheme == "waspadai" && it.host == "community" }
            ?.lastPathSegment
        openTanyaAreaFull.value = intent?.getBooleanExtra(
            FloatingVerifyService.EXTRA_OPEN_FULL,
            false,
        ) == true
        consumeTriggerIntent(intent)
        setContent {
            WaspadAITheme {
                val shouldOpenTanyaArea by openTanyaAreaFull.collectAsStateWithLifecycle()
                val trigger by pendingTrigger.collectAsStateWithLifecycle()
                WaspadAiApp(
                    app = app,
                    sharedCaseId = sharedCaseId,
                    openTanyaAreaFull = shouldOpenTanyaArea,
                    onTanyaAreaOpened = { openTanyaAreaFull.value = false },
                    pendingTrigger = trigger,
                    onTriggerConsumed = { pendingTrigger.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(FloatingVerifyService.EXTRA_OPEN_FULL, false)) {
            openTanyaAreaFull.value = true
        }
        consumeTriggerIntent(intent)
    }

    private fun consumeTriggerIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_CONSUME_PENDING_TRIGGER, false)) {
            pendingTrigger.value = (application as WaspadAIApplication).pendingTriggerStore.take()
            intent.removeExtra(EXTRA_CONSUME_PENDING_TRIGGER)
            return
        }
        if (intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
            lifecycleScope.launch {
                IncomingTriggerParser(contentResolver).parse(intent)
                    .onSuccess { pendingTrigger.value = it }
                    .onFailure {
                        Toast.makeText(
                            this@MainActivity,
                            it.message ?: "Konten share tidak dapat dibaca.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                intent.action = null
            }
        }
    }

    companion object {
        const val EXTRA_CONSUME_PENDING_TRIGGER = "consume_pending_verification_trigger"
    }
}

@Composable
private fun WaspadAiApp(
    app: WaspadAIApplication,
    sharedCaseId: String? = null,
    openTanyaAreaFull: Boolean = false,
    onTanyaAreaOpened: () -> Unit = {},
    pendingTrigger: PendingVerificationTrigger? = null,
    onTriggerConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var showCommunityBadge by rememberSaveable { mutableStateOf(false) }
    var quickAccessRequest by rememberSaveable { mutableStateOf(0) }
    val verificationViewModel: VerificationViewModel = viewModel(
        factory = VerificationViewModel.Factory(
            submitTextVerification = SubmitTextVerificationUseCase(app.verificationRepository),
            submitImageVerification = SubmitImageVerificationUseCase(app.verificationRepository),
            loadHistory = LoadVerificationConversationsUseCase(app.verificationRepository),
            loadHistoryDetail = LoadVerificationConversationDetailUseCase(
                app.verificationRepository,
            ),
            requestCommunityPreview = RequestCommunityPreviewUseCase(app.communityRepository),
            publishCommunityCase = PublishCommunityCaseUseCase(app.communityRepository),
            communityRepository = app.communityRepository,
            communityBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
            accessTokenProvider = app.authRepository,
            isRemoteEnabled = BuildConfig.WASPADAI_REMOTE_ENABLED,
        ),
    )
    val communityViewModel: CommunityViewModel = viewModel(
        factory = CommunityViewModel.Factory(
            repository = app.communityRepository,
            accessTokenProvider = app.authRepository,
            communityBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
            defaultBaseUrl = BuildConfig.WASPADAI_API_BASE_URL,
            defaultAccessToken = BuildConfig.WASPADAI_SUPABASE_ACCESS_TOKEN,
        ),
    )
    val learningViewModel: LearningViewModel = viewModel(
        factory = LearningViewModel.Factory(
            repository = app.learningRepository,
            accessTokenProvider = app.authRepository,
            baseUrl = BuildConfig.WASPADAI_API_BASE_URL,
        ),
    )
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.Factory(
            repository = app.profileRepository,
            tokenProvider = app.authRepository,
            baseUrl = BuildConfig.WASPADAI_API_BASE_URL,
        ),
    )
    val learningUiState by learningViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(currentRoute) {
        if (currentRoute == LearningRouteName) learningViewModel.onAction(LearningAction.Refresh)
    }
    LaunchedEffect(openTanyaAreaFull, currentRoute) {
        if (openTanyaAreaFull && currentRoute != null && currentRoute != WelcomeRouteName) {
            if (currentRoute != VerificationRouteName) {
                navController.navigate(VerificationRouteName) {
                    popUpTo(HomeRouteName) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onTanyaAreaOpened()
        }
    }
    LaunchedEffect(pendingTrigger?.id, currentRoute) {
        if (pendingTrigger != null && currentRoute != null && currentRoute != WelcomeRouteName) {
            if (currentRoute != VerificationRouteName) {
                navController.navigate(VerificationRouteName) {
                    popUpTo(HomeRouteName) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    val navigateToTopLevel: (String) -> Unit = { destination ->
        val targetRoute = when (destination) {
            "Beranda" -> HomeRouteName
            "Periksa" -> VerificationRouteName
            "Koneksi" -> CommunityRouteName
            "Pelajari" -> LearningRouteName
            "Profil" -> ProfileRouteName
            else -> null
        }
        if (targetRoute != null && targetRoute != currentRoute) {
            if (targetRoute == HomeRouteName) {
                val returnedToHome = navController.popBackStack(
                    route = HomeRouteName,
                    inclusive = false,
                )
                if (!returnedToHome) {
                    navController.navigate(HomeRouteName) {
                        launchSingleTop = true
                    }
                }
            } else {
                navController.navigate(targetRoute) {
                    popUpTo(HomeRouteName) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            VerificationRouteName -> {
                communityViewModel.onAction(CommunityAction.PrefetchBackend)
                verificationViewModel.onAction(VerificationAction.RefreshHistory)
            }
            CommunityRouteName -> communityViewModel.onAction(CommunityAction.RefreshBackend)
            ProfileRouteName -> profileViewModel.onAction(ProfileAction.Refresh)
        }
    }

    CompositionLocalProvider(
        LocalCommunityNotification provides CommunityNotificationState(
            showBadge = showCommunityBadge,
            markOpened = { showCommunityBadge = false },
        )
    ) {
    val startDestination = rememberSaveable {
        when {
            !app.authRepository.hasSession() -> WelcomeRouteName
            sharedCaseId != null -> CommunityRouteName
            else -> HomeRouteName
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {
        composable(WelcomeRouteName) {
            AuthLandingScreen(
                rememberedCredentials = app.rememberedCredentialsStore.load(),
                onRememberCredentials = app.rememberedCredentialsStore::save,
                onForgetCredentials = app.rememberedCredentialsStore::clear,
                onAuthenticate = { email, password, fullName, isSignUp ->
                    runCatching {
                        if (isSignUp) {
                            app.authRepository.signUp(
                                email = email,
                                password = password,
                                fullName = requireNotNull(fullName),
                            )
                        } else {
                            app.authRepository.signIn(email, password)
                        }
                    }
                },
                onRequestPasswordReset = { email ->
                    runCatching { app.authRepository.requestPasswordReset(email) }
                },
                onVerifyPasswordResetCode = { email, code ->
                    runCatching { app.authRepository.verifyPasswordResetCode(email, code) }
                },
                onUpdatePassword = { newPassword ->
                    runCatching { app.authRepository.updatePassword(newPassword) }
                },
                onAuthenticated = {
                    navController.navigate(if (sharedCaseId != null) CommunityRouteName else HomeRouteName) {
                        popUpTo(WelcomeRouteName) { inclusive = true }
                    }
                },
            )
        }
        composable(HomeRouteName) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    loadHome = LoadHomeUseCase(app.homeRepository),
                    accessTokenProvider = app.authRepository,
                    baseUrl = BuildConfig.WASPADAI_API_BASE_URL,
                ),
            )
            LaunchedEffect(Unit) { viewModel.onAction(HomeAction.Refresh) }
            HomeRoute(
                onDestinationSelected = navigateToTopLevel,
                onCommunityCaseSelected = { communityId ->
                    communityViewModel.onAction(
                        CommunityAction.OpenPublishedPost(communityId),
                    )
                    navigateToTopLevel("Koneksi")
                },
                viewModel = viewModel,
            )
        }
        composable(VerificationRouteName) {
            LaunchedEffect(pendingTrigger?.id) {
                val trigger = pendingTrigger ?: return@LaunchedEffect
                val sharedText = trigger.contexts
                    .filterIsInstance<CapturedContext.Text>()
                    .joinToString("\n\n") { it.text }
                if (sharedText.isNotBlank()) {
                    val firstText = trigger.contexts.filterIsInstance<CapturedContext.Text>().first()
                    verificationViewModel.onAction(
                        VerificationAction.TextContextSelected(
                            text = sharedText,
                            source = firstText.source,
                            pageContext = firstText.pageContext,
                        )
                    )
                }
                val images = trigger.contexts
                    .filterIsInstance<CapturedContext.Image>()
                    .map { image ->
                        VerificationAction.ImageSelected(
                            imageBytes = image.imageBytes,
                            contentType = image.contentType,
                            fileName = image.fileName,
                            source = image.source,
                        )
                }
                if (images.isNotEmpty()) {
                    verificationViewModel.onAction(VerificationAction.AttachmentsSelected(images))
                }
                onTriggerConsumed()
            }
            VerificationRoute(
                viewModel = verificationViewModel,
                openQuickAccessRequest = quickAccessRequest,
                onDestinationSelected = navigateToTopLevel,
                onCommunityPublished = { communityId ->
                    showCommunityBadge = true
                    communityViewModel.onAction(CommunityAction.OpenPublishedPost(communityId))
                    navigateToTopLevel("Koneksi")
                },
            )
        }
        composable(VerificationChatNewRouteName) {
            val verificationState by verificationViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                verificationViewModel.onAction(VerificationAction.NewConversation)
            }
            LaunchedEffect(
                verificationState.activeConversationId,
                verificationState.phase,
            ) {
                val conversationId = verificationState.activeConversationId
                if (conversationId != null && verificationState.phase is VerificationPhase.Success) {
                    navController.navigate("verification/chat/$conversationId") {
                        popUpTo(VerificationChatNewRouteName) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            VerificationRoute(
                viewModel = verificationViewModel,
                isChatScreen = true,
                onBackToConversations = {
                    navController.popBackStack(VerificationRouteName, inclusive = false)
                    verificationViewModel.onAction(VerificationAction.RefreshHistory)
                },
                onCommunityPublished = { communityId ->
                    showCommunityBadge = true
                    communityViewModel.onAction(CommunityAction.OpenPublishedPost(communityId))
                    navigateToTopLevel("Koneksi")
                },
            )
        }
        composable(VerificationChatRouteName) { entry ->
            val conversationId = entry.arguments?.getString("conversationId").orEmpty()
            LaunchedEffect(conversationId) {
                if (conversationId.isNotBlank()) {
                    verificationViewModel.onAction(
                        VerificationAction.OpenConversation(conversationId),
                    )
                }
            }
            VerificationRoute(
                viewModel = verificationViewModel,
                isChatScreen = true,
                onBackToConversations = {
                    navController.popBackStack(VerificationRouteName, inclusive = false)
                    verificationViewModel.onAction(VerificationAction.RefreshHistory)
                },
                onCommunityPublished = { communityId ->
                    showCommunityBadge = true
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
                currentUserName = profileState.profile?.displayName,
                currentUserAvatarUrl = profileState.profile?.avatarUrl,
            )
        }
        composable(LearningRouteName) {
            LearningScreen(
                uiState = learningUiState,
                onAction = learningViewModel::onAction,
                onDestinationSelected = navigateToTopLevel,
            )
        }
        composable(ProfileRouteName) {
            ProfileRoute(
                state = profileState,
                accessToken = profileState.accessToken,
                onAction = profileViewModel::onAction,
                onDestinationSelected = navigateToTopLevel,
                onChangePassword = { password ->
                    runCatching { app.authRepository.updatePassword(password) }
                },
                onLogout = {
                    app.authRepository.clearSession()
                    communityViewModel.onAction(CommunityAction.ResetPrivateState)
                    learningViewModel.onAction(LearningAction.ResetPrivateState)
                    app.rememberedCredentialsStore.clear()
                    app.pendingTriggerStore.clear()
                    onTriggerConsumed()
                    FloatingVerifyService.stop(app)
                    navController.navigate(WelcomeRouteName) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onCommunityPostSelected = { communityId ->
                    communityViewModel.onAction(CommunityAction.OpenPublishedPost(communityId))
                    navigateToTopLevel("Koneksi")
                },
                onOpenQuickAccess = {
                    quickAccessRequest += 1
                    navigateToTopLevel("Periksa")
                },
            )
        }
    }
    }
}
