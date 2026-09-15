# Web Architecture Opsional

[Kembali ke indeks arsitektur sistem](system-and-folder-architecture.md)

## 5. Web opsional

```text
frontend/web/
├── src/app/{login,moderation,contributions,community,learning}/
├── src/components/{ui,layout,feedback}/
├── src/features/{moderation,contributions,community}/
├── src/lib/{api,auth,validation}/
├── src/hooks/
├── src/types/
├── public/
├── tests/
├── package.json
├── package-lock.json
├── next.config.ts
└── .env.example
```

UI moderator bukan auth boundary: endpoint server tetap memeriksa role. Body Markdown user disanitasi saat render; tidak mengaktifkan raw HTML/script. `NEXT_PUBLIC_*` hanya URL/public key, tidak ada service secret. Web AI yang sudah ada tetap bisa dipakai sebagai demo AI milik Shafwan; bukan bukti bahwa Product web telah tersedia.
