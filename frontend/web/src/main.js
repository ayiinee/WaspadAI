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
    },
  ],
};

function createElement(tag, className, text) {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text) element.textContent = text;
  return element;
}

function makeAttachment(name) {
  const attachment = createElement("div", "attachment-card");
  const icon = createElement("span", "attachment-icon", "IMG");
  const details = createElement("div", "attachment-details");
  details.append(createElement("strong", "", name));
  details.append(createElement("small", "", "Lampiran untuk diperiksa"));
  attachment.append(icon, details);
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

document.querySelectorAll("[data-tab]").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll("[data-tab]").forEach((button) => button.classList.remove("active"));
    tab.classList.add("active");
  });
});

render();
