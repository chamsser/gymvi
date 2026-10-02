from __future__ import annotations

import argparse
import json
import sys
from datetime import date
from pathlib import Path
from typing import Sequence

from gymvi_pipeline.normalize import NormalizationReceipt, normalize_facility_snapshot
from gymvi_pipeline.spoinfo import FacilityRequest
from gymvi_pipeline.data_go_facilities import MAX_ROWS_PER_PAGE
from gymvi_pipeline.nationwide import acquire_nationwide_facility_snapshot
from gymvi_pipeline.program_import import import_program_csv
from gymvi_pipeline.program_csv_probe import probe_program_csv_joins
from gymvi_pipeline.program_join_report import analyze_program_joins
from gymvi_pipeline.workflow import (
    acquire_facility_snapshot,
    fixture_fetcher,
    run_facility_spike,
    run_nationwide_facilities,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="gymvi-pipeline",
        description="Gymvi의 추적 가능한 공공데이터 파이프라인",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    acquire = subparsers.add_parser(
        "acquire-facilities", help="지정 행정구역의 시설 원본과 메타데이터를 새로 저장"
    )
    _add_acquisition_arguments(acquire)

    normalize = subparsers.add_parser(
        "normalize-facilities", help="검증된 시설 원본 스냅샷을 정규화"
    )
    normalize.add_argument("--snapshot-dir", type=Path, required=True)
    normalize.add_argument("--output-root", type=Path, required=True)

    run = subparsers.add_parser(
        "run-facility-spike", help="시설 수집·불변 저장·정규화·QA를 한 번에 실행"
    )
    _add_acquisition_arguments(run)

    acquire_nationwide = subparsers.add_parser(
        "acquire-nationwide-facilities",
        help="공식 OpenAPI의 전국 시설 전체 페이지를 불변 스냅샷으로 저장",
    )
    _add_nationwide_arguments(acquire_nationwide)

    run_nationwide = subparsers.add_parser(
        "run-nationwide-facilities",
        help="공식 OpenAPI 전국 시설 전체 페이지를 수집·정규화·QA",
    )
    _add_nationwide_arguments(run_nationwide)

    import_programs = subparsers.add_parser(
        "import-program-csv",
        help="수동 다운로드한 프로그램 CSV를 검증·불변 저장·정규화",
    )
    import_programs.add_argument("--source-file", type=Path, required=True)
    import_programs.add_argument(
        "--license-evidence-file", type=Path, required=True
    )
    import_programs.add_argument("--source-modified-date", required=True)
    import_programs.add_argument(
        "--encoding",
        choices=("utf-8-sig", "cp949"),
        default="utf-8-sig",
    )
    import_programs.add_argument("--output-root", type=Path, required=True)

    analyze_joins = subparsers.add_parser(
        "analyze-program-joins",
        help="정규화된 전국 시설·프로그램의 결합 분포와 강서구 추적을 산출",
    )
    analyze_joins.add_argument("--programs-file", type=Path, required=True)
    analyze_joins.add_argument("--program-qa-file", type=Path, required=True)
    analyze_joins.add_argument("--facilities-file", type=Path, required=True)
    analyze_joins.add_argument("--facility-qa-file", type=Path, required=True)
    analyze_joins.add_argument("--as-of-date", type=date.fromisoformat, required=True)
    analyze_joins.add_argument("--output-directory", type=Path, required=True)

    probe_joins = subparsers.add_parser(
        "probe-program-csv-joins",
        help="이용허락 미확인 CSV를 게시 없이 비공개 시설 결합 분포만 측정",
    )
    probe_joins.add_argument("--source-file", type=Path, required=True)
    probe_joins.add_argument("--source-modified-date", type=date.fromisoformat, required=True)
    probe_joins.add_argument("--facilities-file", type=Path, required=True)
    probe_joins.add_argument("--facility-qa-file", type=Path, required=True)
    probe_joins.add_argument("--as-of-date", type=date.fromisoformat, required=True)
    probe_joins.add_argument("--output-directory", type=Path, required=True)
    probe_joins.add_argument("--focus-region")
    probe_joins.add_argument(
        "--encoding", choices=("utf-8-sig", "cp949"), default="utf-8-sig"
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "probe-program-csv-joins":
            receipt = probe_program_csv_joins(
                source_file=args.source_file,
                source_modified_date=args.source_modified_date,
                facilities_file=args.facilities_file,
                facility_qa_file=args.facility_qa_file,
                output_directory=args.output_directory,
                as_of_date=args.as_of_date,
                focus_region=args.focus_region,
                encoding=args.encoding,
            )
            _print_json(
                {
                    "report_path": str(receipt.report_path),
                    "input_rows": receipt.input_rows,
                    "unique_rows": receipt.unique_rows,
                    "exact_count": receipt.exact_count,
                    "current_exact_count": receipt.current_exact_count,
                    "publication_allowed": False,
                }
            )
            return 0

        if args.command == "analyze-program-joins":
            receipt = analyze_program_joins(
                programs_file=args.programs_file,
                program_qa_file=args.program_qa_file,
                facilities_file=args.facilities_file,
                facility_qa_file=args.facility_qa_file,
                output_directory=args.output_directory,
                as_of_date=args.as_of_date,
            )
            _print_json(
                {
                    "report_path": str(receipt.report_path),
                    "joins_path": str(receipt.joins_path),
                    "program_count": receipt.program_count,
                    "exact_count": receipt.exact_count,
                    "current_exact_count": receipt.current_exact_count,
                }
            )
            return 0

        if args.command == "import-program-csv":
            receipt = import_program_csv(
                source_file=args.source_file,
                license_evidence_file=args.license_evidence_file,
                output_root=args.output_root,
                source_modified_date=args.source_modified_date,
                encoding=args.encoding,
            )
            _print_json(
                {
                    "snapshot_id": receipt.snapshot_id,
                    "snapshot_directory": str(receipt.snapshot_directory),
                    "response_sha256": receipt.response_sha256,
                    "row_count": receipt.row_count,
                    "input_row_count": receipt.input_row_count,
                    "duplicate_row_count": receipt.duplicate_row_count,
                    "normalized_path": str(receipt.normalized_path),
                    "qa_report_path": str(receipt.qa_report_path),
                    "qa_status": "PASS",
                }
            )
            return 0

        if args.command == "normalize-facilities":
            normalized = normalize_facility_snapshot(
                snapshot_directory=args.snapshot_dir,
                output_root=args.output_root,
            )
            _print_json(_normalization_payload(normalized))
            return 0 if normalized.status == "PASS" else 2

        if args.command in {
            "acquire-nationwide-facilities",
            "run-nationwide-facilities",
        }:
            service_key = _read_service_key(
                args.service_key_file,
                from_stdin=args.service_key_stdin,
            )
            if args.command == "acquire-nationwide-facilities":
                snapshot = acquire_nationwide_facility_snapshot(
                    output_root=args.output_root,
                    service_key=service_key,
                    requested_rows=args.requested_rows,
                    timeout_seconds=args.timeout,
                )
                _print_json(
                    {
                        "snapshot_id": snapshot.snapshot_id,
                        "snapshot_directory": str(snapshot.snapshot_directory),
                        "response_sha256": snapshot.response_sha256,
                        "row_count": snapshot.row_count,
                        "page_count": snapshot.page_count,
                    }
                )
                return 0
            receipt = run_nationwide_facilities(
                output_root=args.output_root,
                service_key=service_key,
                requested_rows=args.requested_rows,
                timeout_seconds=args.timeout,
            )
            _print_json(
                {
                    "snapshot_id": receipt.snapshot.snapshot_id,
                    "snapshot_directory": str(receipt.snapshot.snapshot_directory),
                    "response_sha256": receipt.snapshot.response_sha256,
                    "page_count": receipt.snapshot.page_count,
                    **_normalization_payload(receipt.normalization),
                }
            )
            return 0 if receipt.normalization.status == "PASS" else 2

        request = FacilityRequest(
            sido_code=args.sido_code,
            sigungu_code=args.sigungu_code,
            center_latitude=args.center_latitude,
            center_longitude=args.center_longitude,
            national_physical_center_only=not (
                args.include_all or args.include_all_gangseo
            ),
            requested_rows=args.requested_rows,
        )
        fetcher = fixture_fetcher(args.fixture) if args.fixture else None

        if args.command == "acquire-facilities":
            if fetcher is None:
                snapshot = acquire_facility_snapshot(
                    output_root=args.output_root,
                    request=request,
                    timeout_seconds=args.timeout,
                )
            else:
                snapshot = acquire_facility_snapshot(
                    output_root=args.output_root,
                    request=request,
                    timeout_seconds=args.timeout,
                    fetcher=fetcher,
                )
            _print_json(
                {
                    "snapshot_id": snapshot.snapshot_id,
                    "snapshot_directory": str(snapshot.snapshot_directory),
                    "response_sha256": snapshot.response_sha256,
                    "row_count": snapshot.row_count,
                }
            )
            return 0

        if fetcher is None:
            receipt = run_facility_spike(
                output_root=args.output_root,
                request=request,
                timeout_seconds=args.timeout,
            )
        else:
            receipt = run_facility_spike(
                output_root=args.output_root,
                request=request,
                timeout_seconds=args.timeout,
                fetcher=fetcher,
            )
        payload = {
            "snapshot_id": receipt.snapshot.snapshot_id,
            "snapshot_directory": str(receipt.snapshot.snapshot_directory),
            "response_sha256": receipt.snapshot.response_sha256,
            **_normalization_payload(receipt.normalization),
        }
        _print_json(payload)
        return 0 if receipt.normalization.status == "PASS" else 2
    except Exception as exc:  # CLI boundary: request details are never echoed.
        print(
            json.dumps(
                {"status": "ERROR", "error": str(exc)},
                ensure_ascii=False,
            ),
            file=sys.stderr,
        )
        return 1


def _add_acquisition_arguments(parser: argparse.ArgumentParser) -> None:
    defaults = FacilityRequest()
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--sido-code", default=defaults.sido_code)
    parser.add_argument("--sigungu-code", default=defaults.sigungu_code)
    parser.add_argument("--center-latitude", default=defaults.center_latitude)
    parser.add_argument("--center-longitude", default=defaults.center_longitude)
    parser.add_argument(
        "--requested-rows",
        type=int,
        default=defaults.requested_rows,
        choices=range(1, 201),
        metavar="1..200",
    )
    parser.add_argument(
        "--include-all",
        action="store_true",
        help="국민체육센터 필터를 제거하고 지정 행정구역의 공개 시설을 수집",
    )
    parser.add_argument(
        "--include-all-gangseo",
        action="store_true",
        help=argparse.SUPPRESS,
    )
    parser.add_argument(
        "--fixture",
        type=Path,
        help="테스트 전용 원본 파일. 지정한 실행은 실제 수집 증거가 아닙니다.",
    )


def _add_nationwide_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--output-root", type=Path, required=True)
    service_key_source = parser.add_mutually_exclusive_group(required=True)
    service_key_source.add_argument("--service-key-file", type=Path)
    service_key_source.add_argument(
        "--service-key-stdin",
        action="store_true",
        help="서비스키 한 개를 표준 입력에서 읽고 디스크에 저장하지 않음",
    )
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument(
        "--requested-rows",
        type=int,
        default=MAX_ROWS_PER_PAGE,
        choices=range(1, MAX_ROWS_PER_PAGE + 1),
        metavar=f"1..{MAX_ROWS_PER_PAGE}",
    )


def _read_service_key(path: Path | None, *, from_stdin: bool = False) -> str:
    if from_stdin:
        value = sys.stdin.read().strip()
    else:
        if path is None:
            raise ValueError("공공데이터포털 서비스키 입력 경로가 필요합니다.")
        resolved = path.resolve()
        if not resolved.is_file():
            raise ValueError("공공데이터포털 서비스키 파일을 찾을 수 없습니다.")
        value = resolved.read_text(encoding="utf-8-sig").strip()
    if not value or any(character.isspace() for character in value):
        raise ValueError("서비스키 입력은 공백 없는 키 한 개만 포함해야 합니다.")
    if len(value) > 1_024:
        raise ValueError("서비스키 형식이 올바르지 않습니다.")
    return value


def _normalization_payload(receipt: NormalizationReceipt) -> dict[str, object]:
    return {
        "qa_status": receipt.status,
        "input_row_count": receipt.input_row_count,
        "normalized_row_count": receipt.normalized_row_count,
        "rejected_row_count": receipt.rejected_row_count,
        "normalized_path": str(receipt.normalized_path),
        "qa_report_path": str(receipt.qa_report_path),
        "error_count": receipt.error_count,
    }


def _print_json(value: object) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True))
