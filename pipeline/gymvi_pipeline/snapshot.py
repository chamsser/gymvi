from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from gymvi_pipeline import __version__
from gymvi_pipeline.spoinfo import (
    CATALOG_DATASET_ID,
    CATALOG_NAME,
    CATALOG_URL,
    PROVIDER_NAME,
    PROVIDER_SYSTEM_NAME,
    PROVIDER_SYSTEM_URL,
    SOURCE_LICENSE,
    SOURCE_MODIFIED_DATE,
    FacilityRequest,
    FetchResult,
    ParsedFacilityResponse,
)


SNAPSHOT_METADATA_SCHEMA = "gymvi://schemas/snapshot-metadata/1.0.0"
NORMALIZATION_SCHEMA_VERSION = "1.0.0"


@dataclass(frozen=True)
class SnapshotReceipt:
    snapshot_id: str
    snapshot_directory: Path
    response_path: Path
    metadata_path: Path
    response_sha256: str
    row_count: int


def write_facility_snapshot(
    *,
    output_root: Path,
    request: FacilityRequest,
    fetched: FetchResult,
    parsed: ParsedFacilityResponse,
    collected_at: datetime | None = None,
) -> SnapshotReceipt:
    timestamp = collected_at or datetime.now(timezone.utc)
    if timestamp.tzinfo is None:
        raise ValueError("collected_at은 timezone-aware 값이어야 합니다.")
    timestamp = timestamp.astimezone(timezone.utc)

    response_sha256 = hashlib.sha256(fetched.body).hexdigest()
    timestamp_id = timestamp.strftime("%Y%m%dT%H%M%S%fZ")
    snapshot_id = f"spoinfo-facilities-{timestamp_id}-{response_sha256[:12]}"
    snapshot_directory = (
        output_root.resolve() / "raw" / "facilities" / snapshot_id
    )
    snapshot_directory.mkdir(parents=True, exist_ok=False)

    response_path = snapshot_directory / "response.json"
    metadata_path = snapshot_directory / "metadata.json"
    with response_path.open("xb") as stream:
        stream.write(fetched.body)

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
            "acquisition_url": fetched.requested_url,
            "license": SOURCE_LICENSE,
            "source_modified_date": SOURCE_MODIFIED_DATE,
        },
        "request": {
            "method": "GET",
            "collected_at": _iso_z(timestamp),
            "transport": fetched.transport,
            "parameters": request.parameters(),
            "requested_pages": [request.page_number],
            "requested_rows": request.requested_rows,
            "api_key_used": False,
        },
        "response": {
            "http_status": fetched.status_code,
            "content_type": fetched.content_type,
            "byte_count": len(fetched.body),
            "sha256": response_sha256,
            "row_count": len(parsed.rows),
            "reported_total_count": parsed.total_count,
            "returned_pages": [parsed.page_number],
            "missing_pages": [],
            "source_result_code": parsed.result_code,
            "source_result_message": parsed.result_message,
        },
        "collector": {
            "name": "gymvi-pipeline",
            "version": __version__,
            "normalization_schema_version": NORMALIZATION_SCHEMA_VERSION,
        },
        "status": "SUCCESS",
        "limitations": [
            "공공데이터포털 정식 OpenAPI 서비스키를 사용하지 않은 제공 시스템 공개 검색 응답입니다.",
            "시설 응답만으로 현재 영업·프로그램 신청·안전 상태를 확정하지 않습니다.",
        ],
    }
    _write_json_exclusive(metadata_path, metadata)

    return SnapshotReceipt(
        snapshot_id=snapshot_id,
        snapshot_directory=snapshot_directory,
        response_path=response_path,
        metadata_path=metadata_path,
        response_sha256=response_sha256,
        row_count=len(parsed.rows),
    )


def load_verified_snapshot(snapshot_directory: Path) -> tuple[dict[str, Any], bytes]:
    metadata, payloads = load_verified_snapshot_payloads(snapshot_directory)
    if len(payloads) != 1:
        raise ValueError("여러 페이지 스냅샷은 payloads 로더를 사용해야 합니다.")
    return metadata, payloads[0]


def load_verified_snapshot_payloads(
    snapshot_directory: Path,
) -> tuple[dict[str, Any], tuple[bytes, ...]]:
    metadata_path = snapshot_directory / "metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    page_entries = metadata.get("response", {}).get("pages")
    if page_entries is None:
        response_path = snapshot_directory / "response.json"
        payload = response_path.read_bytes()
        actual_sha256 = hashlib.sha256(payload).hexdigest()
        expected_sha256 = metadata["response"]["sha256"]
        if actual_sha256 != expected_sha256:
            raise ValueError(
                "원본 응답 SHA-256이 메타데이터와 달라 정규화를 중단합니다."
            )
        return metadata, (payload,)

    if not isinstance(page_entries, list) or not page_entries:
        raise ValueError("전국 스냅샷 페이지 메타데이터가 비어 있습니다.")
    payloads: list[bytes] = []
    page_hashes: list[str] = []
    expected_page_numbers = list(range(1, len(page_entries) + 1))
    actual_page_numbers = [entry.get("page_number") for entry in page_entries]
    if actual_page_numbers != expected_page_numbers:
        raise ValueError("전국 스냅샷 페이지 순서가 연속적이지 않습니다.")
    for entry in page_entries:
        relative_path = Path(str(entry.get("path", "")))
        if relative_path.is_absolute() or ".." in relative_path.parts:
            raise ValueError("전국 스냅샷 페이지 경로가 안전하지 않습니다.")
        payload = (snapshot_directory / relative_path).read_bytes()
        actual_sha256 = hashlib.sha256(payload).hexdigest()
        expected_sha256 = str(entry.get("sha256", ""))
        if actual_sha256 != expected_sha256:
            raise ValueError(
                "전국 원본 페이지 SHA-256이 메타데이터와 다릅니다."
            )
        payloads.append(payload)
        page_hashes.append(actual_sha256)
    manifest_sha256 = hashlib.sha256(
        "\n".join(page_hashes).encode("ascii")
    ).hexdigest()
    if manifest_sha256 != metadata["response"]["sha256"]:
        raise ValueError("전국 원본 페이지 매니페스트 SHA-256이 다릅니다.")
    if metadata["response"].get("missing_pages") != []:
        raise ValueError("전국 스냅샷에 누락 페이지가 기록돼 있습니다.")
    return metadata, tuple(payloads)


def _write_json_exclusive(path: Path, value: Any) -> None:
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")


def _iso_z(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")
