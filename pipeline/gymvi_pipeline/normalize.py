from __future__ import annotations

import json
import math
import re
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from gymvi_pipeline.snapshot import (
    NORMALIZATION_SCHEMA_VERSION,
    load_verified_snapshot_payloads,
)
from gymvi_pipeline.data_go_facilities import parse_data_go_facility_response
from gymvi_pipeline.spoinfo import parse_facility_response


NORMALIZED_FACILITY_SCHEMA = "gymvi://schemas/normalized-facility/1.0.0"
KOREA_LONGITUDE_RANGE = (124.0, 132.0)
KOREA_LATITUDE_RANGE = (33.0, 39.5)
SOURCE_FIELD_ALIASES: dict[str, tuple[str, ...]] = {
    "faciCd": ("faciCd", "faci_cd"),
    "faciNm": ("faciNm", "faci_nm"),
    "faciGbCd": ("faciGbCd",),
    "faciGbNm": ("faciGbNm", "faci_gb_nm"),
    "faciGbDetailNm": ("faciGbDetailNm",),
    "fcobCd": ("fcobCd",),
    "fcobNm": ("fcobNm", "fcob_nm"),
    "ftypeCd": ("ftypeCd",),
    "ftypeNm": ("ftypeNm", "ftype_nm"),
    "faciRoadAddr1": ("faciRoadAddr1", "faci_road_addr"),
    "faciRoadAddrDetail": ("faci_road_daddr",),
    "faciAddr1": ("faciAddr1", "faci_addr"),
    "faciAddrDetail": ("faci_daddr",),
    "faciPost": ("faciPost", "faci_road_zip", "faci_zip"),
    "faciTel": ("faciTel", "faci_tel_no"),
    "faciGfa": ("faciGfa", "faci_gfa"),
    "faciPointX": ("faciPointX", "faci_lot"),
    "faciPointY": ("faciPointY", "faci_lat"),
    "cpCd": ("cpCd",),
    "cpbCd": ("cpbCd",),
    "cpNm": ("cpNm", "cp_nm"),
    "cpbNm": ("cpbNm", "cpb_nm"),
}


@dataclass(frozen=True)
class NormalizationReceipt:
    snapshot_id: str
    normalized_path: Path
    qa_report_path: Path
    input_row_count: int
    normalized_row_count: int
    status: str
    error_count: int
    rejected_row_count: int


