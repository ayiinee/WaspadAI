import "./styles.css";

const config = {
  apiBaseUrl: (import.meta.env.VITE_WASPADAI_API_BASE_URL || "").replace(/\/$/, ""),
  liveApiEnabled: import.meta.env.VITE_ENABLE_LIVE_API === "true",
};

const conversation = document.querySelector("#conversation");
const input = document.querySelector("#message-input");
const imageInput = document.querySelector("#image-input");
const fileInput = document.querySelector("#file-input");
const attachmentPreview = document.querySelector("#attachment-preview");
const sendButton = document.querySelector("#send-button");
const modeToggle = document.querySelector("#mode-toggle");
const attachButton = document.querySelector("#attach-button");
const attachMenu = document.querySelector("#attach-menu");
const choosePhoto = document.querySelector("#choose-photo");
const chooseFile = document.querySelector("#choose-file");
const authScreen = document.querySelector("#auth-screen");
const appShell = document.querySelector("#app-shell");
const authForm = document.querySelector("#auth-form");
const authEmail = document.querySelector("#auth-email");
const authPassword = document.querySelector("#auth-password");
const authConfirmation = document.querySelector("#auth-confirm-password");
const confirmationField = document.querySelector("#confirm-password-field");
const authError = document.querySelector("#auth-error");
const authFormTitle = document.querySelector("#auth-form-title");
const authSubmit = document.querySelector("#auth-submit");
const signInTab = document.querySelector("#sign-in-tab");
const signUpTab = document.querySelector("#sign-up-tab");
const communityScreen = document.querySelector("#community-screen");
const communityDetailScreen = document.querySelector("#community-detail-screen");
const learnScreen = document.querySelector("#learn-screen");
const learnMaterials = document.querySelector("#learn-materials");
const learnSearchInput = document.querySelector("#learn-search-input");
const materialScreen = document.querySelector("#material-screen");
const materialContent = document.querySelector("#material-content");
const quizChoiceModal = document.querySelector("#quiz-choice-modal");
const quizScreen = document.querySelector("#quiz-screen");
const quizBody = document.querySelector("#quiz-body");
const quizCount = document.querySelector("#quiz-count");
const quizProgressBar = document.querySelector("#quiz-progress-bar");
const communityDetailContent = document.querySelector("#community-detail-content");
const communityFeed = document.querySelector("#community-feed");
const communitySearchInput = document.querySelector("#community-search-input");
const communityFilter = document.querySelector("#community-filter");

let isSignUp = false;

const state = {
  busy: false,
  attachment: null,
  messages: [
    {
      kind: "user",
      text: "Aku barusan dapat chat seperti ini di WhatsApp.",
      attachment: "Lihat Pesanan.apk",
    },
    {
      kind: "analysis",
      risk: "Tinggi",
      text: "Pesan ini berisiko tinggi karena meminta Anda menginstal file APK dari WhatsApp dan memberikan akses SMS. Jangan lanjutkan instruksi pengirim sebelum identitas dan sumber aplikasi dapat diverifikasi.",
      reasons: [
        "File APK dapat berasal dari sumber yang belum terverifikasi.",
        "Aplikasi mencurigakan dapat meminta akses SMS dan membaca kode OTP.",
      ],
      actions: [
        "Jangan membuka aplikasi atau memberikan izin tambahan.",
        "Verifikasi pengirim melalui kanal resmi sebelum menindaklanjuti pesan.",
      ],
      isSample: true,
    },
  ],
};

const communityPosts = [
  {
    id: "prabowo-video",
    author: "Putu Alvin Mahendra",
    timestamp: "10 Agustus 2026 | 10.17 WITA",
    body: "Beredar potongan video yang mengatasnamakan Presiden Prabowo di media sosial. Komunitas sedang melakukan pengecekan terhadap sumber asli dan konteks informasi untuk memastikan apakah informasi tersebut benar atau menyesatkan.",
    supportCount: 10,
    commentCount: 5,
    hoaxCount: 8,
    cautionCount: 14,
    validCount: 3,
    supported: false,
    verdict: null,
  },
  {
    id: "gibran-position",
    author: "Rifqi Aditya Nugroho",
    timestamp: "10 Agustus 2026 | 10.17 WITA",
    body: "Beredar unggahan yang menyebutkan adanya pencopotan Gibran dari jabatannya sebagai Wakil Presiden. Informasi ini masih perlu diperiksa dengan membandingkan sumber resmi dan konteks pemberitaan untuk memastikan kebenarannya.",
    supportCount: 10,
    commentCount: 5,
    hoaxCount: 6,
    cautionCount: 11,
    validCount: 4,
    supported: false,
    verdict: null,
  },
];

