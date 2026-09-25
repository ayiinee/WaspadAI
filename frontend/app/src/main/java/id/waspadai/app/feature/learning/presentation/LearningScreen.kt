package id.waspadai.app.feature.learning.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.core.ui.WaspadAIPageHeader
import id.waspadai.app.core.ui.waspadAIBottomNavigationContentPadding
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import id.waspadai.app.R
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAIContribution
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAILightBlue
import id.waspadai.app.ui.theme.WaspadAIMuted
import id.waspadai.app.ui.theme.WaspadAIValid
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class LearningStage(val title: String, val body: String, val lessonId: String = "")

@Composable
private fun learningContentGutter() =
    if (LocalConfiguration.current.screenWidthDp < 360) 16.dp else 22.dp

private data class LearningQuestion(
    val question: String,
    val answers: List<String>,
    val correctAnswer: Int = -1,
    val questionId: String = "",
    val optionIds: List<String> = emptyList(),
)

private data class LearningMaterial(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val illustrationRes: Int? = null,
    val illustrationTileColor: Color = Color(0xFFEAF4FB),
    val stages: List<LearningStage>,
    val questions: List<LearningQuestion>,
    val moduleId: String = "",
    val moduleVersion: Int = 1,
    val mediaId: String = "",
    val imageUrl: String? = null,
    val imageAltText: String = "",
    val latestScore: Double? = null,
    val progressUpdatedAt: String? = null,
    val latestCorrectAnswers: Int? = null,
    val latestTotalQuestions: Int? = null,
    val readingDurationSeconds: Long? = null,
    val quizDurationSeconds: Long? = null,
)

private data class LearningSessionStats(
    val correctAnswers: Int,
    val totalQuestions: Int,
    val readingSeconds: Long,
    val quizSeconds: Long,
    val completedAt: String,
)

private data class LearningStatus(
    val kind: String,
    val questionIndex: Int = 0,
    val progressPercent: Float = 0f,
)

private data class LearningVisual(
    val illustrationRes: Int?,
    val tileColor: Color,
)

private fun learningVisualFor(title: String): LearningVisual = when {
    title.contains("Phishing", ignoreCase = true) ||
        title.contains("OTP", ignoreCase = true) ||
        title.contains("PIN", ignoreCase = true) ->
        LearningVisual(R.drawable.learning_phishing, Color(0xFFE4F2FC))
    title.contains("Impersonation", ignoreCase = true) ||
        title.contains("instansi", ignoreCase = true) ->
        LearningVisual(R.drawable.learning_impersonation, Color(0xFFF0ECFF))
    title.contains("Misinformasi", ignoreCase = true) ||
        title.contains("Hoaks", ignoreCase = true) ->
        LearningVisual(R.drawable.learning_hoax, Color(0xFFFFE7E7))
    else -> LearningVisual(null, Color(0xFFEAF4FB))
}

