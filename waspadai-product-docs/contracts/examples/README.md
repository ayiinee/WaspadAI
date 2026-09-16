# Current Contract Examples

Status: `CURRENT`.

- `text-request.json`: body Product API untuk `POST /api/v1/verifications/text`; Bearer token dan `Idempotency-Key` berada di header.
- `verification-response.json`: wrapper Product yang valid dan jujur berlabel `execution_mode=MOCK`.
- `validation-error.json`: error envelope aman Product API.

Image dan community evidence belum memiliki fixture current karena masih `TARGET`. Bentuk target berada pada kontrak kanonik, bukan pada fixture runtime ini.