const learningMaterials = [
  { icon: "✓", tone: "blue", title: "Kenali ciri-ciri hoaks", description: "Temukan tanda-tanda informasi yang perlu diperiksa ulang.", active: true },
  { icon: "⌁", tone: "violet", title: "Periksa sumber informasi", description: "Latih kebiasaan mengecek sumber sebelum percaya atau berbagi." },
  { icon: "!", tone: "orange", title: "Aman dari modus penipuan", description: "Kenali pola pesan yang meminta data pribadi atau uang." },
  { icon: "↗", tone: "green", title: "Bagikan informasi dengan bijak", description: "Pahami langkah sederhana sebelum meneruskan sebuah kabar." },
];

const materialLessons = {
  "Kenali ciri-ciri hoaks": {
    stages: [
      { title: "Apa itu hoaks?", text: "Hoaks adalah informasi palsu atau menyesatkan yang dibuat seolah-olah benar. Hoaks dapat memancing rasa takut, marah, atau terburu-buru agar kita langsung percaya dan membagikannya." },
      { title: "Perhatikan judul dan bahasa", text: "Judul yang terlalu heboh, huruf kapital berlebihan, dan kalimat seperti ‘sebarkan sekarang juga’ adalah tanda untuk berhenti sejenak. Baca isi lengkapnya, bukan hanya judul." },
      { title: "Gunakan tiga pertanyaan", text: "Tanyakan: siapa sumbernya, kapan informasi dibuat, dan apa buktinya? Jika satu saja belum jelas, simpan dulu informasi tersebut dan lakukan pemeriksaan ulang." },
    ],
    quiz: [
      { question: "Kalimat mana yang paling perlu dicurigai?", answers: ["Baca laporan lengkap di situs resmi", "Sebarkan sekarang juga sebelum dihapus!", "Data dirangkum dari tiga sumber"], correct: 1 },
      { question: "Langkah pertama saat menerima kabar mengejutkan adalah...", answers: ["Langsung meneruskan ke grup", "Mengecek sumber dan tanggalnya", "Menghapus semua pesan"], correct: 1 },
      { question: "Jika bukti sebuah klaim belum jelas, sebaiknya...", answers: ["Menunda membagikan dan memeriksa ulang", "Menambahkan opini agar lebih meyakinkan", "Meminta orang lain menyebarkannya"], correct: 0 },
    ],
  },
  "Periksa sumber informasi": {
    stages: [
      { title: "Cari sumber pertama", text: "Telusuri siapa yang pertama kali menerbitkan informasi. Sumber asli biasanya memiliki konteks, tanggal, dan identitas yang dapat diperiksa." },
      { title: "Bandingkan dengan sumber tepercaya", text: "Bandingkan klaim dengan situs pemerintah, media kredibel, atau pernyataan resmi. Jangan hanya mengandalkan satu unggahan yang beredar." },
      { title: "Cek konteks gambar", text: "Gambar lama dapat digunakan kembali untuk cerita baru. Gunakan pencarian gambar atau baca keterangan lengkap sebelum menarik kesimpulan." },
    ],
    quiz: [
      { question: "Sumber yang paling kuat untuk memeriksa kebijakan baru adalah...", answers: ["Pesan berantai tanpa tautan", "Akun resmi lembaga terkait", "Komentar anonim"], correct: 1 },
      { question: "Mengapa tanggal publikasi perlu diperiksa?", answers: ["Agar tahu konteks dan kebaruan informasi", "Supaya unggahan terlihat populer", "Karena semua informasi lama pasti salah"], correct: 0 },
      { question: "Apa yang dilakukan saat dua sumber berbeda?", answers: ["Pilih yang paling sering dibagikan", "Bandingkan bukti dan kredibilitasnya", "Sebarkan keduanya tanpa catatan"], correct: 1 },
    ],
  },
  "Aman dari modus penipuan": {
    stages: [
      { title: "Kenali tanda tekanan", text: "Penipu sering membuat situasi terasa mendesak: akun akan diblokir, hadiah harus diambil hari ini, atau keluarga sedang butuh uang." },
      { title: "Lindungi data rahasia", text: "OTP, PIN, kata sandi, dan kode pemulihan tidak boleh diberikan melalui chat. Pihak resmi tidak akan meminta data rahasia tersebut." },
      { title: "Verifikasi lewat kanal resmi", text: "Tutup percakapan lalu hubungi nomor resmi dari situs atau aplikasi. Jangan memakai nomor atau tautan yang diberikan oleh pengirim pesan." },
    ],
    quiz: [
      { question: "Data yang tidak boleh dibagikan lewat chat adalah...", answers: ["Nama panggilan", "OTP dan PIN", "Jam bertemu"], correct: 1 },
      { question: "Saat diminta transfer dengan segera, lakukan...", answers: ["Verifikasi melalui kanal resmi", "Kirim sebagian dulu", "Balas dengan foto identitas"], correct: 0 },
      { question: "Tautan hadiah yang mencurigakan sebaiknya...", answers: ["Dibuka di perangkat lain", "Diteruskan ke teman", "Tidak dibuka dan dihapus"], correct: 2 },
    ],
  },
  "Bagikan informasi dengan bijak": {
    stages: [
      { title: "Berhenti sejenak", text: "Sebelum menekan tombol bagikan, periksa apakah informasi itu benar, bermanfaat, dan tidak merugikan orang lain." },
      { title: "Tulis konteks dengan jelas", text: "Jika informasi sudah terverifikasi, sertakan sumber dan konteksnya. Hindari potongan kalimat yang bisa membuat orang salah paham." },
      { title: "Hormati privasi", text: "Hapus nomor telepon, alamat, dan identitas pribadi sebelum membagikan tangkapan layar atau cerita ke ruang publik." },
    ],
    quiz: [
      { question: "Kebiasaan baik sebelum membagikan kabar adalah...", answers: ["Mengecek kebenaran dan konteks", "Menambahkan judul yang lebih heboh", "Menyembunyikan sumber"], correct: 0 },
      { question: "Mengapa sumber perlu dicantumkan?", answers: ["Agar pembaca bisa memeriksa ulang", "Supaya pesan lebih panjang", "Agar terlihat viral"], correct: 0 },
      { question: "Informasi pribadi dalam tangkapan layar sebaiknya...", answers: ["Dibiarkan agar lengkap", "Dihapus atau disamarkan", "Diperbesar"], correct: 1 },
    ],
  },
};

