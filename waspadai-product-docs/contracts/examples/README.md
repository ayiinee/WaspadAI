# Current Contract Examples

Status: `CURRENT`.

- `text-request.json`: JSON body untuk public text verification.
- `verification-response.json`: direct WaspadAI success response tanpa wrapper Product.
- `validation-error.json`: bentuk error validasi FastAPI.

Image request tidak direpresentasikan sebagai JSON karena wire format-nya `multipart/form-data` dengan field binary `image`, optional `question`, dan `output_mode=BOTH`.

