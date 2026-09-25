from datetime import date

from app.official_referral import OfficialChannel, resolve_official_referral
from app.schemas.verification import OfficialReferral


def referral(status="URGENT", mode="RECOVERY", routes=None):
    return OfficialReferral.model_validate({"status": status, "mode": mode, "routes": routes or []})


def route(kind, priority="PRIMARY"):
    return {"route_type": kind, "priority": priority, "reason": "Gunakan kanal resmi."}


def test_not_required_is_authoritative_even_with_routes():
    result = resolve_official_referral(
        referral("NOT_REQUIRED", None, [route("FINANCIAL_SCAM_REPORTING")])
    )
    assert result.status == "NOT_REQUIRED"
    assert result.routes == []
    assert result.government_reporting_options == []


def test_prevention_and_recovery_preserve_status_mode_and_priority():
    kinds = [route("FINANCIAL_SCAM_REPORTING", "SECONDARY"), route("FINANCIAL_PROVIDER")]
    for status, mode in [("RECOMMENDED", "PREVENTION"), ("URGENT", "RECOVERY")]:
        result = resolve_official_referral(referral(status, mode, kinds))
        assert (result.status, result.mode) == (status, mode)
        assert [item.route_type for item in result.routes] == [
            "FINANCIAL_PROVIDER",
            "FINANCIAL_SCAM_REPORTING",
        ]
        assert result.routes[0].action_type == "GUIDANCE_ONLY"
        assert result.routes[1].channel.id == "ojk-iasc"


def test_unknown_route_is_skipped_without_losing_valid_routes():
    result = resolve_official_referral(
        referral(routes=[route("FUTURE_ROUTE"), route("ACCOUNT_PROVIDER")])
    )
    assert [item.route_type for item in result.routes] == ["ACCOUNT_PROVIDER"]


def test_inactive_or_http_channel_falls_back_to_guidance():
    for url, active in [("http://iasc.ojk.go.id", True), ("https://iasc.ojk.go.id", False)]:
        channel = OfficialChannel(
            id="test",
            route_type="FINANCIAL_SCAM_REPORTING",
            organization_name="OJK",
            channel_name="IASC",
            description="",
            destination_url=url,
            is_active=active,
            verified_at=date.today(),
        )
        result = resolve_official_referral(
            referral(routes=[route("FINANCIAL_SCAM_REPORTING")]), (channel,)
        )
        assert result.routes[0].action_type == "GUIDANCE_ONLY"
        assert result.routes[0].channel is None


def test_missing_channel_keeps_safe_guidance():
    result = resolve_official_referral(
        referral(routes=[route("FINANCIAL_PROVIDER"), route("DEVICE_RECOVERY")]), ()
    )
    assert len(result.routes) == 2
    assert all(item.action_type == "GUIDANCE_ONLY" for item in result.routes)


def test_government_reporting_options_are_specific_and_separate_from_ai_routes():
    result = resolve_official_referral(referral(routes=[route("ACCOUNT_PROVIDER")]))
    assert [item.channel.id for item in result.government_reporting_options] == [
        "komdigi-aduan-nomor",
        "komdigi-aduan-konten",
    ]
    assert all(
        item.channel.destination_url.startswith("https://")
        for item in result.government_reporting_options
    )
    assert all(item.route_type != "FINANCIAL_SCAM_REPORTING" for item in result.routes)


def test_inactive_or_http_government_reporting_channel_is_hidden():
    channel = OfficialChannel(
        id="bad",
        route_type="SUSPICIOUS_CONTENT",
        organization_name="Komdigi",
        channel_name="Aduan Konten",
        description="",
        destination_url="http://aduankonten.id/",
        verified_at=date.today(),
    )
    result = resolve_official_referral(
        referral(routes=[route("ACCOUNT_PROVIDER")]), government_reporting_channels=(channel,)
    )
    assert result.government_reporting_options == []
