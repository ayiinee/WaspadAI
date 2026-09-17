# WaspadAI Browser Demo

Frontend ini adalah demo browser untuk melihat dan mencoba UI WaspadAI. Default-nya memakai simulasi lokal; tidak ada token Supabase, service-role key, database credential, atau internal AI key di browser.

## Jalankan

```powershell
Set-Location WaspadAI\frontend\web
npm install
npm run dev
```

Buka URL yang ditampilkan Vite, biasanya `http://localhost:5173`. Karena server
development mendengarkan jaringan lokal, halaman ini juga dapat dibuka dari perangkat
lain pada Wi-Fi yang sama melalui URL `http://IP-KOMPUTER:5173` (izinkan bila Windows
Firewall menampilkan permintaan akses).

Untuk membuat berkas statis siap unggah ke hosting, jalankan:

```powershell
npm run build
```

Hasilnya berada di folder `dist/`.

## Mode API publik (opsional)

Salin `.env.example` menjadi `.env.local`, lalu set `VITE_ENABLE_LIVE_API=true`. Hanya base URL publik yang boleh diisi. API browser harus mengizinkan origin Vite melalui CORS; bila tidak, aplikasi menampilkan error aman dan tidak mengganti hasil dengan simulasi.

Jangan pernah menaruh `X-Waspadai-API-Key`, Supabase service-role key, password database, atau credential server pada file environment web.
