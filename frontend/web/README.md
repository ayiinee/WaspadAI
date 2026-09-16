# WaspadAI Browser Demo

Frontend ini adalah demo browser untuk melihat dan mencoba UI WaspadAI. Default-nya memakai simulasi lokal; tidak ada token Supabase, service-role key, database credential, atau internal AI key di browser.

## Jalankan

```powershell
Set-Location WaspadAI\frontend\web
npm install
npm run dev
```

Buka URL yang ditampilkan Vite, biasanya `http://127.0.0.1:5173`.

## Mode API publik (opsional)

Salin `.env.example` menjadi `.env.local`, lalu set `VITE_ENABLE_LIVE_API=true`. Hanya base URL publik yang boleh diisi. API browser harus mengizinkan origin Vite melalui CORS; bila tidak, aplikasi menampilkan error aman dan tidak mengganti hasil dengan simulasi.

Jangan pernah menaruh `X-Waspadai-API-Key`, Supabase service-role key, password database, atau credential server pada file environment web.