function setAuthMode(signUp) {
  isSignUp = signUp;
  signInTab.classList.toggle("active", !signUp);
  signUpTab.classList.toggle("active", signUp);
  signInTab.setAttribute("aria-selected", String(!signUp));
  signUpTab.setAttribute("aria-selected", String(signUp));
  confirmationField.hidden = !signUp;
  authConfirmation.required = signUp;
  authPassword.autocomplete = signUp ? "new-password" : "current-password";
  authFormTitle.textContent = signUp ? "Buat akun" : "Selamat datang kembali";
  authSubmit.textContent = signUp ? "Buat akun" : "Masuk";
  authError.hidden = true;
}

function showAuthError(message) {
  authError.textContent = message;
  authError.hidden = false;
}

signInTab.addEventListener("click", () => setAuthMode(false));
signUpTab.addEventListener("click", () => setAuthMode(true));
authForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const email = authEmail.value.trim();
  const password = authPassword.value;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    showAuthError("Masukkan alamat email yang valid.");
    return;
  }
  if (password.length < 8) {
    showAuthError("Kata sandi minimal terdiri dari 8 karakter.");
    return;
  }
  if (isSignUp && password !== authConfirmation.value) {
    showAuthError("Konfirmasi kata sandi belum sama.");
    return;
  }
  authScreen.hidden = true;
  appShell.hidden = false;
  input.focus();
});

function renderCommunity() {
  const query = communitySearchInput.value.trim().toLowerCase();
  const filter = communityFilter.value;
  const posts = communityPosts.filter((post) => {
    const matchesQuery = !query || `${post.author} ${post.timestamp} ${post.body}`.toLowerCase().includes(query);
    const matchesFilter = filter === "all" || (filter === "rated" ? post.verdict : !post.verdict);
    return matchesQuery && matchesFilter;
  });
  if (!posts.length) {
    communityFeed.innerHTML = '<p class="community-empty">Kasus tidak ditemukan.<br /><small>Coba kata kunci atau filter lain.</small></p>';
    return;
  }
  communityFeed.replaceChildren(...posts.map((post) => makeCommunityPost(post)));
}

function showCommunityDetail(post) {
  appShell.hidden = true;
  communityScreen.hidden = true;
  communityDetailScreen.hidden = false;
  syncSelectedTab("Koneksi");
  renderCommunityDetail(post);
}

