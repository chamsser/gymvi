from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from gymvi_pipeline.normalize import (
    NormalizationReceipt,
    normalize_facility_snapshot,
)
from gymvi_pipeline.snapshot import SnapshotReceipt, write_facility_snapshot
from gymvi_pipeline.nationwide import (
    NationwideSnapshotReceipt,
    acquire_nationwide_facility_snapshot,
)
from gymvi_pipeline.spoinfo import (
    FacilityRequest,
    FetchResult,
    Fetcher,
    fetch_facilities,
    parse_facility_response,
)


@dataclass(frozen=True)
class FacilitySpikeReceipt:
    snapshot: SnapshotReceipt
    normalization: NormalizationReceipt


@dataclass(frozen=True)
class NationwideFacilityReceipt:
    snapshot: NationwideSnapshotReceipt
    normalization: NormalizationReceipt


def acquire_facility_snapshot(
    *,
    output_root: Path,
    request: FacilityRequest,
    timeout_seconds: float = 30.0,
    fetcher: Fetcher = fetch_facilities,
    collected_at: datetime | None = None,
) -> SnapshotReceipt:
    fetched = fetcher(request, timeout_seconds)
    if fetched.status_code != 200:
        raise ValueError(f"시설 원본 HTTP 상태가 200이 아닙니다: {fetched.status_code}")
    parsed = parse_facility_response(fetched.body)
    return write_facility_snapshot(
        output_root=output_root,
        request=request,
        fetched=fetched,
        parsed=parsed,
        collected_at=collected_at,
    )


def run_facility_spike(
    *,
    output_root: Path,
    request: FacilityRequest,
    timeout_seconds: float = 30.0,
    fetcher: Fetcher = fetch_facilities,
    collected_at: datetime | None = None,
) -> FacilitySpikeReceipt:
    snapshot = acquire_facility_snapshot(
        output_root=output_root,
        request=request,
        timeout_seconds=timeout_seconds,
        fetcher=fetcher,
        collected_at=collected_at,
    )
    normalization = normalize_facility_snapshot(
        snapshot_directory=snapshot.snapshot_directory,
        output_root=output_root,
    )
    return FacilitySpikeReceipt(snapshot=snapshot, normalization=normalization)


def run_nationwide_facilities(
    *,
    output_root: Path,
    service_key: str,
    requested_rows: int = 1_000,
    timeout_seconds: float = 30.0,
    collected_at: datetime | None = None,
) -> NationwideFacilityReceipt:
    snapshot = acquire_nationwide_facility_snapshot(
        output_root=output_root,
        service_key=service_key,
        requested_rows=requested_rows,
        timeout_seconds=timeout_seconds,
        collected_at=collected_at,
    )
    normalization = normalize_facility_snapshot(
        snapshot_directory=snapshot.snapshot_directory,
        output_root=output_root,
    )
    return NationwideFacilityReceipt(snapshot, normalization)


def fixture_fetcher(path: Path) -> Fetcher:
    fixture_bytes = path.read_bytes()

    def fetch(request: FacilityRequest, timeout_seconds: float) -> FetchResult:
        del timeout_seconds
        return FetchResult(
            requested_url=request.url(),
            status_code=200,
            content_type="application/json; charset=UTF-8",
            body=fixture_bytes,
            transport="fixture",
        )

    return fetch
