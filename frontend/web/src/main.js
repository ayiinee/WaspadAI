import "./styles.css";

const config = {
  apiBaseUrl: (import.meta.env.VITE_WASPADAI_API_BASE_URL || "").replace(/\/$/, ""),
  liveApiEnabled: import.meta.env.VITE_ENABLE_LIVE_API === "true",
};

const conversation = document.querySelector("#conversation");
const input = document.querySelector("#message-input");
const imageInput = document.querySelector("#image-input");
const attachmentPreview = document.querySelector("#attachment-preview");
const sendButton = document.querySelector("#send-button");
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
    supported: false,
    verdict: null,
  },
];

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

function makeCommunityPost(post) {
  const card = createElement("article", "community-post");
  const profile = createElement("div", "community-profile");
  profile.append(createElement("span", "community-avatar", post.author.split(" ").map((name) => name[0]).slice(0, 2).join("")));
  const byline = createElement("div", "");
  byline.append(createElement("strong", "", post.author), createElement("small", "", post.timestamp));
  profile.append(byline);
  card.append(profile, createElement("p", "community-post-copy", post.body));
  card.append(createElement("div", "community-evidence", "Bukti visual sedang diperiksa"));

  const assessment = createElement("section", `community-assessment${post.assessmentOpen ? " open" : ""}`);
  const assessmentTrigger = createElement(
    "button",
    "assessment-trigger",
    post.verdict ? `Penilaian: ${post.verdict}` : "Beri penilaian",
  );
  assessmentTrigger.type = "button";
  assessmentTrigger.setAttribute("aria-expanded", String(Boolean(post.assessmentOpen)));
  assessmentTrigger.addEventListener("click", () => { post.assessmentOpen = !post.assessmentOpen; renderCommunity(); });
  assessment.append(assessmentTrigger);

  const panel = createElement("div", "assessment-panel");
  panel.append(createElement("p", "assessment-label", "Pilih penilaian"));
  const choices = createElement("div", "assessment-choices");
  [["Hoaks", "hoaks"], ["Waspada", "waspada"], ["Valid", "valid"]].forEach(([label, style]) => {
    const button = createElement("button", `verdict-button ${style}${post.verdict === label ? " selected" : ""}`, post.verdict === label ? `✓ ${label}` : label);
    button.type = "button";
    button.addEventListener("click", () => { post.verdict = label; renderCommunity(); });
    choices.append(button);
  });
  const reasonLabel = createElement("label", "assessment-reason-label", "Alasan penilaian");
  const reason = document.createElement("textarea");
  reason.className = "assessment-reason";
  reason.placeholder = "Jelaskan alasan atau temuan Anda...";
  reason.value = post.reason || "";
  reason.addEventListener("input", () => { post.reason = reason.value; });
  reasonLabel.append(reason);
  const evidenceLabel = createElement("label", "evidence-upload");
  evidenceLabel.append(createElement("span", "", "＋ Tambahkan gambar atau bukti"));
  const evidenceInput = document.createElement("input");
  evidenceInput.type = "file";
  evidenceInput.accept = "image/jpeg,image/png,image/webp";
  evidenceInput.addEventListener("change", () => {
    evidenceLabel.querySelector("span").textContent = evidenceInput.files?.[0]?.name || "＋ Tambahkan gambar atau bukti";
  });
  evidenceLabel.append(evidenceInput);
  const submitAssessment = createElement("button", "assessment-submit", "Kirim penilaian");
  submitAssessment.type = "button";
  submitAssessment.addEventListener("click", () => {
    post.assessmentOpen = false;
    renderCommunity();
  });
  panel.append(choices, reasonLabel, evidenceLabel, submitAssessment);
  assessment.append(panel);
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
  const icon = createElement("span", "attachment-icon", "APK");
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
  if (file.size > 8_000_000) {
    state.messages.push({ kind: "status", error: true, text: "Ukuran gambar melebihi batas 8 MB." });
    imageInput.value = "";
    render();
    return;
  }
  state.attachment = file;
  showAttachmentPreview();
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

function setSelectedTab(container, destination) {
  container.querySelectorAll("[data-tab]").forEach((button) => {
    const selected = button.dataset.tab === destination;
    button.classList.toggle("active", selected);
    button.setAttribute("aria-pressed", String(selected));
  });
}

function showVerification() {
  communityScreen.hidden = true;
  appShell.hidden = false;
  setSelectedTab(primaryNav, "Periksa");
}

function showCommunity() {
  appShell.hidden = true;
  communityScreen.hidden = false;
  setSelectedTab(communityNav, "Koneksi");
  renderCommunity();
}

document.querySelectorAll("[data-tab]").forEach((tab) => {
  tab.addEventListener("click", () => {
    const destination = tab.dataset.tab;
    if (destination === "Koneksi") showCommunity();
    else if (destination === "Periksa") showVerification();
    else {
      state.messages.push({ kind: "status", text: `${destination} belum tersedia pada slicing ini.` });
      render();
    }
  });
});

document.querySelector("#community-back").addEventListener("click", showVerification);
communitySearchInput.addEventListener("input", renderCommunity);
communityFilter.addEventListener("change", renderCommunity);

render();