function renderCommunityDetail(post) {
  communityDetailContent.innerHTML = `
    <header class="detail-header"><button id="detail-back" type="button" aria-label="Kembali">←</button><h1>Detail Kasus</h1></header>
    <article class="detail-body">
      <section class="detail-profile"><span class="community-avatar">${post.author.split(" ").map((name) => name[0]).slice(0, 2).join("")}</span><div><strong>${post.author}</strong><small>${post.timestamp}</small></div><button type="button" class="connect-button">Terhubung</button></section>
      <p class="detail-caption">${post.body}</p>
      <div class="detail-image">Bukti visual dari komunitas</div>
      <section class="detail-info"><h2>Informasi unggahan</h2><p>Diunggah oleh: ${post.author}</p><p>Tanggal unggah: ${post.timestamp}</p><p>Status: Sedang ditinjau komunitas</p></section>
      <section class="detail-insight"><h2>Insight komunitas</h2><div><p class="insight-hoaks"><b>${post.hoaxCount}</b><span>Hoaks</span></p><p class="insight-waspada"><b>${post.cautionCount}</b><span>Waspada</span></p><p class="insight-valid"><b>${post.validCount}</b><span>Valid</span></p></div></section>
      <section class="detail-assessment"><button id="detail-assessment-toggle" class="assessment-trigger" type="button">${post.verdict ? `Penilaian: ${post.verdict}` : "Beri penilaian"}</button><div id="detail-assessment-panel" class="assessment-panel${post.detailAssessmentOpen ? " open" : ""}"><p class="assessment-label">Pilih penilaian</p><div class="assessment-choices"><button class="verdict-button hoaks${post.verdict === "Hoaks" ? " selected" : ""}" type="button">${post.verdict === "Hoaks" ? "✓ " : ""}Hoaks</button><button class="verdict-button waspada${post.verdict === "Waspada" ? " selected" : ""}" type="button">${post.verdict === "Waspada" ? "✓ " : ""}Waspada</button><button class="verdict-button valid${post.verdict === "Valid" ? " selected" : ""}" type="button">${post.verdict === "Valid" ? "✓ " : ""}Valid</button></div><label class="assessment-reason-label">Alasan penilaian<textarea class="assessment-reason" placeholder="Jelaskan alasan atau temuan Anda...">${post.reason || ""}</textarea></label><label class="evidence-upload"><span>＋ Tambahkan gambar atau bukti</span><input type="file" accept="image/jpeg,image/png,image/webp" /></label><button id="detail-submit" class="assessment-submit" type="button">Kirim penilaian</button><p id="detail-notice" class="detail-notice" hidden></p></div></section>
      <section class="detail-actions"><button id="detail-support" type="button" class="${post.supported ? "supported" : ""}">${post.supported ? "♥" : "♡"} ${post.supportCount}</button><span>◌ ${post.commentCount} komentar</span><span>↗ Bagikan</span></section>
    </article>`;

  communityDetailContent.querySelector("#detail-back").addEventListener("click", showCommunity);
  communityDetailContent.querySelector("#detail-assessment-toggle").addEventListener("click", () => {
    post.detailAssessmentOpen = !post.detailAssessmentOpen;
    renderCommunityDetail(post);
  });
  communityDetailContent.querySelectorAll(".verdict-button").forEach((button) => {
    button.addEventListener("click", () => {
      post.verdict = button.textContent.replace("✓", "").trim();
      post.detailAssessmentOpen = true;
      renderCommunityDetail(post);
    });
  });
  const reason = communityDetailContent.querySelector(".assessment-reason");
  reason.addEventListener("input", () => { post.reason = reason.value; });
  const evidence = communityDetailContent.querySelector(".evidence-upload input");
  evidence.addEventListener("change", () => {
    evidence.closest("label").querySelector("span").textContent = evidence.files?.[0]?.name || "＋ Tambahkan gambar atau bukti";
  });
  communityDetailContent.querySelector("#detail-submit").addEventListener("click", () => {
    const notice = communityDetailContent.querySelector("#detail-notice");
    notice.textContent = "Desain penilaian tersimpan secara lokal.";
    notice.hidden = false;
  });
  communityDetailContent.querySelector("#detail-support").addEventListener("click", () => {
    post.supported = !post.supported;
    post.supportCount += post.supported ? 1 : -1;
    renderCommunityDetail(post);
  });
}

function makeCommunityPost(post) {
  const card = createElement("article", "community-post");
  const profile = createElement("div", "community-profile");
  profile.append(createElement("span", "community-avatar", post.author.split(" ").map((name) => name[0]).slice(0, 2).join("")));
  const byline = createElement("div", "");
  byline.append(createElement("strong", "", post.author), createElement("small", "", post.timestamp));
  profile.append(byline);
  card.append(profile, createElement("p", "community-post-copy", post.body));
  card.append(createElement("div", "community-evidence", "Bukti visual sedang diperiksa"));

  const assessment = createElement("section", "community-assessment");
  const assessmentTrigger = createElement(
    "button",
    "assessment-trigger",
    "Beri penilaian",
  );
  assessmentTrigger.type = "button";
  assessmentTrigger.addEventListener("click", () => showCommunityDetail(post));
  assessment.append(assessmentTrigger);
  const actions = createElement("div", "community-actions");
  const support = createElement("button", post.supported ? "supported" : "", `${post.supported ? "♥" : "♡"} ${post.supportCount}`);
  support.type = "button";
  support.addEventListener("click", () => { post.supported = !post.supported; post.supportCount += post.supported ? 1 : -1; renderCommunity(); });
  const comments = createElement("span", "", `◌ ${post.commentCount}`);
  const share = createElement("button", "", "↗ Share");
  share.type = "button";
  share.addEventListener("click", async () => {
    const text = `${post.body}\n\nDibagikan dari WaspadAI`;
    if (navigator.share) await navigator.share({ title: "Kasus WaspadAI", text });
    else await navigator.clipboard?.writeText(text);
  });
  actions.append(support, comments, share);
  card.append(assessment, actions);
  return card;
}

function createElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text) element.textContent = text;
  return element;
}