def normalize_facility_snapshot(
    *, snapshot_directory: Path, output_root: Path
) -> NormalizationReceipt:
    metadata, payloads = load_verified_snapshot_payloads(snapshot_directory)
    acquisition_format = str(metadata.get("source", {}).get("acquisition_format", ""))
    is_nationwide = acquisition_format == "data-go-openapi-v1"
    if is_nationwide:
        parsed_pages = [parse_data_go_facility_response(payload) for payload in payloads]
        reported_total_count = parsed_pages[0].total_count
        rows = tuple(row for page in parsed_pages for row in page.rows)
        if any(page.total_count != reported_total_count for page in parsed_pages):
            raise ValueError("전국 스냅샷 페이지별 totalCount가 서로 다릅니다.")
    else:
        if len(payloads) != 1:
            raise ValueError("기존 공개 검색 스냅샷은 한 페이지여야 합니다.")
        parsed = parse_facility_response(payloads[0])
        reported_total_count = parsed.total_count
        rows = parsed.rows
    snapshot_id = str(metadata["snapshot_id"])
    collected_at = str(metadata["request"]["collected_at"])
    requested_sigungu_code = None if is_nationwide else _requested_sigungu_code(metadata)
    source_dataset_id = str(metadata["source"]["source_dataset_id"])
    provider_namespace = "kspo-sfms-openapi" if is_nationwide else "spoinfo"

    errors: list[dict[str, Any]] = []
    rejections: list[dict[str, Any]] = []
    warnings: list[dict[str, Any]] = []
    normalized: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    requested_sigungu_count = 0

    for index, raw_row in enumerate(rows, start=1):
        raw_id = _required_text(raw_row, "faciCd")
        if raw_id is None:
            errors.append(_issue(index, None, "MISSING_FACILITY_ID"))
            continue
        if raw_id in seen_ids:
            errors.append(_issue(index, raw_id, "DUPLICATE_FACILITY_ID"))
            continue
        seen_ids.add(raw_id)

        row_errors = _validate_row(index, raw_id, raw_row)
        if is_nationwide and row_errors:
            if any(_blocks_located_projection(issue) for issue in row_errors):
                rejections.extend(
                    {
                        **issue,
                        "disposition": "REJECTED_FROM_LOCATED_PROJECTION",
                    }
                    for issue in row_errors
                )
                continue
            warnings.extend(
                {
                    **issue,
                    "disposition": "NORMALIZED_WITH_UNKNOWN",
                }
                for issue in row_errors
            )
        elif row_errors:
            errors.extend(row_errors)
            continue

        facility = _normalize_row(
            raw_row=raw_row,
            raw_id=raw_id,
            snapshot_id=snapshot_id,
            collected_at=collected_at,
            source_dataset_id=source_dataset_id,
            provider_namespace=provider_namespace,
        )
        if requested_sigungu_code is None:
            requested_sigungu_count += 1
        elif (
            facility["administrative_area"]["sigungu_code"]
            == requested_sigungu_code
        ):
            requested_sigungu_count += 1
        else:
            errors.append(
                _issue(index, raw_id, "SIGUNGU_OUTSIDE_REQUEST", "cpbCd")
            )
        normalized.append(facility)

    if reported_total_count != len(rows):
        issue = {
            "code": "REPORTED_TOTAL_DIFFERS_FROM_RETURNED_ROWS",
            "reported_total_count": reported_total_count,
            "returned_row_count": len(rows),
        }
        if is_nationwide:
            errors.append(issue)
        else:
            warnings.append(issue)

    if not normalized:
        errors.append(
            {
                "code": "NO_VALID_FACILITY",
                "message": "정규화에 성공한 시설이 없습니다.",
            }
        )
    if requested_sigungu_count == 0:
        errors.append(
            {
                "code": "NO_REQUESTED_SIGUNGU_FACILITY",
                "message": (
                    "요청한 시군구 코드의 시설이 한 건도 "
                    "정규화되지 않았습니다."
                ),
            }
        )

    status = "PASS" if not errors else "FAIL"
    normalized_directory = (
        output_root.resolve() / "normalized" / "facilities" / snapshot_id
    )
    qa_directory = output_root.resolve() / "qa" / "facilities" / snapshot_id
    normalized_directory.mkdir(parents=True, exist_ok=False)
    qa_directory.mkdir(parents=True, exist_ok=False)
    normalized_path = normalized_directory / "facilities.ndjson"
    qa_report_path = qa_directory / "report.json"

    with normalized_path.open("x", encoding="utf-8", newline="\n") as stream:
        for facility in normalized:
            stream.write(
                json.dumps(facility, ensure_ascii=False, sort_keys=True) + "\n"
            )

    report = {
        "schema_version": "1.1.0",
        "snapshot_id": snapshot_id,
        "status": status,
        "projection": {
            "name": "LOCATED_FACILITIES",
            "source_rows_retained_in_raw_snapshot": True,
        },
        "counts": {
            "input_rows": len(rows),
            "reported_total_rows": reported_total_count,
            "normalized_rows": len(normalized),
            "rejected_rows": len(rows) - len(normalized),
            "requested_sigungu_rows": requested_sigungu_count,
            "duplicate_ids": sum(
                issue["code"] == "DUPLICATE_FACILITY_ID" for issue in errors
            ),
            "errors": len(errors),
            "rejections": len(rejections),
            "warnings": len(warnings),
        },
        "checks": {
            "raw_hash_verified": True,
            "required_fields_checked": True,
            "identifier_uniqueness_checked": True,
            "coordinate_range_checked": True,
            "requested_sigungu_trace_required": not is_nationwide,
            "complete_pagination_required": is_nationwide,
            "unknown_states_preserved": True,
        },
        "errors": errors,
        "rejections": rejections,
        "warnings": warnings,
    }
    with qa_report_path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")

    return NormalizationReceipt(
        snapshot_id=snapshot_id,
        normalized_path=normalized_path,
        qa_report_path=qa_report_path,
        input_row_count=len(rows),
        normalized_row_count=len(normalized),
        status=status,
        error_count=len(errors),
        rejected_row_count=len(rows) - len(normalized),
    )


def _blocks_located_projection(issue: dict[str, Any]) -> bool:
    if issue["code"] in {"INVALID_COORDINATE", "COORDINATE_OUTSIDE_KOREA"}:
        return True
    return (
        issue["code"] == "MISSING_REQUIRED_FIELD"
        and issue.get("field") in SOURCE_FIELD_ALIASES["faciNm"]
    )


