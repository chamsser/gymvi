from __future__ import annotations

import json
from collections.abc import Mapping
from dataclasses import dataclass, field
from datetime import date
from typing import Any
from urllib.parse import unquote, urlencode

from gymvi_pipeline.spoinfo import SourceResponseError


CATALOG_DATASET_ID = "data-go-kr-15107773"
CATALOG_NAME = "서울올림픽기념국민체육진흥공단_전국체육시설 안전점검 정보"
CATALOG_URL = "https://www.data.go.kr/data/15107773/openapi.do"
SAFETY_OPENAPI_ENDPOINT = (
    "https://apis.data.go.kr/B551014/SRVC_API_FACI_SCHK_RESULT/"
    "TODZ_API_FACI_SAFETY"
)
SOURCE_LICENSE = "이용허락범위 제한 없음"
MAX_ROWS_PER_PAGE = 1_000


@dataclass(frozen=True)
class DataGoSafetyRequest:
    service_key: str = field(repr=False)
    page_number: int = 1
    requested_rows: int = MAX_ROWS_PER_PAGE

    def __post_init__(self) -> None:
        if not self.service_key.strip():
            raise ValueError("공공데이터포털 서비스키가 비어 있습니다.")
        if self.page_number < 1:
            raise ValueError("page_number는 1 이상이어야 합니다.")
        if not 1 <= self.requested_rows <= MAX_ROWS_PER_PAGE:
            raise ValueError(
                f"requested_rows는 1 이상 {MAX_ROWS_PER_PAGE} 이하여야 합니다."
            )

    def public_parameters(self) -> dict[str, str]:
        return {
            "pageNo": str(self.page_number),
            "numOfRows": str(self.requested_rows),
            "resultType": "json",
        }

    def url(self) -> str:
        parameters = {
            "serviceKey": unquote(self.service_key.strip()),
            **self.public_parameters(),
        }
        return f"{SAFETY_OPENAPI_ENDPOINT}?{urlencode(parameters)}"


@dataclass(frozen=True)
class ParsedSafetyResponse:
    rows: tuple[dict[str, Any], ...]
    total_count: int
    page_number: int
    rows_per_page: int
    result_code: str
    result_message: str


@dataclass(frozen=True)
class SafetyRecord:
    facility_code: str
    facility_name: str | None
    total_grade_code: str | None
    total_grade_name: str | None
    inspection_date: str | None
    publication_date: str | None
    base_date: str | None
    facility_updated_date: str | None
    source_record_key: str
    raw: dict[str, Any]


