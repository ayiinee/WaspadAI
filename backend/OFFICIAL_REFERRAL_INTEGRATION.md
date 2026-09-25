# Official Referral Integration

WaspadAI AI `official_referral` → Product Backend `AIResult` →
`resolve_official_referral` → trusted channel directory → Android result card →
explicit confirmation → external official channel.

The AI decides whether a referral is needed and supplies route types and priority.
The Product Backend preserves that decision and resolves destinations from its own
directory. Android presents the resolved routes. Verdict and risk level remain
independent from the safety referral. No report, evidence, or personal data is sent
automatically.

| AI route type | Product resolution |
| --- | --- |
| `FINANCIAL_SCAM_REPORTING` | Verified OJK IASC HTTPS channel |
| `FINANCIAL_PROVIDER` | Guidance to contact the user's own bank or payment provider |
| `ACCOUNT_PROVIDER` | Account recovery guidance |
| `PLATFORM_REPORTING` | Platform reporting guidance |
| `DEVICE_RECOVERY` | Device recovery guidance |
| `OFFICIAL_INSTITUTION` | Generic official institution guidance |

The configured directory is currently a reviewed tuple in `app/official_referral.py`.
It can be replaced by a database repository when channel administration is added.

For a visible referral, Product also offers separate, optional government reporting
channels: Komdigi [Aduan Nomor](https://jdih.komdigi.go.id/infografis/view/58)
for suspicious phone numbers and [Aduan Konten](https://jdih.komdigi.go.id/infografis/view/59)
for suspicious links/sites. These are clearly labelled by reporting subject and
the user chooses which subject applies. They do not modify AI status, route type,
or priority; they are not automatic reports. IASC remains exclusive to the
`FINANCIAL_SCAM_REPORTING` AI route.

The directory currently contains the OJK Indonesia Anti-Scam Centre (IASC) for
`FINANCIAL_SCAM_REPORTING` only. OJK [identifies `iasc.ojk.go.id` as its sole
official IASC reporting site](https://ojk.go.id/id/berita-dan-kegiatan/info-terkini/Pages/Waspada-Penipuan-Website-Mengatasnamakan-Indonesia-Anti-Scam-Centre-IASC.aspx).
Inactive and non-HTTPS channels are never resolved. All other known route types
produce guidance without an external link. Unknown route types are skipped.

The result includes both the original `official_referral` and Product-generated
`resolved_official_referral`. Existing stored results without the new fields use
the `NOT_REQUIRED` default. If resolution fails, verification still completes.

Future work: add a reviewed Local Government Directory indexed by structured
region and institution context, with verified URLs and lifecycle management. Do
not infer a local government or user region from narrative text. No new location
permission is needed.
