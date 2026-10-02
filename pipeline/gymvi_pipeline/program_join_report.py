from __future__ import annotations

import json
from collections import Counter, defaultdict
from dataclasses import dataclass, fields
from datetime import date
from pathlib import Path
from typing import Any

from gymvi_pipeline.program_join_index import FacilityJoinIndex
from gymvi_pipeline.programs import (
    ProgramRecord,
    _facility_address,
    _facility_name,
    _facility_phone,
)
from gymvi_pipeline.spoinfo import SourceResponseError


@dataclass(frozen=True)
class ProgramJoinReportReceipt:
    report_path: Path
    joins_path: Path
    program_count: int
    exact_count: int
    current_exact_count: int


def analyze_program_joins(
    *,
    programs_file: Path,
    program_qa_file: Path,
    facilities_file: Path,
    facility_qa_file: Path,
    output_directory: Path,
    as_of_date: date,
) -> ProgramJoinReportReceipt:
    program_qa = _load_qa(program_qa_file, "program")
    facility_qa = _load_qa(facility_qa_file, "facility")
    facilities, facility_version = _load_facilities(facilities_file)
    _verify_qa(facility_qa, facility_version, len(facilities), "시설")
    index = FacilityJoinIndex(facilities)
    output = output_directory.resolve()
    output.mkdir(parents=True, exist_ok=False)
    joins_path = output / "joins.ndjson"
    report_path = output / "report.json"

    counts: Counter[str] = Counter()
    region_counts: dict[str, Counter[str]] = defaultdict(Counter)
    gangseo_traces: list[dict[str, Any]] = []
    exact_traces: list[dict[str, Any]] = []
    program_version: str | None = None

    with joins_path.open("x", encoding="utf-8", newline="\n") as stream:
        for _, row in _iter_ndjson(programs_file):
            version = _required_string(row, "dataset_version")
            if program_version is None:
                program_version = version
            elif program_version != version:
                raise SourceResponseError("프로그램 파일에 여러 데이터 버전이 섞여 있습니다.")
            program = _program_record(row)
            join = index.join(program)
            period = _period_state(program, as_of_date)
            region = _region(program.raw)
            facility = index.facility(join.facility_id) if join.facility_id else None
            trace = {
                "program_id": program.program_id,
                "program_source_row_number": _source_row_number(row, version),
                "facility_id": join.facility_id,
                "facility_source_record_id": (
                    facility.get("source_record_id") if facility else None
                ),
                "state": join.state,
                "reason_codes": join.reason_codes,
                "period_state": period,
                "region": region,
            }
            stream.write(json.dumps(trace, ensure_ascii=False, sort_keys=True) + "\n")
            counts[join.state] += 1
            counts[f"PERIOD_{period}"] += 1
            region_counts[region][join.state] += 1
            if join.state == "EXACT" and period == "CURRENT":
                counts["CURRENT_EXACT"] += 1
                region_counts[region]["CURRENT_EXACT"] += 1
            if join.state == "EXACT" and len(exact_traces) < 10:
                exact_traces.append(trace)
            if region == "서울특별시 강서구" and len(gangseo_traces) < 20:
                gangseo_traces.append(trace)

    if program_version is None:
        raise SourceResponseError("프로그램 정규화 파일에 행이 없습니다.")
    _verify_qa(
        program_qa,
        program_version,
        sum(counts[state] for state in ("EXACT", "REVIEWED", "CANDIDATE", "REJECTED")),
        "프로그램",
    )
    report = {
        "schema_version": "1.0.0",
        "status": "ANALYZED_NOT_PUBLISHED",
        "program_dataset_version": program_version,
        "facility_dataset_version": facility_version,
        "as_of_date": as_of_date.isoformat(),
        "counts": {
            "programs": sum(
                counts[state]
                for state in ("EXACT", "REVIEWED", "CANDIDATE", "REJECTED")
            ),
            "exact": counts["EXACT"],
            "reviewed": counts["REVIEWED"],
            "candidate": counts["CANDIDATE"],
            "rejected": counts["REJECTED"],
            "current_exact": counts["CURRENT_EXACT"],
            "period_current": counts["PERIOD_CURRENT"],
            "period_out_of_period": counts["PERIOD_OUT_OF_PERIOD"],
            "period_unknown": counts["PERIOD_UNKNOWN"],
        },
        "regions": {
            region: {
                "exact": state_counts["EXACT"],
                "candidate": state_counts["CANDIDATE"],
                "rejected": state_counts["REJECTED"],
                "current_exact": state_counts["CURRENT_EXACT"],
            }
            for region, state_counts in sorted(region_counts.items())
        },
        "regions_with_three_current_exact": sorted(
            region
            for region, state_counts in region_counts.items()
            if state_counts["CURRENT_EXACT"] >= 3
        ),
        "gangseo_traces": gangseo_traces,
        "exact_traces": exact_traces,
        "limitations": [
            "EXACT는 시설 결합만 뜻하며 현재 신청 가능이나 영업 중을 뜻하지 않습니다.",
            "CANDIDATE는 검토 전 게시할 수 없습니다.",
            "기간 내 프로그램이어도 정확한 시간과 남은 정원은 별도 근거 없이는 미상입니다.",
        ],
    }
    with report_path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write("\n")
    return ProgramJoinReportReceipt(
        report_path=report_path,
        joins_path=joins_path,
        program_count=report["counts"]["programs"],
        exact_count=counts["EXACT"],
        current_exact_count=counts["CURRENT_EXACT"],
    )