def parse_safety_response(payload: bytes) -> ParsedSafetyResponse:
    try:
        document = json.loads(payload.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SourceResponseError(
            "안전점검 OpenAPI 응답이 UTF-8 JSON이 아닙니다."
        ) from exc
    if not isinstance(document, Mapping):
        raise SourceResponseError("안전점검 OpenAPI 최상위 값이 객체가 아닙니다.")

    response = document.get("response", document)
    if not isinstance(response, Mapping):
        raise SourceResponseError("안전점검 OpenAPI response가 객체가 아닙니다.")
    try:
        header = _mapping(response, "header")
        body = _mapping(response, "body")
    except (KeyError, TypeError) as exc:
        raise SourceResponseError(
            "안전점검 OpenAPI의 header/body 구조가 없습니다."
        ) from exc
    result_code = str(header.get("resultCode", ""))
    result_message = str(header.get("resultMsg", ""))
    if result_code not in {"00", "0000"}:
        raise SourceResponseError(
            f"안전점검 OpenAPI 실패: resultCode={result_code or 'missing'}"
        )

    items_value = body.get("items", {})
    if not isinstance(items_value, Mapping):
        raise SourceResponseError("안전점검 OpenAPI items가 객체가 아닙니다.")
    raw_rows = items_value.get("item")
    if raw_rows is None:
        rows: list[dict[str, Any]] = []
    elif isinstance(raw_rows, Mapping):
        rows = [dict(raw_rows)]
    elif isinstance(raw_rows, list) and all(
        isinstance(row, Mapping) for row in raw_rows
    ):
        rows = [dict(row) for row in raw_rows]
    else:
        raise SourceResponseError("안전점검 OpenAPI item 형식이 올바르지 않습니다.")

    total_count = _nonnegative_int(body.get("totalCount"), "totalCount")
    page_number = _positive_int(body.get("pageNo"), "pageNo")
    rows_per_page = _positive_int(body.get("numOfRows"), "numOfRows")
    if rows_per_page > MAX_ROWS_PER_PAGE:
        raise SourceResponseError("numOfRows가 공식 최대값 1000을 넘었습니다.")
    if total_count < len(rows):
        raise SourceResponseError("totalCount가 실제 응답 행 수보다 작습니다.")
    return ParsedSafetyResponse(
        rows=tuple(rows),
        total_count=total_count,
        page_number=page_number,
        rows_per_page=rows_per_page,
        result_code=result_code,
        result_message=result_message,
    )


def normalize_safety_rows(rows: tuple[dict[str, Any], ...]) -> tuple[SafetyRecord, ...]:
    records: list[SafetyRecord] = []
    seen_keys: set[str] = set()
    for row in rows:
        facility_code = _text(row.get("faci_cd"))
        if facility_code is None:
            raise SourceResponseError("안전점검 행에 faci_cd가 없습니다.")
        inspection_date = _date(row.get("schk_visit_ymd"), "schk_visit_ymd")
        publication_date = _date(row.get("schk_open_ymd"), "schk_open_ymd")
        source_record_key = ":".join(
            [facility_code, inspection_date or "unknown", publication_date or "unknown"]
        )
        if source_record_key in seen_keys:
            raise SourceResponseError("안전점검 원본 레코드 키가 중복됩니다.")
        seen_keys.add(source_record_key)
        records.append(
            SafetyRecord(
                facility_code=facility_code,
                facility_name=_text(row.get("faci_nm")),
                total_grade_code=_text(row.get("schk_tot_grd_cd")),
                total_grade_name=_text(row.get("schk_tot_grd_nm")),
                inspection_date=inspection_date,
                publication_date=publication_date,
                base_date=_date(row.get("base_ymd"), "base_ymd"),
                facility_updated_date=_date(
                    row.get("faci_upd_ymd"), "faci_upd_ymd"
                ),
                source_record_key=source_record_key,
                raw=dict(row),
            )
        )
    return tuple(records)


def _mapping(parent: Mapping[str, Any], key: str) -> Mapping[str, Any]:
    value = parent[key]
    if not isinstance(value, Mapping):
        raise TypeError(key)
    return value


def _text(value: Any) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    return normalized or None


def _date(value: Any, field_name: str) -> str | None:
    text = _text(value)
    if text is None:
        return None
    digits = text.replace("-", "").replace(".", "")
    if len(digits) != 8 or not digits.isdigit():
        raise SourceResponseError(f"{field_name} 날짜 형식이 올바르지 않습니다.")
    normalized = f"{digits[:4]}-{digits[4:6]}-{digits[6:]}"
    try:
        date.fromisoformat(normalized)
    except ValueError as exc:
        raise SourceResponseError(
            f"{field_name} 날짜 값이 올바르지 않습니다."
        ) from exc
    return normalized


def _nonnegative_int(value: Any, field_name: str) -> int:
    try:
        result = int(value)
    except (TypeError, ValueError) as exc:
        raise SourceResponseError(f"{field_name}가 정수가 아닙니다.") from exc
    if result < 0:
        raise SourceResponseError(f"{field_name}가 음수입니다.")
    return result


def _positive_int(value: Any, field_name: str) -> int:
    result = _nonnegative_int(value, field_name)
    if result == 0:
        raise SourceResponseError(f"{field_name}가 0입니다.")
    return result
