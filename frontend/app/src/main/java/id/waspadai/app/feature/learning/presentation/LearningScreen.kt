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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.waspadai.app.core.ui.WaspadAIBottomNavigation
import id.waspadai.app.ui.theme.WaspadAIBackground
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAIContribution
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAILightBlue
import id.waspadai.app.ui.theme.WaspadAIMuted
import id.waspadai.app.ui.theme.WaspadAIValid

private data class LearningStage(val title: String, val body: String)

private data class LearningQuestion(
    val question: String,
    val answers: List<String>,
    val correctAnswer: Int,
)

private data class LearningMaterial(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val stages: List<LearningStage>,
    val questions: List<LearningQuestion>,
)

private data class LearningStatus(
    val kind: String,
    val questionIndex: Int = 0,
)

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
    onDestinationSelected: (String) -> Unit = {},
) {
    var selectedMaterial by remember { mutableStateOf<LearningMaterial?>(null) }
    var progressEntries by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun statusFor(material: LearningMaterial): LearningStatus? = progressEntries
        .firstOrNull { it.startsWith("${material.title}|") }
        ?.split('|')
        ?.let { parts -> LearningStatus(parts.getOrNull(1).orEmpty(), parts.getOrNull(2)?.toIntOrNull() ?: 0) }

    fun updateStatus(material: LearningMaterial, status: LearningStatus) {
        progressEntries = progressEntries
            .filterNot { it.startsWith("${material.title}|") }
            .plus("${material.title}|${status.kind}|${status.questionIndex}")
    }

    if (selectedMaterial == null) {
        LearningListScreen(
            statusFor = ::statusFor,
            onMaterialSelected = {
                updateStatus(it, statusFor(it) ?: LearningStatus("in_progress"))
                selectedMaterial = it
            },
            onDestinationSelected = onDestinationSelected,
        )
    } else {
        LearningDetailScreen(
            material = selectedMaterial!!,
            savedQuestionIndex = statusFor(selectedMaterial!!)?.questionIndex ?: 0,
            onStatusChanged = { updateStatus(selectedMaterial!!, it) },
            onBack = { selectedMaterial = null },
            onDestinationSelected = onDestinationSelected,
        )
    }
}