def _validate_row(
    index: int, raw_id: str, raw_row: dict[str, Any]
) -> list[dict[str, Any]]:
    issues: list[dict[str, Any]] = []
    for field in ("faciNm", "fcobNm"):
        if _required_text(raw_row, field) is None:
            issues.append(
                _issue(
                    index,
                    raw_id,
                    "MISSING_REQUIRED_FIELD",
                    _source_field(raw_row, field),
                )
            )

    if not (_required_text(raw_row, "cpCd") or _required_text(raw_row, "cpNm")):
        issues.append(_issue(index, raw_id, "MISSING_REQUIRED_FIELD", "cp"))
    if not (_required_text(raw_row, "cpbCd") or _required_text(raw_row, "cpbNm")):
        issues.append(_issue(index, raw_id, "MISSING_REQUIRED_FIELD", "cpb"))

    if not (
        _required_text(raw_row, "faciRoadAddr1")
        or _required_text(raw_row, "faciAddr1")
    ):
        issues.append(_issue(index, raw_id, "MISSING_ADDRESS"))

    longitude = _coordinate(_source_value(raw_row, "faciPointX"))
    latitude = _coordinate(_source_value(raw_row, "faciPointY"))
    if longitude is None or latitude is None:
        issues.append(_issue(index, raw_id, "INVALID_COORDINATE"))
    elif not (
        KOREA_LONGITUDE_RANGE[0] <= longitude <= KOREA_LONGITUDE_RANGE[1]
        and KOREA_LATITUDE_RANGE[0] <= latitude <= KOREA_LATITUDE_RANGE[1]
    ):
        issues.append(_issue(index, raw_id, "COORDINATE_OUTSIDE_KOREA"))
    return issues


def _normalize_row(
    *,
    raw_row: dict[str, Any],
    raw_id: str,
    snapshot_id: str,
    collected_at: str,
    source_dataset_id: str,
    provider_namespace: str,
) -> dict[str, Any]:
    source_record_id = f"{snapshot_id}:{raw_id}"
    name = _required_text(raw_row, "faciNm")
    road_address = _joined_source_text(raw_row, "faciRoadAddr1", "faciRoadAddrDetail")
    lot_address = _joined_source_text(raw_row, "faciAddr1", "faciAddrDetail")
    phone = _optional_phone(_source_value(raw_row, "faciTel"))
    gross_floor_area = _optional_gross_floor_area(
        _source_value(raw_row, "faciGfa")
    )
    longitude = _coordinate(_source_value(raw_row, "faciPointX"))
    latitude = _coordinate(_source_value(raw_row, "faciPointY"))
    assert name is not None and longitude is not None and latitude is not None

    name_field = _source_field(raw_row, "faciNm")
    road_field = _source_field(raw_row, "faciRoadAddr1")
    lot_field = _source_field(raw_row, "faciAddr1")
    phone_field = _source_field(raw_row, "faciTel")
    gross_floor_area_field = _source_field(raw_row, "faciGfa")
    longitude_field = _source_field(raw_row, "faciPointX")
    latitude_field = _source_field(raw_row, "faciPointY")

    return {
        "$schema": NORMALIZED_FACILITY_SCHEMA,
        "schema_version": NORMALIZATION_SCHEMA_VERSION,
        "dataset_version": snapshot_id,
        "facility_id": f"spoinfo:{raw_id}",
        "source_record_id": source_record_id,
        "source": {
            "source_dataset_id": source_dataset_id,
            "source_snapshot_id": snapshot_id,
            "provider_namespace": provider_namespace,
            "provider_record_id": raw_id,
            "as_of": collected_at,
        },
        "name": _evidence(name, source_record_id, name_field, collected_at),
        "search_name": _search_text(name),
        "facility_class": {
            "code": _optional_text(_source_value(raw_row, "faciGbCd")),
            "name": _optional_text(_source_value(raw_row, "faciGbNm")),
            "detail_name": _optional_text(_source_value(raw_row, "faciGbDetailNm")),
        },
        "facility_type": {
            "industry_code": _optional_text(_source_value(raw_row, "fcobCd")),
            "industry_name": _optional_text(_source_value(raw_row, "fcobNm")),
            "type_code": _optional_text(_source_value(raw_row, "ftypeCd")),
            "type_name": _optional_text(_source_value(raw_row, "ftypeNm")),
        },
        "administrative_area": {
            "sido_code": _required_text(raw_row, "cpCd"),
            "sigungu_code": _required_text(raw_row, "cpbCd"),
            "sido_name": _required_text(raw_row, "cpNm"),
            "sigungu_name": _required_text(raw_row, "cpbNm"),
        },
        "address": {
            "road": _evidence(
                road_address, source_record_id, road_field, collected_at
            ),
            "lot": _evidence(
                lot_address, source_record_id, lot_field, collected_at
            ),
            "normalized": _normalize_address(road_address or lot_address or ""),
            "postal_code": _optional_text(_source_value(raw_row, "faciPost")),
        },
        "contact": {
            "phone": _evidence(
                phone, source_record_id, phone_field, collected_at
            ),
        },
        "gross_floor_area_square_meters": _evidence(
            gross_floor_area,
            source_record_id,
            gross_floor_area_field,
            collected_at,
        ),
        "location": {
            "crs": "WGS84",
            "longitude": longitude,
            "latitude": latitude,
            "source_fields": [longitude_field, latitude_field],
            "source_record_id": source_record_id,
            "as_of": collected_at,
        },
        "states": {
            "facility_operation": {
                "value": "UNKNOWN",
                "reason": "NO_CURRENT_OPERATION_EVIDENCE",
            },
            "program_application": {
                "value": "UNKNOWN",
                "reason": "NO_CURRENT_APPLICATION_EVIDENCE",
            },
            "safety": {
                "value": "UNKNOWN",
                "reason": "SAFETY_DATASET_NOT_JOINED",
            },
        },
        "field_provenance": {
            "name": name_field,
            "road_address": road_field,
            "lot_address": lot_field,
            "longitude": longitude_field,
            "latitude": latitude_field,
            "facility_class": [
                _source_field(raw_row, "faciGbCd"),
                _source_field(raw_row, "faciGbNm"),
                _source_field(raw_row, "faciGbDetailNm"),
            ],
            "facility_type": [
                _source_field(raw_row, "fcobCd"),
                _source_field(raw_row, "fcobNm"),
                _source_field(raw_row, "ftypeCd"),
                _source_field(raw_row, "ftypeNm"),
            ],
            "phone": phone_field,
            "gross_floor_area_square_meters": gross_floor_area_field,
        },
        "raw": raw_row,
    }


