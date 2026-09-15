# Testing Strategy

Status: `CURRENT`.

## Test pyramid

### Unit test

- validasi text 10 dan 25.000 karakter serta boundary invalid;
- `question` 500 karakter;
- enum `sender_context`;
- mapper direct response tanpa `result` wrapper;
- narrative null/missing/malformed behavior yang aman;
- HTTP status ke UI error category;
- image format, byte limit, dimensi, dan total pixel;
- redaction pada network logging.

### Contract test

- parse [`text-request.json`](../../contracts/examples/text-request.json);
- parse [`verification-response.json`](../../contracts/examples/verification-response.json);
- parse FastAPI validation error fixture;
- assertion base URL/path current;
- assertion tidak ada `Authorization` dan `X-Waspadai-API-Key` pada request AI;
- multipart memakai field `image`, bukan Base64;
- `output_mode=BOTH` pada text dan image.

### Integration test

Jalankan terhadap environment remote yang disetujui dengan data sintetis. Catat timestamp, base URL, build, request ID, status, dan durasi. Jangan memasukkan content sensitif ke artifact test.

Minimum scenarios: health, text success, URL-only text, image success, validation 422, payload 413, media 415, rate-limit/error mapping bila environment mendukung, serta timeout/cancel UX.

### UI/device test

- login gate;
- form validation dan preserved input;
- preview/crop screenshot;
- loading hingga 120 detik tanpa duplicate submit;
- narrative utama dan expandable detail;
- rotation/process recreation sesuai policy;
- offline, timeout, retry manual, dan accessibility.

## Future test isolation

Test untuk wrapper Product, history, community, vote, RLS, atau internal API key harus berada pada suite future/backend. Fixture future tidak boleh dipakai untuk membuat current Android contract test lulus.

## Documentation quality gate

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-docs.ps1
```

Quality gate dokumentasi wajib dijalankan pada PR yang mengubah API, topology, auth, atau contoh payload.

