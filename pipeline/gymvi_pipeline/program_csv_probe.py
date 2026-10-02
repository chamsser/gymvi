from __future__ import annotations

import hashlib
import json
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Any

from gymvi_pipeline.program_join_index import FacilityJoinIndex
from gymvi_pipeline.program_join_report import (
    _load_facilities,
    _load_qa,
    _period_state,
    _region,
    _verify_qa,
)
from gymvi_pipeline.programs import (
    CATALOG_DATASET_ID,
    CATALOG_URL,
    ProgramCsvStats,
    iter_program_csv_rows,
)
from gymvi_pipeline.spoinfo import SourceResponseError


@dataclass(frozen=True)
class ProgramCsvProbeReceipt:
    report_path: Path
    input_rows: int
    unique_rows: int
    exact_count: int
    current_exact_count: int


def probe_program_csv_joins(
    *,
    source_file: Path,
    source_modified_date: date,
    facilities_file: Path,
    facility_qa_file: Path,
    output_directory: Path,
    as_of_date: date,
    focus_region: str | None = None,
    encoding: str = "utf-8-sig",
) -> ProgramCsvProbeReceipt:
    """Measure private joinability without claiming a reusable publication license."""
    source = source_file.resolve()
    if not source.is_file() or source.suffix.casefold() != ".csv":
        raise ValueError("프로그램 원본 CSV 파일을 찾을 수 없습니다.")
    if encoding not in {"utf-8-sig", "cp949"}:
        raise ValueError("프로그램 CSV 인코딩은 utf-8-sig 또는 cp949여야 합니다.")

    facility_qa = _load_qa(facility_qa_file, "facility")
    facilities, facility_version = _load_facilities(facilities_file)
    _verify_qa(facility_qa, facility_version, len(facilities), "시설")
    index = FacilityJoinIndex(facilities)

    source_stat = source.stat()
    source_sha256 = _hash_file(source)
    counts: Counter[str] = Counter()
    region_counts: dict[str, Counter[str]] = defaultdict(Counter)
    region_current_exact_facilities: dict[str, set[str]] = defaultdict(set)
    gangseo_traces: list[dict[str, Any]] = []
    exact_traces: list[dict[str, Any]] = []
    focus_traces: list[dict[str, Any]] = []
    focus_seen_facilities: set[str] = set()
    stats = ProgramCsvStats()

    with source.open("r", encoding=encoding, newline="") as stream:
        for source_row_number, program in iter_program_csv_rows(
            stream, skip_duplicates=True, stats=stats
        ):
            join = index.join(program)
            period = _period_state(program, as_of_date)
            region = _region(program.raw)
            counts[join.state] += 1
            counts[f"PERIOD_{period}"] += 1
            region_counts[region][join.state] += 1
            if join.state == "EXACT" and period == "CURRENT":
                counts["CURRENT_EXACT"] += 1
                region_counts[region]["CURRENT_EXACT"] += 1
                if join.facility_id is not None:
                    region_current_exact_facilities[region].add(join.facility_id)
            facility = index.facility(join.facility_id) if join.facility_id else None
            trace = {
                "program_id": program.program_id,
                "program_source_row_number": source_row_number,
                "facility_id": join.facility_id,
                "facility_source_record_id": (
                    facility.get("source_record_id") if facility else None
                ),
                "state": join.state,
                "reason_codes": join.reason_codes,
                "period_state": period,
                "region": region,
            }
            if join.state == "EXACT" and len(exact_traces) < 10:
                exact_traces.append(trace)
            if region == "서울특별시 강서구" and len(gangseo_traces) < 20:
                gangseo_traces.append(trace)
            if (
                region == focus_region
                and join.state == "EXACT"
                and period == "CURRENT"
                and join.facility_id is not None
                and join.facility_id not in focus_seen_facilities
                and len(focus_traces) < 10
            ):
                focus_seen_facilities.add(join.facility_id)
                focus_traces.append(trace)

    if stats.normalized_rows == 0:
        raise SourceResponseError("프로그램 원본 CSV에 데이터 행이 없습니다.")
    if source.stat() != source_stat or _hash_file(source) != source_sha256:
        raise SourceResponseError("분석 중 프로그램 원본 CSV가 변경됐습니다.")

    report = {
        "schema_version": "1.0.0",
        "status": "PRIVATE_JOINABILITY_ONLY",
        "publication_allowed": False,
        "license_evidence": "UNVERIFIED",
        "source_dataset_id": CATALOG_DATASET_ID,
        "source_catalog_url": CATALOG_URL,
        "source_modified_date": source_modified_date.isoformat(),
        "source_sha256": source_sha256,
        "source_byte_count": source_stat.st_size,
        "facility_dataset_version": facility_version,
        "as_of_date": as_of_date.isoformat(),
        "counts": {
            "input_rows": stats.input_rows,
            "unique_offerings": stats.normalized_rows,
            "duplicate_offering_rows_collapsed": stats.duplicate_rows,
            "variant_duplicate_rows": stats.variant_duplicate_rows,
            "missing_program_type_rows": stats.missing_type_rows,
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
                "exact": states["EXACT"],
                "candidate": states["CANDIDATE"],
                "rejected": states["REJECTED"],
                "current_exact": states["CURRENT_EXACT"],
                "current_exact_facilities": len(region_current_exact_facilities[region]),
            }
            for region, states in sorted(region_counts.items())
        },
        "regions_with_three_current_exact": sorted(
            region
            for region, states in region_counts.items()
            if states["CURRENT_EXACT"] >= 3
        ),
        "regions_with_three_current_exact_facilities": sorted(
            region
            for region, facility_ids in region_current_exact_facilities.items()
            if len(facility_ids) >= 3
        ),
        "gangseo_traces": gangseo_traces,
        "exact_traces": exact_traces,
        "focus_region": focus_region,
        "focus_current_exact_facility_traces": focus_traces,
        "limitations": [
            "이 보고서는 비공개 원본의 결합 가능성만 측정하며 프로그램 게시를 허가하지 않습니다.",
            "EXACT는 시설 결합만 뜻하며 현재 신청 가능이나 영업 중을 뜻하지 않습니다.",
            "기간 내 프로그램이어도 정확한 시간과 남은 정원은 별도 근거 없이는 미상입니다.",
        ],
    }
    joined_count = sum(
        counts[state] for state in ("EXACT", "REVIEWED", "CANDIDATE", "REJECTED")
    )
    if joined_count != stats.normalized_rows:
        raise SourceResponseError("프로그램 결합 집계 건수가 고유 프로그램 건수와 다릅니다.")
    output = output_directory.resolve()
    output.mkdir(parents=True, exist_ok=False)
    report_path = output / "report.json"
    with report_path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(report, stream, ensure_ascii=False, sort_keys=True, indent=2)
        stream.write("\n")
    return ProgramCsvProbeReceipt(
        report_path=report_path,
        input_rows=stats.input_rows,
        unique_rows=stats.normalized_rows,
        exact_count=counts["EXACT"],
        current_exact_count=counts["CURRENT_EXACT"],
    )


def _hash_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()