function makeAttachment(name) {
  const attachment = createElement("div", "attachment-card");
  const inner = createElement("div", "attachment-inner");
  inner.append(createElement("p", "attachment-label", "Contoh lampiran"));
  const file = createElement("div", "attachment-file");
  const extension = name.split(".").pop()?.toUpperCase() || "FILE";
  const isPhoto = /\.(png|jpe?g|webp)$/i.test(name);
  const icon = createElement("span", "attachment-icon", isPhoto ? "IMG" : extension.slice(0, 4));
  const details = createElement("div", "attachment-details");
  details.append(createElement("strong", "", name));
  details.append(createElement("small", "", "5,1 MB · APK"));
  file.append(icon, details);
  inner.append(file);
  attachment.append(inner);
  return attachment;
}

function makeUserMessage(message) {
  const container = createElement("article", "message user-message");
  if (message.attachment) container.append(makeAttachment(message.attachment));
  const bubble = createElement("p", "bubble", message.text);
  container.append(bubble);
  return container;
}

function makeList(title, entries) {
  const section = createElement("section", "analysis-section");
  section.append(createElement("h3", "", title));
  const list = document.createElement("ul");
  entries.forEach((entry) => list.append(createElement("li", "", entry)));
  section.append(list);
  return section;
}

function makeAnalysisMessage(message) {
  const card = createElement("article", "message analysis-card");
  const title = createElement("div", "analysis-title");
  title.append(createElement("span", "analysis-title-icon", "✦"));
  title.append(createElement("h2", "", "Hasil Analisis"));
  card.append(title);
  if (message.isSample) card.append(createElement("p", "sample-label", "CONTOH TAMPILAN"));
  card.append(createElement("p", "analysis-copy", message.text));
  if (message.reasons?.length) card.append(makeList("Mengapa berisiko", message.reasons));
  if (message.actions?.length) card.append(makeList("Tindakan yang disarankan", message.actions));
  const risk = createElement("p", `risk risk-${message.risk.toLowerCase()}`, `Status Risiko: ${message.risk}`);
  card.append(risk);
  return card;
}

function makeStatusMessage(text, isError = false) {
  return createElement("p", isError ? "status-message error" : "status-message", text);
}

function render() {
  conversation.replaceChildren();
  state.messages.forEach((message) => {
    if (message.kind === "user") conversation.append(makeUserMessage(message));
    if (message.kind === "analysis") conversation.append(makeAnalysisMessage(message));
    if (message.kind === "status") conversation.append(makeStatusMessage(message.text, message.error));
  });
  if (state.busy) conversation.append(makeStatusMessage("WaspadAI sedang menganalisis…"));
  conversation.scrollTop = conversation.scrollHeight;
}

function makeMockAnalysis(text) {
  const lowerText = text.toLowerCase();
  if (["otp", "pin", "password", "kode verifikasi"].some((word) => lowerText.includes(word))) {
    return {
      kind: "analysis",
      risk: "Tinggi",
      text: "Waspada. Jangan pernah mengirim OTP, PIN, kata sandi, atau kode verifikasi kepada siapa pun. Pihak resmi tidak akan meminta data tersebut lewat chat.",
      reasons: ["Data keamanan akun diminta melalui chat.", "Pesan mendesak dapat digunakan untuk membuat korban panik."],
      actions: ["Jangan membalas dengan kode apa pun.", "Hubungi layanan resmi melalui nomor atau aplikasi resmi."],
    };
  }
  if (["hadiah", "menang", "transfer", "rekening", "link"].some((word) => lowerText.includes(word))) {
    return {
      kind: "analysis",
      risk: "Sedang",
      text: "Pesan ini perlu diverifikasi. Jangan klik tautan atau mengirim uang sebelum mengecek nomor pengirim dan kanal resmi terkait.",
      reasons: ["Klaim hadiah atau permintaan transfer sering dipakai dalam modus penipuan.", "Sumber pesan belum dapat diverifikasi pada demo ini."],
      actions: ["Periksa informasi di situs atau akun resmi.", "Jangan memasukkan data pribadi pada tautan yang dikirim lewat chat."],
    };
  }
  return {
    kind: "analysis",
    risk: "Sedang",
    text: "Pesan telah dianalisis dalam mode simulasi. Periksa pengirim, jangan membuka tautan mencurigakan, dan jangan membagikan data pribadi.",
    reasons: ["Identitas pengirim belum diverifikasi.", "Konteks pesan saja belum cukup untuk menyatakan klaim aman."],
    actions: ["Bandingkan informasi dengan kanal resmi.", "Tunda tindakan yang meminta data, uang, atau pemasangan aplikasi."],
  };
}