def _evidence(
    value: Any, source_record_id: str, source_field: str, as_of: str
) -> dict[str, Any]:
    return {
        "value": value,
        "source_record_id": source_record_id,
        "source_field": source_field,
        "as_of": as_of,
        "confidence": "SOURCE",
    }


def _required_text(row: dict[str, Any], field: str) -> str | None:
    return _optional_text(_source_value(row, field))


def _source_value(row: dict[str, Any], canonical_field: str) -> Any:
    for field_name in SOURCE_FIELD_ALIASES.get(canonical_field, (canonical_field,)):
        if field_name in row and row[field_name] is not None:
            return row[field_name]
    return None


def _source_field(row: dict[str, Any], canonical_field: str) -> str:
    aliases = SOURCE_FIELD_ALIASES.get(canonical_field, (canonical_field,))
    return next((field_name for field_name in aliases if field_name in row), aliases[0])


def _joined_source_text(
    row: dict[str, Any],
    base_field: str,
    detail_field: str,
) -> str | None:
    values = [
        _optional_text(_source_value(row, base_field)),
        _optional_text(_source_value(row, detail_field)),
    ]
    joined = " ".join(value for value in values if value)
    return joined or None


def _optional_phone(value: Any) -> str | None:
    phone = _optional_text(value)
    if phone is None:
        return None
    if len(phone) < 3 or len(phone) > 40 or re.fullmatch(r"[0-9+()\-\s]+", phone) is None:
        return None
    return phone


def _optional_gross_floor_area(value: Any) -> float | None:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    if not math.isfinite(result) or not 0 < result <= 10_000_000:
        return None
    return result


def _requested_sigungu_code(metadata: dict[str, Any]) -> str:
    try:
        value = metadata["request"]["parameters"]["cpbCd"]
    except (KeyError, TypeError) as exc:
        raise ValueError(
            "스냅샷 메타데이터에 요청 시군구 코드가 없습니다."
        ) from exc
    code = str(value).strip()
    if not code:
        raise ValueError("스냅샷 요청 시군구 코드가 비어 있습니다.")
    return code


def _optional_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _coordinate(value: Any) -> float | None:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def _search_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    return re.sub(r"\s+", " ", normalized).strip()


def _normalize_address(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value)
    return re.sub(r"\s+", " ", normalized).strip()


def _issue(
    row_number: int,
    provider_record_id: str | None,
    code: str,
    field: str | None = None,
) -> dict[str, Any]:
    issue: dict[str, Any] = {
        "row_number": row_number,
        "provider_record_id": provider_record_id,
        "code": code,
    }
    if field is not None:
        issue["field"] = field
    return issue
