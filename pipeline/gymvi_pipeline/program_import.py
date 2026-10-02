from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass
from datetime import date, datetime, timezone
from pathlib import Path
from typing import Any

from gymvi_pipeline import __version__
from gymvi_pipeline.programs import (
    CATALOG_DATASET_ID,
    CATALOG_NAME,
    CATALOG_URL,
    ProgramCsvStats,
    REQUIRED_COLUMNS,
    ProgramRecord,
    iter_program_csv_rows,
)
from gymvi_pipeline.snapshot import (
    NORMALIZATION_SCHEMA_VERSION,
    SNAPSHOT_METADATA_SCHEMA,
    _iso_z,
    _write_json_exclusive,
)


NORMALIZED_PROGRAM_SCHEMA = "gymvi://schemas/normalized-program/1.0.0"
COPY_CHUNK_SIZE = 1024 * 1024


@dataclass(frozen=True)
class ProgramImportReceipt:
    snapshot_id: str
    snapshot_directory: Path
    raw_path: Path
    metadata_path: Path
    normalized_path: Path
    qa_report_path: Path
    response_sha256: str
    row_count: int
    input_row_count: int
    duplicate_row_count: int


def import_program_csv(
    *,
    source_file: Path,
    license_evidence_file: Path,
    output_root: Path,
    source_modified_date: str,
    encoding: str = "utf-8-sig",
    collected_at: datetime | None = None,
) -> ProgramImportReceipt:
    source_path = source_file.resolve()
    if not source_path.is_file() or source_path.suffix.casefold() != ".csv":
        raise ValueError("프로그램 원본 CSV 파일을 찾을 수 없습니다.")
    if encoding not in {"utf-8-sig", "cp949"}:
        raise ValueError("프로그램 CSV 인코딩은 utf-8-sig 또는 cp949여야 합니다.")

    normalized_source_date = _source_date(source_modified_date)
    license_path = license_evidence_file.resolve()
    license_document, license_payload = _load_license_evidence(license_path)

    timestamp = collected_at or datetime.now(timezone.utc)
    if timestamp.tzinfo is None:
        raise ValueError("collected_at은 timezone-aware 값이어야 합니다.")
    timestamp = timestamp.astimezone(timezone.utc)

    source_sha256, source_byte_count = _hash_file(source_path)
    license_sha256 = hashlib.sha256(license_payload).hexdigest()
    timestamp_id = timestamp.strftime("%Y%m%dT%H%M%S%fZ")
    snapshot_id = f"culture-programs-{timestamp_id}-{source_sha256[:12]}"
    root = output_root.resolve()
    snapshot_directory = root / "raw" / "programs" / snapshot_id
    normalized_directory = root / "normalized" / "programs" / snapshot_id
    qa_directory = root / "qa" / "programs" / snapshot_id
    snapshot_directory.mkdir(parents=True, exist_ok=False)
    normalized_directory.mkdir(parents=True, exist_ok=False)
    qa_directory.mkdir(parents=True, exist_ok=False)

    raw_path = snapshot_directory / "response.csv"
    copied_sha256 = _copy_with_hash(source_path, raw_path)
    if copied_sha256 != source_sha256:
        raise ValueError("복사한 프로그램 원본의 SHA-256이 입력 파일과 다릅니다.")
    license_copy_path = snapshot_directory / "license-evidence.json"
    with license_copy_path.open("xb") as stream:
        stream.write(license_payload)

    normalized_path = normalized_directory / "programs.ndjson"
    stats = ProgramCsvStats()
    row_count = _normalize_program_csv(
        raw_path=raw_path,
        normalized_path=normalized_path,
        snapshot_id=snapshot_id,
        source_modified_date=normalized_source_date,
        encoding=encoding,
        stats=stats,
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
            "source_modified_date": normalized_source_date,
            "license": {
                "name": license_document["license_name"],
                "url": license_document["license_url"],
                "attribution": license_document["attribution"],
                "commercial_use_allowed": license_document[
                    "commercial_use_allowed"
                ],
                "modification_allowed": license_document[
                    "modification_allowed"
                ],
                "evidence_path": "license-evidence.json",
                "evidence_sha256": license_sha256,
                "checked_at": license_document["checked_at"],
            },
        },
        "request": {
            "method": "MANUAL_DOWNLOAD",
            "collected_at": _iso_z(timestamp),
            "transport": "manual-download",
            "source_file_name": source_path.name,
            "encoding": encoding,
            "credential_persisted": False,
        },
        "response": {
            "content_type": "text/csv",
            "byte_count": source_byte_count,
            "sha256": source_sha256,
            "row_count": stats.input_rows,
        },
        "normalization": {
            "row_count": row_count,
            "duplicate_offering_rows_collapsed": stats.duplicate_rows,
            "variant_duplicate_rows": stats.variant_duplicate_rows,
            "missing_program_type_rows": stats.missing_type_rows,
        },
        "collector": {
            "name": "gymvi-pipeline",
            "version": __version__,
            "normalization_schema_version": NORMALIZATION_SCHEMA_VERSION,
        },
        "status": "SUCCESS",
        "limitations": [
            "모집인원은 정원 정보이며 잔여석이나 신청 가능 상태를 뜻하지 않습니다.",
            "원본 시간값에서 확인되지 않은 정확한 수업시각을 추론하지 않습니다.",
        ],
    }
    _write_json_exclusive(metadata_path, metadata)

    qa_report_path = qa_directory / "report.json"
    qa_report = {
        "schema_version": "1.0.0",
        "snapshot_id": snapshot_id,
        "status": "PASS",
        "counts": {
            "input_rows": stats.input_rows,
            "normalized_rows": row_count,
            "rejected_rows": 0,
            "duplicate_ids": 0,
            "duplicate_offering_rows_collapsed": stats.duplicate_rows,
            "variant_duplicate_rows": stats.variant_duplicate_rows,
            "missing_program_type_rows": stats.missing_type_rows,
            "errors": 0,
        },
        "checks": {
            "raw_hash_verified": True,
            "required_columns_checked": sorted(REQUIRED_COLUMNS),
            "program_identifier_uniqueness_checked": True,
            "duplicate_offering_keys_collapsed": True,
            "license_evidence_verified": True,
            "unknown_application_state_preserved": True,
        },
        "errors": [],
    }
    _write_json_exclusive(qa_report_path, qa_report)

    return ProgramImportReceipt(
        snapshot_id=snapshot_id,
        snapshot_directory=snapshot_directory,
        raw_path=raw_path,
        metadata_path=metadata_path,
        normalized_path=normalized_path,
        qa_report_path=qa_report_path,
        response_sha256=source_sha256,
        row_count=row_count,
        input_row_count=stats.input_rows,
        duplicate_row_count=stats.duplicate_rows,
    )