async function requestLiveAnalysis(text, attachment) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 120_000);
  try {
    let response;
    if (attachment) {
      const form = new FormData();
      form.append("image", attachment);
      form.append("output_mode", "BOTH");
      response = await fetch(`${config.apiBaseUrl}/api/v1/verify/image`, {
        method: "POST",
        body: form,
        signal: controller.signal,
      });
    } else {
      response = await fetch(`${config.apiBaseUrl}/api/v1/verify/text`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ text, output_mode: "BOTH", sender_context: "UNKNOWN_NUMBER" }),
        signal: controller.signal,
      });
    }
    if (!response.ok) throw new Error(`API mengembalikan HTTP ${response.status}.`);
    const payload = await response.json();
    const narrative = payload?.presentation?.narrative?.text;
    if (!narrative) throw new Error("Respons API tidak memiliki narrative yang dapat ditampilkan.");
    return {
      kind: "analysis",
      risk: payload.risk_level === "HIGH" ? "Tinggi" : payload.risk_level === "LOW" ? "Rendah" : "Sedang",
      text: narrative,
      reasons: payload.why || [],
      actions: (payload.recommended_actions || []).map((action) => action.title || action.detail).filter(Boolean),
    };
  } finally {
    window.clearTimeout(timeout);
  }
}

function showAttachmentPreview() {
  attachmentPreview.replaceChildren();
  if (!state.attachment) {
    attachmentPreview.hidden = true;
    return;
  }
  attachmentPreview.hidden = false;
  attachmentPreview.append(makeAttachment(state.attachment.name));
  const remove = createElement("button", "remove-attachment", "Hapus");
  remove.type = "button";
  remove.addEventListener("click", () => {
    state.attachment = null;
    imageInput.value = "";
    showAttachmentPreview();
  });
  attachmentPreview.append(remove);
}

function setAttachment(file) {
  if (!file) return;
  if (file.size > 8_000_000) {
    state.messages.push({ kind: "status", error: true, text: "Ukuran lampiran melebihi batas 8 MB." });
    render();
    return;
  }
  state.attachment = file;
  showAttachmentPreview();
}

function closeAttachMenu() {
  attachMenu.hidden = true;
  attachButton.setAttribute("aria-expanded", "false");
}

attachButton.addEventListener("click", () => {
  const open = attachMenu.hidden;
  attachMenu.hidden = !open;
  attachButton.setAttribute("aria-expanded", String(open));
});
choosePhoto.addEventListener("click", () => { closeAttachMenu(); imageInput.click(); });
chooseFile.addEventListener("click", () => { closeAttachMenu(); fileInput.click(); });
document.addEventListener("click", (event) => {
  if (!event.target.closest(".attach-menu-wrap")) closeAttachMenu();
});
modeToggle.addEventListener("click", () => {
  const active = modeToggle.getAttribute("aria-pressed") === "true";
  modeToggle.setAttribute("aria-pressed", String(!active));
  modeToggle.classList.toggle("inactive", active);
  modeToggle.setAttribute("aria-label", active ? "Aktifkan pemeriksaan pesan" : "Nonaktifkan pemeriksaan pesan");
});

async function submit() {
  const text = input.value.trim();
  const attachment = state.attachment;
  if (state.busy || (!attachment && text.length < 10)) {
    if (!state.busy) {
      state.messages.push({ kind: "status", error: true, text: "Masukkan minimal 10 karakter atau pilih gambar untuk diperiksa." });
      render();
    }
    return;
  }

  const displayText = text || "Gambar dikirim untuk diperiksa.";
  state.messages.push({ kind: "user", text: displayText, attachment: attachment?.name });
  input.value = "";
  state.attachment = null;
  imageInput.value = "";
  showAttachmentPreview();
  state.busy = true;
  sendButton.disabled = true;
  render();

  try {
    const result = config.liveApiEnabled && config.apiBaseUrl
      ? await requestLiveAnalysis(displayText, attachment)
      : await new Promise((resolve) => window.setTimeout(() => resolve(makeMockAnalysis(displayText)), 700));
    state.messages.push(result);
  } catch (error) {
    state.messages.push({
      kind: "status",
      error: true,
      text: `Pemeriksaan belum berhasil: ${error.message} Silakan coba lagi.`,
    });
  } finally {
    state.busy = false;
    sendButton.disabled = false;
    render();
  }
}

imageInput.addEventListener("change", () => {
  const file = imageInput.files?.[0];
  if (!file) return;
  setAttachment(file);
  imageInput.value = "";
});
fileInput.addEventListener("change", () => {
  setAttachment(fileInput.files?.[0]);
  fileInput.value = "";
});

sendButton.addEventListener("click", submit);
input.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    submit();
  }
});

const primaryNav = appShell.querySelector(".bottom-nav");
const communityNav = primaryNav.cloneNode(true);
communityNav.id = "community-nav";
document.querySelector("#community-nav").replaceWith(communityNav);
const communityDetailNav = primaryNav.cloneNode(true);
communityDetailNav.id = "community-detail-nav";
document.querySelector("#community-detail-nav").replaceWith(communityDetailNav);
const learnNav = primaryNav.cloneNode(true);
learnNav.id = "learn-nav";
document.querySelector("#learn-nav").replaceWith(learnNav);
const materialNav = primaryNav.cloneNode(true);
materialNav.id = "material-nav";
document.querySelector("#material-nav").replaceWith(materialNav);

let currentMaterial = null;
let currentStage = 0;
let currentQuizQuestion = 0;
let quizScore = 0;
let quizAnswered = false;

