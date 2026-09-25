"""Trusted Product destinations for structured AI referral routes."""

from __future__ import annotations

from datetime import date
from urllib.parse import urlsplit

from app.schemas.verification import (
    OfficialChannel,
    OfficialReferral,
    OfficialReportingOption,
    ResolvedOfficialReferral,
    ResolvedRoute,
)

# The IASC host is confirmed by OJK: https://ojk.go.id/id/berita-dan-kegiatan/
# info-terkini/Pages/Waspada-Penipuan-Website-Mengatasnamakan-Indonesia-Anti-Scam-Centre-IASC.aspx
CHANNELS: tuple[OfficialChannel, ...] = (
    OfficialChannel(
        id="ojk-iasc",
        route_type="FINANCIAL_SCAM_REPORTING",
        organization_name="Otoritas Jasa Keuangan",
        channel_name="Indonesia Anti-Scam Centre",
        description="Pelaporan penipuan transaksi keuangan dan upaya penanganan dana.",
        destination_url="https://iasc.ojk.go.id/",
        verified_at=date(2026, 9, 25),
    ),
)

# Optional government reporting destinations. These are user-selected, not AI routes.
# Komdigi documents Aduan Nomor at https://jdih.komdigi.go.id/infografis/view/58
# and Aduan Konten at https://jdih.komdigi.go.id/infografis/view/59.
GOVERNMENT_REPORTING_CHANNELS: tuple[OfficialChannel, ...] = (
    OfficialChannel(
        id="komdigi-aduan-nomor",
        route_type="SUSPICIOUS_NUMBER",
        organization_name="Kementerian Komunikasi dan Digital",
        channel_name="Aduan Nomor",
        description="Laporkan nomor seluler yang mencurigakan atau diduga terkait penipuan.",
        destination_url="https://aduannomor.id/",
        verified_at=date(2026, 9, 25),
    ),
    OfficialChannel(
        id="komdigi-aduan-konten",
        route_type="SUSPICIOUS_CONTENT",
        organization_name="Kementerian Komunikasi dan Digital",
        channel_name="Aduan Konten",
        description="Laporkan tautan atau situs mencurigakan melalui kanal pengaduan konten.",
        destination_url="https://www.aduankonten.id/",
        verified_at=date(2026, 9, 25),
    ),
)

GUIDANCE = {
    "OFFICIAL_INSTITUTION": (
        "Konfirmasi ke instansi resmi",
        "Konfirmasi melalui kanal resmi instansi terkait.",
    ),
    "ACCOUNT_PROVIDER": (
        "Amankan akun",
        "Gunakan pusat bantuan resmi layanan tempat akunmu terdaftar untuk mengamankan akun.",
    ),
    "FINANCIAL_PROVIDER": (
        "Hubungi bank / penyedia pembayaran",
        "Hubungi bank atau penyedia pembayaran yang kamu gunakan melalui kanal resminya.",
    ),
    "FINANCIAL_SCAM_REPORTING": (
        "Laporkan penipuan finansial",
        "Siapkan bukti dan gunakan kanal pelaporan resmi.",
    ),
    "PLATFORM_REPORTING": (
        "Laporkan ke platform",
        "Gunakan fitur pelaporan resmi pada platform terkait.",
    ),
    "DEVICE_RECOVERY": (
        "Amankan perangkat",
        "Amankan perangkat dan ikuti panduan pemulihan resmi penyedia perangkat.",
    ),
}


def is_safe_channel_url(value: str) -> bool:
    try:
        parsed = urlsplit(value)
        return (
            parsed.scheme == "https"
            and bool(parsed.hostname)
            and not parsed.username
            and not parsed.password
        )
    except ValueError:
        return False


def resolve_official_referral(
    referral: OfficialReferral,
    channels: tuple[OfficialChannel, ...] = CHANNELS,
    region_code: str | None = None,
    government_reporting_channels: tuple[OfficialChannel, ...] = GOVERNMENT_REPORTING_CHANNELS,
) -> ResolvedOfficialReferral:
    if referral.status == "NOT_REQUIRED":
        return ResolvedOfficialReferral()
    routes: list[ResolvedRoute] = []
    for route in referral.routes:
        copy = GUIDANCE.get(route.route_type)
        if copy is None:
            continue
        channel = next(
            (
                item
                for item in channels
                if item.route_type == route.route_type
                and item.is_active
                and is_safe_channel_url(item.destination_url)
                and (item.region_code is None or item.region_code == region_code)
            ),
            None,
        )
        routes.append(
            ResolvedRoute(
                route_type=route.route_type,
                priority=route.priority,
                reason=route.reason,
                action_type="EXTERNAL_URL" if channel else "GUIDANCE_ONLY",
                title=channel.channel_name if channel else copy[0],
                guidance=None if channel else copy[1],
                channel=channel,
            )
        )
    routes.sort(key=lambda item: item.priority != "PRIMARY")
    reporting_options = [
        OfficialReportingOption(
            subject=channel.route_type,
            title=channel.channel_name,
            description=channel.description,
            channel=channel,
        )
        for channel in government_reporting_channels
        if channel.route_type in {"SUSPICIOUS_NUMBER", "SUSPICIOUS_CONTENT"}
        and channel.is_active
        and is_safe_channel_url(channel.destination_url)
    ]
    return ResolvedOfficialReferral(
        status=referral.status,
        mode=referral.mode,
        summary=referral.summary,
        routes=routes,
        government_reporting_options=reporting_options,
    )