def _normalize_program_csv(
    *,
    raw_path: Path,
    normalized_path: Path,
    snapshot_id: str,
    source_modified_date: str,
    encoding: str,
    stats: ProgramCsvStats,
) -> int:
    row_count = 0
    partial_path = normalized_path.with_suffix(normalized_path.suffix + ".partial")
    with raw_path.open("r", encoding=encoding, newline="") as source_stream:
        with partial_path.open("x", encoding="utf-8", newline="\n") as output_stream:
            for source_row_number, program in iter_program_csv_rows(
                source_stream, skip_duplicates=True, stats=stats
            ):
                normalized = _normalized_program(
                    program=program,
                    snapshot_id=snapshot_id,
                    source_row_number=source_row_number,
                    source_modified_date=source_modified_date,
                )
                output_stream.write(
                    json.dumps(normalized, ensure_ascii=False, sort_keys=True) + "\n"
                )
                row_count += 1
    if row_count == 0:
        partial_path.unlink()
        raise ValueError("프로그램 원본 CSV에 데이터 행이 없습니다.")
    partial_path.replace(normalized_path)
    return row_count


def _normalized_program(
    *,
    program: ProgramRecord,
    snapshot_id: str,
    source_row_number: int,
    source_modified_date: str,
) -> dict[str, Any]:
    record = asdict(program)
    return {
        "$schema": NORMALIZED_PROGRAM_SCHEMA,
        "schema_version": NORMALIZATION_SCHEMA_VERSION,
        "dataset_version": snapshot_id,
        **record,
        "source": {
            "source_dataset_id": CATALOG_DATASET_ID,
            "source_snapshot_id": snapshot_id,
            "source_row_number": source_row_number,
            "as_of": source_modified_date,
        },
        "states": {
            "program_application": {
                "value": "UNKNOWN",
                "reason": "NO_CURRENT_APPLICATION_EVIDENCE",
            }
        },
    }


def _load_license_evidence(path: Path) -> tuple[dict[str, Any], bytes]:
    if not path.is_file():
        raise ValueError("프로그램 이용 조건 증거 JSON 파일을 찾을 수 없습니다.")
    payload = path.read_bytes()
    try:
        document = json.loads(payload.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ValueError("프로그램 이용 조건 증거 JSON 형식이 올바르지 않습니다.") from exc
    if not isinstance(document, dict):
        raise ValueError("프로그램 이용 조건 증거는 JSON 객체여야 합니다.")
    for field_name in ("license_name", "license_url", "attribution", "checked_at"):
        value = document.get(field_name)
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"프로그램 이용 조건 증거의 {field_name} 값이 없습니다.")
    if not document["license_url"].casefold().startswith("https://"):
        raise ValueError("프로그램 이용 조건 URL은 HTTPS여야 합니다.")
    for field_name in ("commercial_use_allowed", "modification_allowed"):
        if not isinstance(document.get(field_name), bool):
            raise ValueError(f"프로그램 이용 조건 증거의 {field_name} 값은 boolean이어야 합니다.")
    _aware_timestamp(document["checked_at"])
    return document, payload


def _source_date(value: str) -> str:
    try:
        parsed = date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError("프로그램 원천 갱신일은 YYYY-MM-DD 형식이어야 합니다.") from exc
    if parsed.isoformat() != value:
        raise ValueError("프로그램 원천 갱신일은 YYYY-MM-DD 형식이어야 합니다.")
    return parsed.isoformat()


def _aware_timestamp(value: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("이용 조건 확인 시각은 시간대가 포함된 ISO 8601이어야 합니다.") from exc
    if parsed.tzinfo is None:
        raise ValueError("이용 조건 확인 시각은 시간대가 포함된 ISO 8601이어야 합니다.")
    return parsed


def _hash_file(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    byte_count = 0
    with path.open("rb") as stream:
        while chunk := stream.read(COPY_CHUNK_SIZE):
            digest.update(chunk)
            byte_count += len(chunk)
    return digest.hexdigest(), byte_count


def _copy_with_hash(source: Path, destination: Path) -> str:
    digest = hashlib.sha256()
    with source.open("rb") as source_stream:
        with destination.open("xb") as destination_stream:
            while chunk := source_stream.read(COPY_CHUNK_SIZE):
                destination_stream.write(chunk)
                digest.update(chunk)
    return digest.hexdigest()