function setSelectedTab(container, destination) {
  container.querySelectorAll("[data-tab]").forEach((button) => {
    const selected = button.dataset.tab === destination;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-pressed", String(selected));
  });
}

// Setiap halaman memakai salinan bottom nav yang berbeda. Sinkronkan semuanya
// agar tidak ada salinan yang menyimpan status aktif dari halaman sebelumnya.
function syncSelectedTab(destination) {
  [primaryNav, communityNav, communityDetailNav, learnNav, materialNav].forEach((nav) => {
    setSelectedTab(nav, destination);
  });
}

function showVerification() {
  communityScreen.hidden = true;
  communityDetailScreen.hidden = true;
  learnScreen.hidden = true;
  materialScreen.hidden = true;
  quizChoiceModal.hidden = true;
  quizScreen.hidden = true;
  appShell.hidden = false;
  syncSelectedTab("Periksa");
}

function renderLearning() {
  const query = learnSearchInput.value.trim().toLowerCase();
  const materials = learningMaterials.filter((material) =>
    !query || `${material.title} ${material.description}`.toLowerCase().includes(query),
  );
  learnMaterials.replaceChildren(...materials.map((material) => {
    const card = createElement("button", `learn-material-card${material.active ? " is-active" : ""}`);
    card.type = "button";
    card.addEventListener("click", () => showMaterial(material));
    const icon = createElement("span", `learn-material-icon ${material.tone}`, material.icon);
    icon.setAttribute("aria-hidden", "true");
    const copy = createElement("div", "learn-material-copy");
    copy.append(createElement("h3", "", material.title), createElement("p", "", material.description));
    const arrow = createElement("span", "learn-arrow", "›");
    arrow.setAttribute("aria-hidden", "true");
    card.append(icon, copy, arrow);
    return card;
  }));
  if (!materials.length) learnMaterials.append(createElement("p", "learn-empty", "Materi tidak ditemukan. Coba kata kunci lain."));
}

function showLearning() {
  appShell.hidden = true;
  communityScreen.hidden = true;
  communityDetailScreen.hidden = true;
  materialScreen.hidden = true;
  quizChoiceModal.hidden = true;
  quizScreen.hidden = true;
  learnScreen.hidden = false;
  syncSelectedTab("Pelajari");
  renderLearning();
}

function showMaterial(material) {
  currentMaterial = material;
  currentStage = 0;
  appShell.hidden = true;
  communityScreen.hidden = true;
  communityDetailScreen.hidden = true;
  learnScreen.hidden = true;
  materialScreen.hidden = false;
  quizChoiceModal.hidden = true;
  quizScreen.hidden = true;
  syncSelectedTab("Pelajari");
  renderMaterial();
}

function renderMaterial() {
  const lesson = materialLessons[currentMaterial.title] || { stages: [], quiz: [] };
  const stage = lesson.stages[currentStage];
  const isLast = currentStage === lesson.stages.length - 1;
  materialContent.innerHTML = `
    <header class="material-hero">
      <button id="material-back" class="material-back-button" type="button" aria-label="Kembali ke daftar materi">←</button>
      <div class="material-hero-copy"><p class="material-kicker">MATERI ${learningMaterials.indexOf(currentMaterial) + 1} DARI ${learningMaterials.length}</p><h1>${currentMaterial.title}</h1><p>${currentMaterial.description}</p></div>
      <span class="material-hero-mark" aria-hidden="true">${currentMaterial.icon}</span>
    </header>
    <section class="material-body">
      <div class="material-progress-row"><span>TAHAP ${currentStage + 1} DARI ${lesson.stages.length}</span><strong>${Math.round(((currentStage + 1) / lesson.stages.length) * 100)}%</strong></div>
      <div class="material-progress"><span style="width: ${((currentStage + 1) / lesson.stages.length) * 100}%"></span></div>
      <div class="material-placeholder" role="img" aria-label="Placeholder media pembelajaran"><span aria-hidden="true">▧</span><strong>Placeholder materi visual</strong><small>Area ini dapat diisi ilustrasi atau video pembelajaran nanti.</small></div>
      <article class="material-stage-card"><span class="material-stage-number">${String(currentStage + 1).padStart(2, "0")}</span><div><p class="material-kicker">TAHAP ${currentStage + 1}</p><h2>${stage.title}</h2><p>${stage.text}</p></div></article>
      <div class="material-stage-list" aria-label="Tahapan materi">${lesson.stages.map((item, index) => `<button class="material-stage-dot${index === currentStage ? " current" : ""}${index < currentStage ? " done" : ""}" type="button" data-stage="${index}"><span>${index < currentStage ? "✓" : index + 1}</span>${item.title}</button>`).join("")}</div>
      <button id="material-next" class="material-primary-button" type="button">${isLast ? "Selesai membaca" : "Lanjut ke tahap berikutnya"}<span aria-hidden="true">→</span></button>
    </section>`;
  materialContent.querySelector("#material-back").addEventListener("click", showLearning);
  materialContent.querySelectorAll("[data-stage]").forEach((button) => button.addEventListener("click", () => {
    currentStage = Number(button.dataset.stage);
    renderMaterial();
  }));
  materialContent.querySelector("#material-next").addEventListener("click", () => {
    if (!isLast) { currentStage += 1; renderMaterial(); return; }
    quizChoiceModal.hidden = false;
  });
}

