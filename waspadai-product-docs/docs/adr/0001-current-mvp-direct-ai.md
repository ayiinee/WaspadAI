# ADR-0001: Direct Public AI untuk MVP Android

- Status: Superseded oleh ADR-0002
- Date: 2026-09-15

Keputusan lama mengizinkan Android memanggil public WaspadAI langsung. Keputusan tersebut tidak lagi berlaku. File dipertahankan agar perubahan topology dapat diaudit.

Android production sekarang hanya memanggil Product API. Public WaspadAI tetap dapat dipakai website demo atau pengujian terpisah, tetapi bukan boundary aplikasi mobile.