def _load_facilities(path: Path) -> tuple[list[dict[str, Any]], str]:
    facilities: list[dict[str, Any]] = []
    version: str | None = None
    for _, row in _iter_ndjson(path):
        row_version = _required_string(row, "dataset_version")
        if version is None:
            version = row_version
        elif version != row_version:
            raise SourceResponseError("시설 파일에 여러 데이터 버전이 섞여 있습니다.")
        facilities.append(
            {
                "facility_id": _required_string(row, "facility_id"),
                "name": _facility_name(row),
                "road_address": _facility_address(row),
                "phone": _facility_phone(row),
                "location": row.get("location"),
                "source_record_id": _required_string(row, "source_record_id"),
            }
        )
    if version is None:
        raise SourceResponseError("시설 정규화 파일에 행이 없습니다.")
    return facilities, version


def _load_qa(path: Path, kind: str) -> dict[str, Any]:
    source = path.resolve()
    if not source.is_file() or source.suffix.casefold() != ".json":
        raise ValueError(f"{kind} QA 보고서 JSON 파일을 찾을 수 없습니다.")
    try:
        document = json.loads(source.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SourceResponseError(f"{kind} QA 보고서 형식이 올바르지 않습니다.") from exc
    if not isinstance(document, dict) or document.get("status") != "PASS":
        raise SourceResponseError(f"{kind} QA가 PASS가 아닙니다.")
    checks = document.get("checks")
    if not isinstance(checks, dict) or checks.get("raw_hash_verified") is not True:
        raise SourceResponseError(f"{kind} QA에 원본 해시 검증 증거가 없습니다.")
    if kind == "program" and checks.get("license_evidence_verified") is not True:
        raise SourceResponseError("프로그램 QA에 이용 조건 검증 증거가 없습니다.")
    if kind == "facility":
        projection = document.get("projection")
        if not isinstance(projection, dict) or projection.get("name") != "LOCATED_FACILITIES":
            raise SourceResponseError("시설 QA는 LOCATED_FACILITIES 투영이어야 합니다.")
    return document


def _verify_qa(
    document: dict[str, Any], version: str, row_count: int, label: str
) -> None:
    counts = document.get("counts")
    if (
        document.get("snapshot_id") != version
        or not isinstance(counts, dict)
        or type(counts.get("normalized_rows")) is not int
        or counts["normalized_rows"] != row_count
    ):
        raise SourceResponseError(f"{label} QA의 버전·행 수가 정규화 파일과 다릅니다.")


def _iter_ndjson(path: Path):
    source = path.resolve()
    if not source.is_file() or source.suffix.casefold() != ".ndjson":
        raise ValueError("정규화 NDJSON 파일을 찾을 수 없습니다.")
    with source.open("r", encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise SourceResponseError(
                    f"정규화 NDJSON {line_number}행의 JSON 형식이 올바르지 않습니다."
                ) from exc
            if not isinstance(row, dict):
                raise SourceResponseError(
                    f"정규화 NDJSON {line_number}행은 객체여야 합니다."
                )
            yield line_number, row


def _program_record(row: dict[str, Any]) -> ProgramRecord:
    names = {field.name for field in fields(ProgramRecord)}
    try:
        values = {name: row[name] for name in names}
    except KeyError as exc:
        raise SourceResponseError("프로그램 정규화 행의 필수 필드가 없습니다.") from exc
    if not isinstance(values["raw"], dict) or not isinstance(values["weekdays"], list):
        raise SourceResponseError("프로그램 정규화 행의 필드 형식이 올바르지 않습니다.")
    values["weekdays"] = tuple(values["weekdays"])
    return ProgramRecord(**values)


def _required_string(row: dict[str, Any], name: str) -> str:
    value = row.get(name)
    if not isinstance(value, str) or not value:
        raise SourceResponseError(f"정규화 행에 {name} 값이 없습니다.")
    return value


def _source_row_number(row: dict[str, Any], version: str) -> int:
    source = row.get("source")
    if (
        not isinstance(source, dict)
        or source.get("source_snapshot_id") != version
        or type(source.get("source_row_number")) is not int
        or source["source_row_number"] < 2
    ):
        raise SourceResponseError("프로그램 원본 행 번호·스냅샷 출처가 없습니다.")
    return source["source_row_number"]


def _region(raw: dict[str, Any]) -> str:
    sido = str(raw.get("CTPRVN_NM") or "").strip()
    sigungu = str(raw.get("SIGNGU_NM") or "").strip()
    return " ".join(part for part in (sido, sigungu) if part) or "UNKNOWN"


def _period_state(program: ProgramRecord, as_of_date: date) -> str:
    if program.begin_date is None or program.end_date is None:
        return "UNKNOWN"
    if program.begin_date <= as_of_date.isoformat() <= program.end_date:
        return "CURRENT"
    return "OUT_OF_PERIOD"
