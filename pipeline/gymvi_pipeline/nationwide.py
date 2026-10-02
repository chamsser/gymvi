from __future__ import annotations

import hashlib
import json
import math
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from gymvi_pipeline import __version__
from gymvi_pipeline.data_go_facilities import (
    CATALOG_DATASET_ID,
    CATALOG_NAME,
    CATALOG_URL,
    FACILITY_OPENAPI_ENDPOINT,
    PROVIDER_NAME,
    PROVIDER_SYSTEM_NAME,
    PROVIDER_SYSTEM_URL,
    SOURCE_LICENSE,
    SOURCE_MODIFIED_DATE,
    DataGoFacilityRequest,
    DataGoFetcher,
    DataGoFetchResult,
    fetch_data_go_facilities,
    parse_data_go_facility_response,
)
from gymvi_pipeline.snapshot import (
    NORMALIZATION_SCHEMA_VERSION,
    SNAPSHOT_METADATA_SCHEMA,
    _iso_z,
    _write_json_exclusive,
)


@dataclass(frozen=True)
class NationwideSnapshotReceipt:
    snapshot_id: str
    snapshot_directory: Path
    response_paths: tuple[Path, ...]
    metadata_path: Path
    response_sha256: str
    row_count: int
    page_count: int


def acquire_nationwide_facility_snapshot(
    *,
    output_root: Path,
    service_key: str,
    requested_rows: int = 1_000,
    timeout_seconds: float = 30.0,
    fetcher: DataGoFetcher = fetch_data_go_facilities,
    collected_at: datetime | None = None,
) -> NationwideSnapshotReceipt:
    timestamp = collected_at or datetime.now(timezone.utc)
    if timestamp.tzinfo is None:
        raise ValueError("collected_at은 timezone-aware 값이어야 합니다.")
    timestamp = timestamp.astimezone(timezone.utc)

    first_request = DataGoFacilityRequest(service_key, 1, requested_rows)
    first_fetch = _fetch_checked(first_request, timeout_seconds, fetcher)
    first_page = parse_data_go_facility_response(first_fetch.body)
    if first_page.page_number != 1:
        raise ValueError("전국 시설 첫 응답의 pageNo가 1이 아닙니다.")
    if first_page.rows_per_page != requested_rows:
        raise ValueError("전국 시설 응답 numOfRows가 요청값과 다릅니다.")
    if first_page.total_count <= 0:
        raise ValueError("전국 시설 OpenAPI가 0건을 보고했습니다.")

    page_count = math.ceil(first_page.total_count / requested_rows)
    fetched_pages: list[tuple[DataGoFetchResult, Any]] = [
        (first_fetch, first_page)
    ]
    seen_ids: set[str] = set()
    _register_page_ids(first_page.rows, seen_ids)
    for page_number in range(2, page_count + 1):
        request = DataGoFacilityRequest(service_key, page_number, requested_rows)
        fetched = _fetch_checked(request, timeout_seconds, fetcher)
        parsed = parse_data_go_facility_response(fetched.body)
        if parsed.page_number != page_number:
            raise ValueError(
                f"전국 시설 응답 pageNo 불일치: expected={page_number}"
            )
        if parsed.total_count != first_page.total_count:
            raise ValueError("수집 도중 전국 시설 totalCount가 바뀌었습니다.")
        if parsed.rows_per_page != requested_rows:
            raise ValueError("전국 시설 응답 numOfRows가 요청값과 다릅니다.")
        _register_page_ids(parsed.rows, seen_ids)
        fetched_pages.append((fetched, parsed))

    actual_row_count = sum(len(parsed.rows) for _, parsed in fetched_pages)
    if actual_row_count != first_page.total_count:
        raise ValueError(
            "전국 시설 전체 페이지 행 수가 totalCount와 일치하지 않습니다."
        )

    page_hashes = [hashlib.sha256(fetched.body).hexdigest() for fetched, _ in fetched_pages]
    manifest_sha256 = hashlib.sha256("\n".join(page_hashes).encode("ascii")).hexdigest()
    timestamp_id = timestamp.strftime("%Y%m%dT%H%M%S%fZ")
    snapshot_id = f"kspo-facilities-{timestamp_id}-{manifest_sha256[:12]}"
    snapshot_directory = output_root.resolve() / "raw" / "facilities" / snapshot_id
    pages_directory = snapshot_directory / "pages"
    pages_directory.mkdir(parents=True, exist_ok=False)

    page_metadata: list[dict[str, Any]] = []
    response_paths: list[Path] = []
    for index, ((fetched, parsed), page_hash) in enumerate(
        zip(fetched_pages, page_hashes, strict=True),
        start=1,
    ):
        relative_path = f"pages/page-{index:06d}.json"
        response_path = snapshot_directory / relative_path
        with response_path.open("xb") as stream:
            stream.write(fetched.body)
        response_paths.append(response_path)
        page_metadata.append(
            {
                "page_number": index,
                "path": relative_path,
                "byte_count": len(fetched.body),
                "sha256": page_hash,
                "row_count": len(parsed.rows),
            }
        )

    metadata_path = snapshot_directory / "metadata.json"
    metadata: dict[str, Any] = {
        "$schema": SNAPSHOT_METADATA_SCHEMA,
        "schema_version": "1.0.0",
        "snapshot_id": snapshot_id,
        "source": {
            "source_dataset_id": CATALOG_DATASET_ID,
            "official_name": CATALOG_NAME,
            "catalog_url": CATALOG_URL,
            "provider": PROVIDER_NAME,
            "provider_system": PROVIDER_SYSTEM_NAME,
            "provider_system_url": PROVIDER_SYSTEM_URL,
            "acquisition_url": FACILITY_OPENAPI_ENDPOINT,
            "acquisition_format": "data-go-openapi-v1",
            "license": SOURCE_LICENSE,
            "source_modified_date": SOURCE_MODIFIED_DATE,
        },
        "request": {
            "method": "GET",
            "scope": "nationwide",
            "collected_at": _iso_z(timestamp),
            "transport": first_fetch.transport,
            "parameters": {
                "pageNo": "1..all",
                "numOfRows": str(requested_rows),
                "resultType": "json",
            },
            "requested_pages": list(range(1, page_count + 1)),
            "requested_rows": requested_rows,
            "api_key_used": True,
            "credential_persisted": False,
        },
        "response": {
            "http_status": 200,
            "content_type": first_fetch.content_type,
            "byte_count": sum(len(fetched.body) for fetched, _ in fetched_pages),
            "sha256": manifest_sha256,
            "row_count": actual_row_count,
            "reported_total_count": first_page.total_count,
            "returned_pages": list(range(1, page_count + 1)),
            "missing_pages": [],
            "pages": page_metadata,
            "source_result_code": first_page.result_code,
            "source_result_message": first_page.result_message,
        },
        "collector": {
            "name": "gymvi-pipeline",
            "version": __version__,
            "normalization_schema_version": NORMALIZATION_SCHEMA_VERSION,
        },
        "status": "SUCCESS",
        "limitations": [
            "시설 응답만으로 현재 영업·프로그램 신청·안전 상태를 확정하지 않습니다."
        ],
    }
    _write_json_exclusive(metadata_path, metadata)
    return NationwideSnapshotReceipt(
        snapshot_id=snapshot_id,
        snapshot_directory=snapshot_directory,
        response_paths=tuple(response_paths),
        metadata_path=metadata_path,
        response_sha256=manifest_sha256,
        row_count=actual_row_count,
        page_count=page_count,
    )


def _fetch_checked(
    request: DataGoFacilityRequest,
    timeout_seconds: float,
    fetcher: DataGoFetcher,
) -> DataGoFetchResult:
    fetched = fetcher(request, timeout_seconds)
    if fetched.status_code != 200:
        # 서비스키가 든 실제 URL이나 응답 본문은 오류에 포함하지 않는다.
        raise ValueError(
            f"전국 시설 OpenAPI HTTP 상태가 200이 아닙니다: {fetched.status_code}"
        )
    if fetched.page_number != request.page_number:
        raise ValueError("전국 시설 수집기 페이지 번호가 요청과 다릅니다.")
    return fetched


def _register_page_ids(rows: tuple[dict[str, Any], ...], seen: set[str]) -> None:
    for row in rows:
        facility_id = str(row.get("faci_cd", "")).strip()
        if not facility_id:
            raise ValueError("전국 시설 응답에 faci_cd가 없는 행이 있습니다.")
        if facility_id in seen:
            raise ValueError("전국 시설 전체 페이지에 중복 faci_cd가 있습니다.")
        seen.add(facility_id)