private fun formatElapsed(totalSeconds: Long): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatCompletionTime(value: String?): String? = value?.let { timestamp ->
    runCatching {
        OffsetDateTime.parse(timestamp)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrNull()
}

private val learningMaterials = listOf(
    LearningMaterial(
        title = "Kenali ciri-ciri hoaks",
        description = "Temukan tanda-tanda informasi yang perlu diperiksa ulang.",
        icon = Icons.Rounded.MenuBook,
        iconColor = WaspadAIBlue,
        stages = listOf(
            LearningStage("Apa itu hoaks?", "Hoaks adalah informasi palsu atau menyesatkan yang dibuat seolah-olah benar. Hoaks dapat memancing rasa takut, marah, atau terburu-buru agar kita langsung percaya dan membagikannya."),
            LearningStage("Perhatikan judul dan bahasa", "Judul yang terlalu heboh, huruf kapital berlebihan, dan kalimat seperti ‘sebarkan sekarang juga’ adalah tanda untuk berhenti sejenak. Baca isi lengkapnya, bukan hanya judul."),
            LearningStage("Gunakan tiga pertanyaan", "Tanyakan: siapa sumbernya, kapan informasi dibuat, dan apa buktinya? Jika satu saja belum jelas, simpan dulu informasi tersebut dan lakukan pemeriksaan ulang."),
        ),
        questions = listOf(
            LearningQuestion("Kalimat mana yang paling perlu dicurigai?", listOf("Baca laporan lengkap di situs resmi", "Sebarkan sekarang juga sebelum dihapus!", "Data dirangkum dari tiga sumber"), 1),
            LearningQuestion("Langkah pertama saat menerima kabar mengejutkan adalah...", listOf("Langsung meneruskan ke grup", "Mengecek sumber dan tanggalnya", "Menghapus semua pesan"), 1),
            LearningQuestion("Jika bukti sebuah klaim belum jelas, sebaiknya...", listOf("Menunda membagikan dan memeriksa ulang", "Menambahkan opini agar lebih meyakinkan", "Meminta orang lain menyebarkannya"), 0),
        ),
    ),
    LearningMaterial(
        title = "Periksa sumber informasi",
        description = "Latih kebiasaan mengecek sumber sebelum percaya atau berbagi.",
        icon = Icons.Rounded.CollectionsBookmark,
        iconColor = Color(0xFF635BBA),
        stages = listOf(
            LearningStage("Cari sumber pertama", "Telusuri siapa yang pertama kali menerbitkan informasi. Sumber asli biasanya memiliki konteks, tanggal, dan identitas yang dapat diperiksa."),
            LearningStage("Bandingkan dengan sumber tepercaya", "Bandingkan klaim dengan situs pemerintah, media kredibel, atau pernyataan resmi. Jangan hanya mengandalkan satu unggahan yang beredar."),
            LearningStage("Cek konteks gambar", "Gambar lama dapat digunakan kembali untuk cerita baru. Gunakan pencarian gambar atau baca keterangan lengkap sebelum menarik kesimpulan."),
        ),
        questions = listOf(
            LearningQuestion("Sumber yang paling kuat untuk memeriksa kebijakan baru adalah...", listOf("Pesan berantai tanpa tautan", "Akun resmi lembaga terkait", "Komentar anonim"), 1),
            LearningQuestion("Mengapa tanggal publikasi perlu diperiksa?", listOf("Agar tahu konteks dan kebaruan informasi", "Supaya unggahan terlihat populer", "Karena semua informasi lama pasti salah"), 0),
            LearningQuestion("Apa yang dilakukan saat dua sumber berbeda?", listOf("Pilih yang paling sering dibagikan", "Bandingkan bukti dan kredibilitasnya", "Sebarkan keduanya tanpa catatan"), 1),
        ),
    ),
    LearningMaterial(
        title = "Aman dari modus penipuan",
        description = "Kenali pola pesan yang meminta data pribadi atau uang.",
        icon = Icons.Rounded.Warning,
        iconColor = Color(0xFFCF7B12),
        stages = listOf(
            LearningStage("Kenali tanda tekanan", "Penipu sering membuat situasi terasa mendesak: akun akan diblokir, hadiah harus diambil hari ini, atau keluarga sedang butuh uang."),
            LearningStage("Lindungi data rahasia", "OTP, PIN, kata sandi, dan kode pemulihan tidak boleh diberikan melalui chat. Pihak resmi tidak akan meminta data rahasia tersebut."),
            LearningStage("Verifikasi lewat kanal resmi", "Tutup percakapan lalu hubungi nomor resmi dari situs atau aplikasi. Jangan memakai nomor atau tautan yang diberikan oleh pengirim pesan."),
        ),
        questions = listOf(
            LearningQuestion("Data yang tidak boleh dibagikan lewat chat adalah...", listOf("Nama panggilan", "OTP dan PIN", "Jam bertemu"), 1),
            LearningQuestion("Saat diminta transfer dengan segera, lakukan...", listOf("Verifikasi melalui kanal resmi", "Kirim sebagian dulu", "Balas dengan foto identitas"), 0),
            LearningQuestion("Tautan hadiah yang mencurigakan sebaiknya...", listOf("Dibuka di perangkat lain", "Diteruskan ke teman", "Tidak dibuka dan dihapus"), 2),
        ),
    ),
    LearningMaterial(
        title = "Bagikan informasi dengan bijak",
        description = "Pahami langkah sederhana sebelum meneruskan sebuah kabar.",
        icon = Icons.Rounded.Lightbulb,
        iconColor = Color(0xFF277B59),
        stages = listOf(
            LearningStage("Berhenti sejenak", "Sebelum menekan tombol bagikan, periksa apakah informasi itu benar, bermanfaat, dan tidak merugikan orang lain."),
            LearningStage("Tulis konteks dengan jelas", "Jika informasi sudah terverifikasi, sertakan sumber dan konteksnya. Hindari potongan kalimat yang bisa membuat orang salah paham."),
            LearningStage("Hormati privasi", "Hapus nomor telepon, alamat, dan identitas pribadi sebelum membagikan tangkapan layar atau cerita ke ruang publik."),
        ),
        questions = listOf(
            LearningQuestion("Kebiasaan baik sebelum membagikan kabar adalah...", listOf("Mengecek kebenaran dan konteks", "Menambahkan judul yang lebih heboh", "Menyembunyikan sumber"), 0),
            LearningQuestion("Mengapa sumber perlu dicantumkan?", listOf("Agar pembaca bisa memeriksa ulang", "Supaya pesan lebih panjang", "Agar terlihat viral"), 0),
            LearningQuestion("Informasi pribadi dalam tangkapan layar sebaiknya...", listOf("Dibiarkan agar lengkap", "Dihapus atau disamarkan", "Diperbesar"), 1),
        ),
    ),
)

@Composable
fun LearningScreen(
    uiState: LearningUiState = LearningUiState(loading = false),
    onAction: (LearningAction) -> Unit = {},
    onDestinationSelected: (String) -> Unit = {},
) {
    val sessionStats = remember { mutableStateMapOf<String, LearningSessionStats>() }
    val materials = uiState.modules.mapIndexed { index, module ->
        val visual = learningVisualFor(module.title)
        LearningMaterial(
            title = module.title,
            description = module.summary,
            icon = listOf(Icons.Rounded.MenuBook, Icons.Rounded.CollectionsBookmark, Icons.Rounded.Warning, Icons.Rounded.Lightbulb)[index % 4],
            iconColor = listOf(WaspadAIBlue, Color(0xFF635BBA), Color(0xFFCF7B12), Color(0xFF277B59))[index % 4],
            illustrationRes = visual.illustrationRes,
            illustrationTileColor = visual.tileColor,
            stages = emptyList(),
            questions = emptyList(),
            moduleId = module.moduleId,
            moduleVersion = module.version,
            latestScore = module.latestScore,
            progressUpdatedAt = module.progressUpdatedAt,
            latestCorrectAnswers = module.latestCorrectAnswers,
            latestTotalQuestions = module.latestTotalQuestions,
            readingDurationSeconds = module.readingDurationSeconds,
            quizDurationSeconds = module.quizDurationSeconds,
        )
    }
    val selectedMaterial = uiState.selectedModule?.let { detail ->
        val visual = learningVisualFor(detail.title)
        LearningMaterial(
            title = detail.title,
            description = detail.summary,
            icon = Icons.Rounded.MenuBook,
            iconColor = WaspadAIBlue,
            illustrationRes = visual.illustrationRes,
            illustrationTileColor = visual.tileColor,
            stages = detail.lessons.sortedBy { it.displayOrder }.map { LearningStage(it.title, it.bodyMd, it.lessonId) },
            questions = uiState.quiz?.questions.orEmpty().map { question ->
                LearningQuestion(
                    question = question.text,
                    answers = question.options.map { it.text },
                    questionId = question.questionId,
                    optionIds = question.options.map { it.optionId },
                )
            },
            moduleId = detail.moduleId,
            moduleVersion = detail.version,
            mediaId = detail.media.firstOrNull { it.mediaType == "IMAGE" }?.mediaId.orEmpty(),
            imageUrl = detail.media.firstOrNull { it.mediaType == "IMAGE" }?.url,
            imageAltText = detail.media.firstOrNull { it.mediaType == "IMAGE" }?.altText.orEmpty(),
            latestScore = materials.firstOrNull { it.moduleId == detail.moduleId }?.latestScore,
            progressUpdatedAt = materials.firstOrNull { it.moduleId == detail.moduleId }?.progressUpdatedAt,
            latestCorrectAnswers = materials.firstOrNull { it.moduleId == detail.moduleId }?.latestCorrectAnswers,
            latestTotalQuestions = materials.firstOrNull { it.moduleId == detail.moduleId }?.latestTotalQuestions,
            readingDurationSeconds = materials.firstOrNull { it.moduleId == detail.moduleId }?.readingDurationSeconds,
            quizDurationSeconds = materials.firstOrNull { it.moduleId == detail.moduleId }?.quizDurationSeconds,
        )
    }
    fun statusFor(material: LearningMaterial): LearningStatus? = uiState.modules
        .firstOrNull { it.moduleId == material.moduleId }
        ?.let { module ->
            when {
                module.progressPercent >= 100 -> LearningStatus("completed", progressPercent = 1f)
                module.progressPercent > 0 -> LearningStatus("in_progress", progressPercent = (module.progressPercent / 100).toFloat())
                else -> LearningStatus("not_started", progressPercent = 0f)
            }
        }

    if (selectedMaterial == null) {
        LearningListScreen(
            materials = materials,
            statusFor = ::statusFor,
            sessionStats = sessionStats,
            onMaterialSelected = { onAction(LearningAction.OpenModule(it.moduleId)) },
            onPracticeSelected = { onAction(LearningAction.OpenPractice) },
            onDestinationSelected = onDestinationSelected,
        )
    } else {
        LearningDetailScreen(
            material = selectedMaterial,
            openQuizOnLoad = uiState.openQuizOnModuleLoad,
            savedQuestionIndex = 0,
            quizResult = uiState.quizResult,
            submitting = uiState.submitting,
            onAnswerSelected = { questionId, optionId -> onAction(LearningAction.SelectAnswer(questionId, optionId)) },
            selectedAnswers = uiState.answers,
            onSubmitQuiz = { readingSeconds, quizSeconds ->
                onAction(
                    LearningAction.SubmitQuiz(
                        readingDurationSeconds = readingSeconds,
                        quizDurationSeconds = quizSeconds,
                    )
                )
            },
            onLessonCompleted = { onAction(LearningAction.CompleteLesson(it)) },
            accessToken = uiState.accessToken,
            onStatusChanged = {},
            onSessionCompleted = { result, readingSeconds, quizSeconds ->
                sessionStats[selectedMaterial.moduleId] = LearningSessionStats(
                    correctAnswers = result.correctAnswers,
                    totalQuestions = result.totalQuestions,
                    readingSeconds = readingSeconds,
                    quizSeconds = quizSeconds,
                    completedAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                )
            },
            onBack = { onAction(LearningAction.CloseModule) },
            onDestinationSelected = onDestinationSelected,
        )
    }
}

@Composable
private fun LearningListScreen(
    materials: List<LearningMaterial>,
    statusFor: (LearningMaterial) -> LearningStatus?,
    sessionStats: Map<String, LearningSessionStats>,
    onMaterialSelected: (LearningMaterial) -> Unit,
    onPracticeSelected: () -> Unit,
    onDestinationSelected: (String) -> Unit,
) {
    val completedCount = materials.count { statusFor(it)?.kind == "completed" }
    val totalProgress = if (materials.isEmpty()) 0f else materials
        .map { statusFor(it)?.progressPercent ?: 0f }
        .average().toFloat()
    val bottomNavigationPadding = waspadAIBottomNavigationContentPadding()
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = WaspadAIBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LearningPageHeader(
                    title = "Pelajari",
                    onBack = { onDestinationSelected("Periksa") },
                    showBack = false,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 20.dp, bottom = bottomNavigationPadding + 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { LearningProgressSection(completedCount, materials.size, totalProgress) }
                    item {
                        val practiceModule = materials.firstOrNull()
                        val practiceProgress = practiceModule?.let { material ->
                            val correct = material.latestCorrectAnswers
                            val total = material.latestTotalQuestions
                            if (correct != null && total != null && total > 0) {
                                (correct.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                statusFor(material)?.progressPercent?.coerceIn(0f, 1f) ?: 0f
                            }
                        } ?: 0f
                        val practiceLabel = when {
                            practiceProgress >= 1f -> "Latihan selesai"
                            practiceProgress > 0f -> "Lanjutkan latihan"
                            else -> "Mulai latihan"
                        }
                        LearningDailyMissions(
                            isCurrentMissionComplete = materials.firstOrNull()?.let { statusFor(it)?.kind == "completed" } == true,
                            onCurrentMissionClick = materials.firstOrNull()?.let { { onMaterialSelected(it) } },
                            onPracticeClick = onPracticeSelected.takeIf { materials.isNotEmpty() },
                            practiceProgress = practiceProgress,
                            practiceLabel = practiceLabel,
                        )
                    }
                    item {
                        Text(
                            "Materi untukmu",
                            modifier = Modifier.padding(horizontal = learningContentGutter()),
                            color = WaspadAIDarkBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(materials, key = { it.moduleId }) { material ->
                        LearningMaterialCard(
                            material = material,
                            status = statusFor(material),
                            sessionStats = sessionStats[material.moduleId],
                            onClick = { onMaterialSelected(material) },
                        )
                    }
                }
            }
        }
        WaspadAIBottomNavigation(
            selectedDestination = "Pelajari",
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LearningProgressSection(completedCount: Int, totalCount: Int, progress: Float) {
    val isComplete = totalCount > 0 && completedCount == totalCount
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = learningContentGutter()),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFF3C4),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color(0xFFFFE48A), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.learning_trophy),
                    contentDescription = "Ilustrasi progres belajar",
                    modifier = Modifier.size(76.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Progres belajarmu", color = WaspadAIDarkBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isComplete) "Semua materi selesai" else "$completedCount dari $totalCount materi selesai",
                    color = WaspadAIDarkBlue,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(Modifier.fillMaxWidth().height(9.dp).background(Color.White.copy(alpha = .8f), RoundedCornerShape(8.dp))) {
                    Box(
                        Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(9.dp)
                            .background(Color(0xFFE1A900), RoundedCornerShape(8.dp))
                    )
                }
                Text(
                    when {
                        isComplete -> "Hebat! Pertahankan kebiasaan verifikasi informasimu."
                        progress >= .65f -> "Sedikit lagi, lanjutkan materi yang belum selesai."
                        else -> "Lanjutkan belajar untuk memperkuat kemampuan verifikasi."
                    },
                    color = Color(0xFF685A28),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun LearningPageHeader(title: String, onBack: () -> Unit, showBack: Boolean = true) {
    WaspadAIPageHeader(
        title = title,
        onBack = onBack.takeIf { showBack },
    )
}

@Composable
private fun LearningDailyMissions(
    isCurrentMissionComplete: Boolean,
    onCurrentMissionClick: (() -> Unit)?,
    onPracticeClick: (() -> Unit)?,
    practiceProgress: Float,
    practiceLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = learningContentGutter()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Misi hari ini", color = WaspadAIDarkBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        LearningMissionRow(
            title = "Jadi detektif hoaks hari ini",
            description = "Pelajari satu materi dan jawab 3 soal.",
            icon = Icons.Rounded.Search,
            illustrationRes = R.drawable.learning_magnify,
            isComplete = isCurrentMissionComplete,
            onClick = onCurrentMissionClick,
        )
        LearningMissionRow(
            title = "Latihan",
            description = "Uji pemahamanmu lewat latihan soal.",
            icon = Icons.Rounded.MenuBook,
            illustrationRes = R.drawable.learning_practice,
            isComplete = practiceProgress >= 1f,
            onClick = onPracticeClick,
            progress = practiceProgress,
            progressLabel = practiceLabel,
        )
    }
}

@Composable
private fun LearningMissionRow(
    title: String,
    description: String,
    icon: ImageVector,
    illustrationRes: Int? = null,
    isComplete: Boolean,
    onClick: (() -> Unit)? = null,
    progress: Float? = null,
    progressLabel: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(42.dp).background(Color(0xFFFFF3C4), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                if (illustrationRes != null) {
                    Image(
                        painter = painterResource(illustrationRes),
                        contentDescription = null,
                        modifier = Modifier.size(35.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = Color(0xFF9A7000), modifier = Modifier.size(23.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = WaspadAIDarkBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(description, color = WaspadAIMuted, fontSize = 11.sp, lineHeight = 15.sp)
                if (progress != null && progressLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0xFFE7EEF2), RoundedCornerShape(5.dp)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(WaspadAIBlue, RoundedCornerShape(5.dp)),
                        )
                    }
                    Text(
                        progressLabel,
                        color = if (progress > 0f) WaspadAIBlue else WaspadAIMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier.size(24.dp).then(
                    if (isComplete) Modifier.background(WaspadAIBlue, CircleShape)
                    else Modifier.border(1.5.dp, Color(0xFF9B8B4B), CircleShape)
                ), contentAlignment = Alignment.Center,
            ) {
                if (isComplete) Icon(Icons.Rounded.Check, "Misi selesai", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LearningMaterialCard(
    material: LearningMaterial,
    status: LearningStatus?,
    sessionStats: LearningSessionStats?,
    onClick: () -> Unit,
) {
    val isInProgress = status?.kind == "in_progress"
    val isCompleted = status?.kind == "completed"
    val progress = status?.progressPercent ?: 0f
    val correctAnswers = sessionStats?.correctAnswers ?: material.latestCorrectAnswers
    val totalQuestions = sessionStats?.totalQuestions ?: material.latestTotalQuestions
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = learningContentGutter())
            .background(Color.White)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(material.illustrationTileColor, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (material.illustrationRes != null) {
                        Image(
                            painter = painterResource(material.illustrationRes),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(material.icon, contentDescription = null, tint = material.iconColor, modifier = Modifier.size(34.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        conciseLearningTitle(material.title),
                        color = WaspadAIDarkBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        conciseLearningDescription(material.title, material.description),
                        color = WaspadAIMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                    )
                    if (correctAnswers != null && totalQuestions != null) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "$correctAnswers/$totalQuestions jawaban benar",
                            color = Color(0xFF627885),
                        fontSize = 11.sp,
                        )
                    } else {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            when {
                                isCompleted -> "Selesai"
                                isInProgress -> "Lanjutkan belajar"
                                else -> "Belum dimulai"
                            },
                            color = if (isInProgress) WaspadAIBlue else WaspadAIMuted,
                            fontSize = 10.sp,
                            fontWeight = if (isInProgress) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                    if (isInProgress) {
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFFE7EEF2), RoundedCornerShape(5.dp))) {
                            Box(Modifier.fillMaxWidth(progress.coerceIn(.04f, 1f)).height(3.dp).background(WaspadAIBlue, RoundedCornerShape(5.dp)))
                        }
                    }
                }
                when (status?.kind) {
                    "completed" -> Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Materi selesai",
                        tint = Color(0xFF3F8065),
                        modifier = Modifier.size(20.dp),
                    )
                    "in_progress" -> CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(28.dp),
                        color = WaspadAIBlue,
                        trackColor = Color(0xFFDDE5EA),
                        strokeWidth = 4.dp,
                    )
                    else -> Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = if (isInProgress) "Lanjutkan materi" else "Buka materi",
                        tint = WaspadAIBlue,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        HorizontalDivider(color = Color(0xFFE1E9EE), thickness = 1.dp)
    }
}

private fun conciseLearningDescription(title: String, fallback: String): String = when {
    title.contains("Phishing", ignoreCase = true) ->
        "Kenali pencurian OTP, PIN, dan kredensial."
    title.contains("Impersonation", ignoreCase = true) ->
        "Kenali penipuan yang mengatasnamakan instansi resmi."
    title.contains("Misinformasi", ignoreCase = true) || title.contains("Hoaks", ignoreCase = true) ->
        "Belajar mengenali berita palsu dan memeriksa sumber informasi."
    else -> fallback
}

private fun conciseLearningTitle(title: String): String = when {
    title.contains("Misinformasi", ignoreCase = true) -> "Misinformasi dan Hoaks"
    else -> title
}

@Composable
private fun LearningDetailScreen(
    material: LearningMaterial,
    openQuizOnLoad: Boolean,
    savedQuestionIndex: Int,
    quizResult: id.waspadai.app.feature.learning.domain.QuizAttemptResult?,
    submitting: Boolean,
    onAnswerSelected: (String, String) -> Unit,
    selectedAnswers: Map<String, String>,
    onSubmitQuiz: (Long, Long) -> Unit,
    onLessonCompleted: (String) -> Unit,
    accessToken: String,
    onStatusChanged: (LearningStatus) -> Unit,
    onSessionCompleted: (id.waspadai.app.feature.learning.domain.QuizAttemptResult, Long, Long) -> Unit,
    onBack: () -> Unit,
    onDestinationSelected: (String) -> Unit,
) {
    var stageIndex by remember(material.title) { mutableIntStateOf(0) }
    var completedStages by remember(material.title) { mutableIntStateOf(0) }
    var showOverview by rememberSaveable(material.moduleId, openQuizOnLoad, material.questions.isNotEmpty()) {
        mutableStateOf(!openQuizOnLoad || material.questions.isEmpty())
    }
    var showQuizChoice by remember(material.title, openQuizOnLoad) { mutableStateOf(false) }
    var showQuiz by remember(material.title, openQuizOnLoad, material.questions.isNotEmpty()) {
        mutableStateOf(openQuizOnLoad && material.questions.isNotEmpty())
    }
    var readingSeconds by rememberSaveable(material.moduleId) { mutableLongStateOf(0L) }
    var readingFinished by rememberSaveable(material.moduleId) { mutableStateOf(false) }

    LaunchedEffect(material.moduleId, readingFinished, showOverview, showQuiz) {
        while (!readingFinished && !showOverview && !showQuiz) {
            delay(1_000)
            readingSeconds += 1
        }
    }

    val bottomNavigationPadding = waspadAIBottomNavigationContentPadding()
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = WaspadAIBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = bottomNavigationPadding),
            ) {
            LearningPageHeader(title = material.title, onBack = onBack)
            if (showOverview) {
                LearningMaterialOverview(
                    material = material,
                    onStart = { showOverview = false },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = learningContentGutter(), vertical = 20.dp),
                ) {
                    LearningStageStepper(
                        stages = material.stages,
                        completedStages = completedStages,
                        elapsedSeconds = readingSeconds,
                    )
                    Spacer(Modifier.height(14.dp))
                    material.stages.getOrNull(stageIndex)?.let { stage ->
                        LearningStageCard(
                            stage = stage,
                            material = material,
                            accessToken = accessToken,
                        )
                    }
                    Spacer(Modifier.height(15.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (stageIndex > 0) {
                            OutlinedButton(
                                onClick = { stageIndex -= 1 },
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WaspadAIBlue),
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Materi sebelumnya", modifier = Modifier.size(20.dp))
                            }
                        }
                        Button(
                            onClick = {
                                material.stages.getOrNull(stageIndex)?.lessonId?.takeIf { it.isNotBlank() }?.let(onLessonCompleted)
                                if (stageIndex < material.stages.lastIndex) {
                                    completedStages = maxOf(completedStages, stageIndex + 1)
                                    stageIndex += 1
                                } else {
                                    completedStages = material.stages.size
                                    readingFinished = true
                                    showQuizChoice = true
                                }
                            },
                            enabled = material.stages.isNotEmpty(),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
                        ) {
                            Text(if (stageIndex == material.stages.lastIndex) "Selesai membaca" else "Lanjut", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
        }
        }
        WaspadAIBottomNavigation(
            selectedDestination = "Pelajari",
            onDestinationSelected = onDestinationSelected,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showQuizChoice) {
        Dialog(
            onDismissRequest = { showQuizChoice = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 340.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WaspadAIValid, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Siap menguji pemahamanmu?", fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = WaspadAIDarkBlue, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Materi selesai dibaca. Yuk, uji pemahamanmu dengan latihan soal singkat.", fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF557383), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showQuizChoice = false },
                            modifier = Modifier.weight(.4f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WaspadAIBlue),
                        ) { Text("Nanti saja", fontSize = 12.sp, textAlign = TextAlign.Center) }
                        Button(
                            onClick = { showQuizChoice = false; showQuiz = true },
                            modifier = Modifier.weight(.6f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue, contentColor = Color.White),
                        ) { Text("Mulai latihan soal", fontSize = 12.sp, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
    if (showQuiz) {
        LearningQuizScreen(
            material = material,
            startQuestionIndex = savedQuestionIndex.coerceIn(0, material.questions.lastIndex),
            quizResult = quizResult,
            submitting = submitting,
            onAnswerSelected = onAnswerSelected,
            selectedAnswers = selectedAnswers,
            onSubmitQuiz = { quizSeconds -> onSubmitQuiz(readingSeconds, quizSeconds) },
            onProgress = { index -> onStatusChanged(LearningStatus("in_progress", index)) },
            onComplete = { result, quizSeconds ->
                onStatusChanged(LearningStatus("completed", material.questions.size))
                onSessionCompleted(result, readingSeconds, quizSeconds)
            },
            onDismiss = { showQuiz = false },
            onBackToLearning = {
                showQuiz = false
                onBack()
            },
        )
    }
}

@Composable
private fun LearningMaterialOverview(
    material: LearningMaterial,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = learningContentGutter(), vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .background(material.illustrationTileColor, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (material.illustrationRes != null) {
                Image(
                    painter = painterResource(material.illustrationRes),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = material.icon,
                    contentDescription = null,
                    tint = material.iconColor,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = material.title,
            color = WaspadAIDarkBlue,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = material.description,
            color = WaspadAIMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Tahapan belajar",
            modifier = Modifier.fillMaxWidth(),
            color = WaspadAIDarkBlue,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "Ikuti setiap tahap secara berurutan, lalu selesaikan latihan soal.",
            modifier = Modifier.fillMaxWidth(),
            color = WaspadAIMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            material.stages.forEachIndexed { index, stage ->
                LearningOverviewStep(
                    number = index + 1,
                    title = stage.title,
                    supportingText = "Materi",
                    color = WaspadAIBlue,
                )
            }
            LearningOverviewStep(
                number = material.stages.size + 1,
                title = "Latihan soal",
                supportingText = "${material.questions.size} soal untuk cek pemahaman",
                color = Color(0xFFC76500),
            )
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onStart,
            enabled = material.stages.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(7.dp))
            Text("Mulai materi", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LearningOverviewStep(
    number: Int,
    title: String,
    supportingText: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(width = 46.dp, height = 56.dp),
            shape = RoundedCornerShape(12.dp),
            color = color,
            shadowElevation = 2.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("TAHAP", color = Color.White.copy(alpha = .8f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                Text(number.toString().padStart(2, '0'), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(13.dp),
            color = Color.White,
            shadowElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8E4EC)),
        ) {
            Column {
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 13.dp, top = 10.dp, end = 13.dp),
                    color = WaspadAIDarkBlue,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = supportingText,
                    modifier = Modifier.padding(start = 13.dp, top = 2.dp, end = 13.dp, bottom = 10.dp),
                    color = color,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LearningStageCard(
    stage: LearningStage,
    material: LearningMaterial,
    accessToken: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8E4EC)),
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            material.imageUrl?.let {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    LearningMediaImage(material, accessToken, Modifier.widthIn(max = 230.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = stage.title,
                color = WaspadAIDarkBlue,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(7.dp))
            Markdown(
                content = stage.body,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LearningStageStepper(
    stages: List<LearningStage>,
    completedStages: Int,
    elapsedSeconds: Long,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            formatElapsed(elapsedSeconds),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = WaspadAIBlue,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(9.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            stages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(if (index < completedStages) WaspadAIBlue else WaspadAILightBlue, RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}

@Composable
private fun LearningMediaImage(material: LearningMaterial, accessToken: String, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(context, material.mediaId, material.imageUrl, accessToken) {
        val headers = NetworkHeaders.Builder().apply {
            if (accessToken.isNotBlank()) set("Authorization", "Bearer ${accessToken.trim()}")
        }.build()
        ImageRequest.Builder(context)
            .data(material.imageUrl)
            .httpHeaders(headers)
            .memoryCacheKey("learning-media:${material.mediaId}")
            .diskCacheKey("learning-media:${material.mediaId}")
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = material.imageAltText,
        modifier = modifier
            .height(138.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFD8E4EC), RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
}


@Composable
private fun LearningQuizScreen(
    material: LearningMaterial,
    startQuestionIndex: Int,
    quizResult: id.waspadai.app.feature.learning.domain.QuizAttemptResult?,
    submitting: Boolean,
    onAnswerSelected: (String, String) -> Unit,
    selectedAnswers: Map<String, String>,
    onSubmitQuiz: (Long) -> Unit,
    onProgress: (Int) -> Unit,
    onComplete: (id.waspadai.app.feature.learning.domain.QuizAttemptResult, Long) -> Unit,
    onDismiss: () -> Unit,
    onBackToLearning: () -> Unit,
) {
    var questionIndex by remember(material.title, startQuestionIndex) { mutableIntStateOf(startQuestionIndex) }
    var reviewMode by rememberSaveable(material.moduleId) { mutableStateOf(false) }
    var quizSeconds by rememberSaveable(material.moduleId) { mutableLongStateOf(0L) }
    val finished = quizResult != null
    val question = material.questions[questionIndex]
    val selectedAnswer = question.optionIds.indexOf(selectedAnswers[question.questionId])
    val allAnswered = material.questions.all { selectedAnswers.containsKey(it.questionId) }
    val feedback = quizResult?.feedback?.firstOrNull { it.questionId == question.questionId }

    LaunchedEffect(material.moduleId, finished) {
        while (!finished) {
            delay(1_000)
            quizSeconds += 1
        }
    }
    LaunchedEffect(quizResult?.attemptId) {
        quizResult?.let { onComplete(it, quizSeconds) }
    }

    fun leaveQuiz() {
        if (!finished) onProgress(questionIndex)
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::leaveQuiz,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(WaspadAIBackground)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(WaspadAIBlue),
            ) {
                Image(
                    painter = painterResource(id = id.waspadai.app.R.drawable.community_header_background),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = .68f,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::leaveQuiz) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatElapsed(quizSeconds), color = Color(0xFFCFE8F8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (reviewMode) "Tinjau jawaban" else "Cek pemahaman", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Keluar",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable(onClick = ::leaveQuiz).padding(vertical = 10.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = learningContentGutter()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    material.questions.forEach { questionItem ->
                        val answered = selectedAnswers.containsKey(questionItem.questionId)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .background(if (answered) Color(0xFFFFD95A) else Color.White.copy(alpha = .42f), RoundedCornerShape(6.dp)),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    color = WaspadAIBackground,
                ) {
                    if (finished && !reviewMode) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = learningContentGutter(), vertical = 72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WaspadAIValid, modifier = Modifier.size(72.dp))
                            Spacer(Modifier.height(18.dp))
                            Text("Latihan selesai", color = WaspadAIDarkBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("${quizResult?.correctAnswers ?: 0} dari ${quizResult?.totalQuestions ?: material.questions.size} jawaban benar", color = WaspadAIMuted, fontSize = 17.sp, textAlign = TextAlign.Center)
                            Text(formatElapsed(quizSeconds), color = WaspadAIBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(28.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = onBackToLearning,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WaspadAIBlue),
                                ) { Text("Halaman utama", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                Button(
                                    onClick = { reviewMode = true; questionIndex = 0 },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
                                ) { Text("Lihat penjelasan", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = learningContentGutter(), vertical = 20.dp),
                        ) {
                            Text(question.question, color = WaspadAIDarkBlue, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(25.dp))
                            question.answers.forEachIndexed { index, answer ->
                                val selected = selectedAnswer == index
                                val correctOption = feedback?.correctOptionId == question.optionIds.getOrNull(index) ||
                                    (feedback?.correct == true && selected)
                                val wrongSelection = reviewMode && selected && feedback?.correct == false
                                val answerBackground = when {
                                    reviewMode && correctOption -> Color(0xFFFFF1B8)
                                    wrongSelection -> Color(0xFFFCE4EA)
                                    selected -> Color(0xFFE2F1FB)
                                    else -> Color.White
                                }
                                val answerStroke = when {
                                    reviewMode && correctOption -> Color(0xFFD8A900)
                                    wrongSelection -> Color(0xFFD95C78)
                                    selected -> WaspadAIBlue
                                    else -> Color(0xFFD8E4EC)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(answerBackground)
                                        .border(1.dp, answerStroke, RoundedCornerShape(14.dp))
                                        .then(if (!reviewMode) Modifier.clickable {
                                            question.optionIds.getOrNull(index)?.let { onAnswerSelected(question.questionId, it) }
                                        } else Modifier)
                                        .padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(31.dp)
                                            .background(
                                                when {
                                                    reviewMode && correctOption -> Color(0xFFD8A900)
                                                    wrongSelection -> Color(0xFFD95C78)
                                                    selected -> WaspadAIBlue
                                                    else -> Color(0xFFE7F3FC)
                                                },
                                                RoundedCornerShape(9.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(('A'.code + index).toChar().toString(), color = if (selected || correctOption) Color.White else WaspadAIBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(answer, color = WaspadAIDarkBlue, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(11.dp))
                            }
                            if (reviewMode) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (feedback?.correct == true) Color(0xFFFFF8D9) else Color(0xFFFFF1F4),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (feedback?.correct == true) Color(0xFFE6C84C) else Color(0xFFE7A0B0),
                                    ),
                                ) {
                                    Column(modifier = Modifier.padding(13.dp)) {
                                        Text(
                                            if (feedback?.correct == true) "Jawabanmu benar" else "Jawabanmu belum tepat",
                                            color = WaspadAIDarkBlue,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(feedback?.explanation.orEmpty(), color = WaspadAIMuted, fontSize = 12.sp, lineHeight = 18.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                material.questions.forEachIndexed { index, item ->
                                    val current = index == questionIndex
                                    val answered = selectedAnswers.containsKey(item.questionId)
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(35.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (current) WaspadAIBlue else if (answered) WaspadAIValid else Color.White)
                                            .border(1.dp, if (current) WaspadAIBlue else if (answered) WaspadAIValid else Color(0xFFD8E4EC), RoundedCornerShape(9.dp))
                                            .clickable { questionIndex = index; onProgress(index) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("${index + 1}", color = if (current || answered) Color.White else WaspadAIBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            if (reviewMode) {
                                Button(
                                    onClick = {
                                        if (questionIndex < material.questions.lastIndex) questionIndex += 1 else onBackToLearning()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text(if (questionIndex == material.questions.lastIndex) "Selesai melihat" else "Pembahasan berikutnya", fontWeight = FontWeight.Bold)
                                }
                            } else if (selectedAnswer >= 0) {
                                Button(
                                    onClick = {
                                        if (questionIndex == material.questions.lastIndex) {
                                            onSubmitQuiz(quizSeconds)
                                        } else {
                                            questionIndex += 1
                                            onProgress(questionIndex)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
                                    shape = RoundedCornerShape(24.dp),
                                    enabled = !submitting && (questionIndex != material.questions.lastIndex || allAnswered),
                                ) {
                                    Text(
                                        if (submitting) "Mengirim..."
                                        else if (questionIndex == material.questions.lastIndex && !allAnswered) "Lengkapi semua jawaban"
                                        else if (questionIndex == material.questions.lastIndex) "Lihat hasil"
                                        else "Soal berikutnya",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LearningQuizDialog(
    material: LearningMaterial,
    startQuestionIndex: Int,
    onProgress: (Int) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var questionIndex by remember(material.title, startQuestionIndex) { mutableIntStateOf(startQuestionIndex) }
    var selectedAnswer by remember(material.title) { mutableIntStateOf(-1) }
    var score by remember(material.title) { mutableIntStateOf(0) }
    var finished by remember(material.title) { mutableStateOf(false) }
    val question = material.questions[questionIndex]

    Dialog(onDismissRequest = { onProgress(questionIndex); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 12.dp) {
            if (finished) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WaspadAIValid, modifier = Modifier.size(58.dp))
                    Spacer(Modifier.height(13.dp))
                    Text("Latihan selesai", color = WaspadAIDarkBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    Text("${score} dari ${material.questions.size} jawaban benar", color = WaspadAIBlue, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Terus berlatih agar semakin yakin saat memeriksa informasi.", color = WaspadAIMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue)) { Text("Kembali ke materi") }
                }
            } else {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("LATIHAN SOAL", color = WaspadAIBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Cek pemahaman", color = WaspadAIDarkBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${questionIndex + 1}/${material.questions.size}", color = WaspadAIBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(WaspadAILightBlue, RoundedCornerShape(6.dp))) {
                        Box(modifier = Modifier.fillMaxWidth((questionIndex + 1).toFloat() / material.questions.size).height(5.dp).background(WaspadAIBlue, RoundedCornerShape(6.dp)))
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(question.question, color = WaspadAIDarkBlue, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(17.dp))
                    question.answers.forEachIndexed { index, answer ->
                        val isSelected = selectedAnswer == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 9.dp)
                                .border(1.dp, if (isSelected) WaspadAIBlue else Color(0xFFD8E4EC), RoundedCornerShape(11.dp))
                                .background(if (isSelected) Color(0xFFE7F3FC) else Color.White, RoundedCornerShape(11.dp))
                                .clickable { selectedAnswer = index }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(modifier = Modifier.size(28.dp).background(if (isSelected) WaspadAIBlue else Color(0xFFE7F3FC), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                                Text(('A'.code + index).toChar().toString(), color = if (isSelected) Color.White else WaspadAIBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(answer, color = WaspadAIDarkBlue, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Button(
                        onClick = {
                            if (selectedAnswer == question.correctAnswer) score += 1
                            if (questionIndex == material.questions.lastIndex) {
                                finished = true
                                onComplete()
                            } else {
                                questionIndex += 1
                                onProgress(questionIndex)
                                selectedAnswer = -1
                            }
                        },
                        enabled = selectedAnswer >= 0,
                        modifier = Modifier.fillMaxWidth().height(47.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
                        shape = RoundedCornerShape(24.dp),
                    ) { Text(if (questionIndex == material.questions.lastIndex) "Lihat hasil" else "Pertanyaan berikutnya", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