@Composable
private fun LearningListScreen(
    statusFor: (LearningMaterial) -> LearningStatus?,
    onMaterialSelected: (LearningMaterial) -> Unit,
    onDestinationSelected: (String) -> Unit,
) {
    Scaffold(
        containerColor = WaspadAIBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            WaspadAIBottomNavigation(
                selectedDestination = "Pelajari",
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LearningPageHeader(title = "Pelajari", onBack = { onDestinationSelected("Periksa") })
            LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                ,
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LearningChallengeFixed()
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text("LANGKAH BERIKUTNYA", color = WaspadAIMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("Target hari ini", color = WaspadAIDarkBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("1 dari 3", color = WaspadAIBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(WaspadAILightBlue, RoundedCornerShape(8.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(.34f)
                                .height(6.dp)
                                .background(WaspadAIBlue, RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
            items(learningMaterials) { material ->
                LearningMaterialCard(material = material, status = statusFor(material), onClick = { onMaterialSelected(material) })
            }
            }
        }
    }
}

@Composable
private fun LearningHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaspadAIDarkBlue)
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.widthIn(max = 270.dp)) {
            Text("RUANG BELAJAR", color = Color(0xFFCFE8F8), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Pelajari", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Bangun kebiasaan mengenali informasi yang aman.", color = Color(0xFFE0F0F8), fontSize = 12.sp, lineHeight = 17.sp)
        }
        Column(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = .15f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("✦", color = Color(0xFFFFDC6B), fontSize = 14.sp)
            Text("3", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp)
            Text("hari", color = Color(0xFFD9EEF8), fontSize = 9.sp)
        }
    }
}

@Composable
private fun LearningPageHeader(title: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WaspadAIBlue)
            .statusBarsPadding()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = id.waspadai.app.R.drawable.community_header_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = .6f,
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LearningChallengeFixed() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .background(WaspadAIContribution, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(Color.White, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("⚡", color = Color.White, fontSize = 19.sp) }
        Box(modifier = Modifier.size(0.dp).offset(x = (-38).dp).alpha(0f), contentAlignment = Alignment.Center) {
            Text("⚡", color = Color(0xFFFFDC6B), fontSize = 19.sp)
        }
        Box(modifier = Modifier.size(0.dp).offset(x = (-38).dp), contentAlignment = Alignment.Center) {
            Text("\u26A1", color = Color(0xFFFFDC6B), fontSize = 19.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Jadi detektif hoaks hari ini", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Jawab 3 skenario singkat sebelum waktunya habis.", color = Color.Black, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Black, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun LearningChallenge() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .border(1.dp, Color(0xFFF2CF67), RoundedCornerShape(10.dp))
            .background(WaspadAIContribution, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(WaspadAIBlue, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("⚡", color = Color.White, fontSize = 19.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("MISI KILAT · TERBATAS", color = Color(0xFF936D00), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp)
            Text("Jadi detektif hoaks hari ini", color = WaspadAITextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Jawab 3 skenario singkat sebelum waktunya habis.", color = Color.Black, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color.Black, modifier = Modifier.size(25.dp))
    }
}

@Composable
private fun LearningMaterialCard(
    material: LearningMaterial,
    status: LearningStatus?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .border(1.dp, if (material == learningMaterials.first()) Color(0xFF7EAECA) else Color(0xFFD8E4EC), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(material.iconColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(material.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(material.title, color = WaspadAIDarkBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(1.dp))
            Text(material.description, color = WaspadAIMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        when (status?.kind) {
            "completed" -> Box(
                modifier = Modifier
                    .size(26.dp)
                    .border(2.dp, WaspadAIValid, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = "Materi selesai", tint = WaspadAIValid, modifier = Modifier.size(17.dp)) }
            "in_progress" -> Icon(Icons.Rounded.PlayArrow, contentDescription = "Materi sedang dipelajari", tint = WaspadAIBlue, modifier = Modifier.size(25.dp))
            else -> Icon(Icons.Rounded.ChevronRight, contentDescription = "Buka materi", tint = Color.Black, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun LearningDetailScreen(
    material: LearningMaterial,
    savedQuestionIndex: Int,
    onStatusChanged: (LearningStatus) -> Unit,
    onBack: () -> Unit,
    onDestinationSelected: (String) -> Unit,
) {
    var stageIndex by remember(material.title) { mutableIntStateOf(0) }
    var completedStages by remember(material.title) { mutableIntStateOf(0) }
    var showQuizChoice by remember(material.title) { mutableStateOf(false) }
    var showQuiz by remember(material.title) { mutableStateOf(false) }

    Scaffold(
        containerColor = WaspadAIBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            WaspadAIBottomNavigation(
                selectedDestination = "Pelajari",
                onDestinationSelected = onDestinationSelected,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LearningPageHeader(title = material.title, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TAHAP ${stageIndex + 1} DARI ${material.stages.size}", color = WaspadAIMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Text("${(completedStages * 100) / material.stages.size}%", color = WaspadAIBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(7.dp).background(WaspadAILightBlue, RoundedCornerShape(8.dp))) {
                    Box(modifier = Modifier.fillMaxWidth(completedStages.toFloat() / material.stages.size).height(7.dp).background(WaspadAIBlue, RoundedCornerShape(8.dp)))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 20.dp)
                        .border(1.dp, Color(0xFF9FC4D8), RoundedCornerShape(14.dp))
                        .background(Color(0xFFF2FAFE), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, tint = Color(0xFF9FC4D8), modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(7.dp))
                        Text("Placeholder materi visual", color = WaspadAIDarkBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Area ilustrasi/video dapat diisi nanti.", color = WaspadAIMuted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(17.dp))
                LearningStageCard(stage = material.stages[stageIndex], index = stageIndex)
                Spacer(Modifier.height(16.dp))
                material.stages.forEachIndexed { index, stage ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { stageIndex = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(if (index < stageIndex) WaspadAIValid else if (index == stageIndex) WaspadAIBlue else WaspadAIBackground, CircleShape)
                                .border(1.dp, if (index <= stageIndex) WaspadAIBlue else WaspadAILightBlue, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index < stageIndex) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) else Text("${index + 1}", color = if (index == stageIndex) Color.White else WaspadAIMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(stage.title, color = if (index == stageIndex) WaspadAIBlue else WaspadAIMuted, fontSize = 12.sp, fontWeight = if (index == stageIndex) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.height(15.dp))
                Button(
                    onClick = {
                        if (stageIndex < material.stages.lastIndex) {
                            completedStages = maxOf(completedStages, stageIndex + 1)
                            stageIndex += 1
                        } else {
                            completedStages = material.stages.size
                            showQuizChoice = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue),
                ) {
                    Text(if (stageIndex == material.stages.lastIndex) "Selesai membaca" else "Lanjut ke tahap berikutnya", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(19.dp))
                }
            }
        }
    }

    if (showQuizChoice) {
        AlertDialog(
            onDismissRequest = { showQuizChoice = false },
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = WaspadAIValid, modifier = Modifier.size(42.dp)) },
            title = { Text("Siap menguji pemahamanmu?", fontWeight = FontWeight.Bold) },
            text = { Text("Kamu sudah menyelesaikan materi ini. Lanjutkan dengan latihan soal singkat atau kembali ke daftar materi.") },
            confirmButton = { Button(onClick = { showQuizChoice = false; showQuiz = true }) { Text("Mulai latihan soal") } },
            dismissButton = { OutlinedButton(onClick = { showQuizChoice = false }) { Text("Nanti saja") } },
        )
    }
    if (showQuiz) {
        LearningQuizScreen(
            material = material,
            startQuestionIndex = savedQuestionIndex.coerceIn(0, material.questions.lastIndex),
            onProgress = { index -> onStatusChanged(LearningStatus("in_progress", index)) },
            onComplete = { onStatusChanged(LearningStatus("completed", material.questions.size)) },
            onDismiss = { showQuiz = false },
        )
    }
}

@Composable
private fun LearningStageCard(stage: LearningStage, index: Int) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 4.dp) {
        Row(modifier = Modifier.padding(17.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(modifier = Modifier.size(35.dp).background(Color(0xFFE7F3FC), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text("${index + 1}".padStart(2, '0'), color = WaspadAIBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("TAHAP ${index + 1}", color = WaspadAIBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                Spacer(Modifier.height(4.dp))
                Text(stage.title, color = WaspadAIDarkBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(stage.body, color = Color(0xFF557383), fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun LearningQuizScreen(
    material: LearningMaterial,
    startQuestionIndex: Int,
    onProgress: (Int) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var questionIndex by remember(material.title, startQuestionIndex) { mutableIntStateOf(startQuestionIndex) }
    var selectedAnswer by remember(material.title, startQuestionIndex) { mutableIntStateOf(-1) }
    var score by remember(material.title, startQuestionIndex) { mutableIntStateOf(0) }
    var finished by remember(material.title, startQuestionIndex) { mutableStateOf(false) }
    val question = material.questions[questionIndex]

    fun leaveQuiz() {
        if (!finished) onProgress(questionIndex)
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::leaveQuiz,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(WaspadAIBlue)) {
            Image(
                painter = painterResource(id = id.waspadai.app.R.drawable.community_header_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = .55f,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::leaveQuiz) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Text("Lesson ${questionIndex + 1}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("Skip", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = ::leaveQuiz).padding(horizontal = 5.dp, vertical = 10.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().height(18.dp).padding(horizontal = 6.dp).background(Color(0xFFD9D9D9), RoundedCornerShape(20.dp))) {
                    Box(modifier = Modifier.fillMaxWidth((questionIndex + 1).toFloat() / material.questions.size).height(18.dp).background(Color(0xFFFFD95A), RoundedCornerShape(20.dp)))
                }
                if (finished) {
                    Column(modifier = Modifier.fillMaxSize().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFFFFD95A), modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(18.dp))
                        Text("Latihan selesai", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("${score} dari ${material.questions.size} jawaban benar", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(28.dp))
                        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD95A)), modifier = Modifier.fillMaxWidth(.82f)) {
                            Text("Kembali ke materi", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 30.dp, bottom = 28.dp)) {
                        Text(question.question, color = Color.White, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(28.dp))
                        question.answers.forEachIndexed { index, answer ->
                            val selected = selectedAnswer == index
                            val isCorrect = selected && index == question.correctAnswer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (selected) Color(0xFFFFD95A) else Color.Transparent)
                                    .border(1.dp, if (selected) Color(0xFFFFD95A) else Color.White, RoundedCornerShape(24.dp))
                                    .clickable { selectedAnswer = index }
                                    .padding(horizontal = 16.dp, vertical = 23.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(answer, color = if (selected) Color.Black else Color.White, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                if (isCorrect) Icon(Icons.Rounded.Check, contentDescription = "Jawaban benar", tint = WaspadAIBlue, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.height(22.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 15.dp, top = 2.dp)) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, tint = Color(0xFFFFD95A), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("See explanation", color = Color(0xFFFFD95A), fontSize = 14.sp, modifier = Modifier.clickable { })
                        }
                        if (selectedAnswer >= 0) {
                            Spacer(Modifier.height(23.dp))
                            Button(
                                onClick = {
                                    if (selectedAnswer == question.correctAnswer) score += 1
                                    if (questionIndex == material.questions.lastIndex) {
                                        finished = true
                                        onComplete()
                                    } else {
                                        questionIndex += 1
                                        selectedAnswer = -1
                                        onProgress(questionIndex)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD95A)),
                                shape = RoundedCornerShape(24.dp),
                            ) { Text(if (questionIndex == material.questions.lastIndex) "Lihat hasil" else "Lanjut", color = Color.Black, fontWeight = FontWeight.Bold) }
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

private val WaspadAITextColor = Color(0xFF15212A)