function renderQuiz() {
  const quiz = materialLessons[currentMaterial.title]?.quiz || [];
  const item = quiz[currentQuizQuestion];
  if (!item) return;
  quizCount.textContent = `${currentQuizQuestion + 1}/${quiz.length}`;
  quizProgressBar.style.width = `${((currentQuizQuestion + 1) / quiz.length) * 100}%`;
  quizBody.innerHTML = `<p class="quiz-eyebrow">PERTANYAAN ${currentQuizQuestion + 1}</p><h3>${item.question}</h3><div class="quiz-answers">${item.answers.map((answer, index) => `<button class="quiz-answer" type="button" data-answer="${index}"><span>${String.fromCharCode(65 + index)}</span>${answer}</button>`).join("")}</div><button id="quiz-next" class="material-primary-button quiz-next" type="button" disabled>${currentQuizQuestion === quiz.length - 1 ? "Lihat hasil" : "Pertanyaan berikutnya"}<span aria-hidden="true">→</span></button>`;
  quizAnswered = false;
  quizBody.querySelectorAll("[data-answer]").forEach((button) => button.addEventListener("click", () => {
    if (quizAnswered) return;
    quizAnswered = true;
    const selected = Number(button.dataset.answer);
    if (selected === item.correct) { quizScore += 1; button.classList.add("correct"); } else { button.classList.add("incorrect"); quizBody.querySelector(`[data-answer="${item.correct}"]`).classList.add("correct"); }
    quizBody.querySelectorAll(".quiz-answer").forEach((answer) => { answer.disabled = true; });
    quizBody.querySelector("#quiz-next").disabled = false;
  }));
  quizBody.querySelector("#quiz-next").addEventListener("click", () => {
    if (currentQuizQuestion === quiz.length - 1) { renderQuizResult(quiz.length); return; }
    currentQuizQuestion += 1;
    renderQuiz();
  });
}

function renderQuizResult(total) {
  quizCount.textContent = "Selesai";
  quizProgressBar.style.width = "100%";
  quizBody.innerHTML = `<div class="quiz-result"><div class="quiz-result-icon">${quizScore >= 2 ? "✓" : "↻"}</div><p class="material-kicker">HASIL LATIHAN</p><h3>${quizScore} dari ${total} jawaban benar</h3><p>${quizScore >= 2 ? "Mantap! Kamu sudah memahami langkah dasar untuk mengenali informasi yang menyesatkan." : "Tidak apa-apa. Baca ulang tahap materi yang masih terasa sulit lalu coba lagi."}</p><button id="quiz-result-action" class="material-primary-button" type="button">${quizScore >= 2 ? "Kembali ke materi" : "Ulangi latihan"}<span aria-hidden="true">→</span></button></div>`;
  quizBody.querySelector("#quiz-result-action").addEventListener("click", () => {
    if (quizScore < 2) { currentQuizQuestion = 0; quizScore = 0; renderQuiz(); return; }
    quizScreen.hidden = true;
    showLearning();
  });
}

function startQuiz() {
  quizChoiceModal.hidden = true;
  quizScreen.hidden = false;
  currentQuizQuestion = 0;
  quizScore = 0;
  renderQuiz();
}

document.querySelector("#quiz-start-button").addEventListener("click", startQuiz);
document.querySelector("#quiz-later-button").addEventListener("click", () => { quizChoiceModal.hidden = true; });
document.querySelector("#quiz-choice-close").addEventListener("click", () => { quizChoiceModal.hidden = true; });
document.querySelector("#quiz-back").addEventListener("click", () => { quizScreen.hidden = true; });

function showCommunity() {
  appShell.hidden = true;
  communityDetailScreen.hidden = true;
  learnScreen.hidden = true;
  materialScreen.hidden = true;
  quizChoiceModal.hidden = true;
  quizScreen.hidden = true;
  communityScreen.hidden = false;
  syncSelectedTab("Koneksi");
  renderCommunity();
}

document.querySelectorAll("[data-tab]").forEach((tab) => {
  tab.addEventListener("click", () => {
    const destination = tab.dataset.tab;
    if (destination === "Koneksi") showCommunity();
    else if (destination === "Periksa") showVerification();
    else if (destination === "Pelajari") showLearning();
    else {
      state.messages.push({ kind: "status", text: `${destination} belum tersedia pada slicing ini.` });
      render();
    }
  });
});

document.querySelector("#community-back").addEventListener("click", showVerification);
communitySearchInput.addEventListener("input", renderCommunity);
communityFilter.addEventListener("change", renderCommunity);
learnSearchInput.addEventListener("input", renderLearning);

render();
