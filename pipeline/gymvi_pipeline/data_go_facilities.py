from __future__ import annotations

import json
from collections.abc import Callable, Mapping
from dataclasses import dataclass, field
from typing import Any
from urllib.parse import unquote, urlencode
from urllib.request import Request, urlopen

from gymvi_pipeline import __version__
from gymvi_pipeline.spoinfo import SourceResponseError


CATALOG_DATASET_ID = "data-go-kr-15113986"
CATALOG_NAME = "서울올림픽기념국민체육진흥공단_전국체육시설 정보"
CATALOG_URL = "https://www.data.go.kr/data/15113986/openapi.do"
PROVIDER_NAME = "서울올림픽기념국민체육진흥공단"
PROVIDER_SYSTEM_NAME = "체육시설알리미"
PROVIDER_SYSTEM_URL = "https://www.spoinfo.or.kr/"
FACILITY_OPENAPI_ENDPOINT = (
    "https://apis.data.go.kr/B551014/SRVC_API_SFMS_FACI/"
    "TODZ_API_SFMS_FACI"
)
SOURCE_LICENSE = "이용허락범위 제한 없음"
SOURCE_MODIFIED_DATE = "2025-05-19"
MAX_ROWS_PER_PAGE = 1_000


@dataclass(frozen=True)
class DataGoFacilityRequest:
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
        # 포털은 인코딩/디코딩 키를 모두 보여주므로 먼저 한 번 풀고 정확히
        # 한 번만 URL 인코딩한다. 이 URL은 메타데이터나 오류에 기록하지 않는다.
        parameters = {
            "serviceKey": unquote(self.service_key.strip()),
            **self.public_parameters(),
        }
        return f"{FACILITY_OPENAPI_ENDPOINT}?{urlencode(parameters)}"


@dataclass(frozen=True)
class DataGoFetchResult:
    page_number: int
    status_code: int
    content_type: str
    body: bytes
    transport: str = "live"


@dataclass(frozen=True)
class ParsedDataGoFacilityResponse:
    rows: tuple[dict[str, Any], ...]
    total_count: int
    page_number: int
    rows_per_page: int
    result_code: str
    result_message: str


DataGoFetcher = Callable[[DataGoFacilityRequest, float], DataGoFetchResult]


def fetch_data_go_facilities(
    request: DataGoFacilityRequest,
    timeout_seconds: float,
) -> DataGoFetchResult:
    http_request = Request(
        request.url(),
        headers={
            "Accept": "application/json",
            "User-Agent": (
                f"GymviPublicDataPipeline/{__version__} (+{CATALOG_URL})"
            ),
        },
        method="GET",
    )
    with urlopen(http_request, timeout=timeout_seconds) as response:
        return DataGoFetchResult(
            page_number=request.page_number,
            status_code=response.status,
            content_type=response.headers.get("Content-Type", ""),
            body=response.read(),
        )


def parse_data_go_facility_response(
    payload: bytes,
) -> ParsedDataGoFacilityResponse:
    try:
        document = json.loads(payload.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SourceResponseError(
            "전국 시설 OpenAPI 응답이 UTF-8 JSON이 아닙니다."
        ) from exc
    if not isinstance(document, Mapping):
        raise SourceResponseError("전국 시설 OpenAPI 최상위 값이 객체가 아닙니다.")

    # 이 API의 정식 JSON은 header/body가 최상위에 있다. 일부 공공데이터
    # 게이트웨이가 response 래퍼를 추가해도 동일 계약으로 검증한다.
    response = document.get("response", document)
    if not isinstance(response, Mapping):
        raise SourceResponseError("전국 시설 OpenAPI response가 객체가 아닙니다.")
    try:
        header = _mapping(response, "header")
        body = _mapping(response, "body")
        items = _mapping(body, "items")
    except (KeyError, TypeError) as exc:
        raise SourceResponseError(
            "전국 시설 OpenAPI의 header/body/items 구조가 없습니다."
        ) from exc

    result_code = str(header.get("resultCode", ""))
    result_message = str(header.get("resultMsg", ""))
    if result_code not in {"00", "0000"}:
        raise SourceResponseError(
            f"전국 시설 OpenAPI 실패: resultCode={result_code or 'missing'}"
        )

    raw_rows = items.get("item")
    if raw_rows is None:
        rows: list[dict[str, Any]] = []
    elif isinstance(raw_rows, Mapping):
        rows = [dict(raw_rows)]
    elif isinstance(raw_rows, list) and all(
        isinstance(row, Mapping) for row in raw_rows
    ):
        rows = [dict(row) for row in raw_rows]
    else:
        raise SourceResponseError("전국 시설 OpenAPI item 형식이 올바르지 않습니다.")

    total_count = _nonnegative_int(body.get("totalCount"), "totalCount")
    page_number = _positive_int(body.get("pageNo"), "pageNo")
    rows_per_page = _positive_int(body.get("numOfRows"), "numOfRows")
    if rows_per_page > MAX_ROWS_PER_PAGE:
        raise SourceResponseError("numOfRows가 공식 최대값 1000을 넘었습니다.")
    if total_count < len(rows):
        raise SourceResponseError("totalCount가 실제 응답 행 수보다 작습니다.")
    return ParsedDataGoFacilityResponse(
        rows=tuple(rows),
        total_count=total_count,
        page_number=page_number,
        rows_per_page=rows_per_page,
        result_code=result_code,
        result_message=result_message,
    )


def _mapping(parent: Mapping[str, Any], key: str) -> Mapping[str, Any]:
    value = parent[key]
    if not isinstance(value, Mapping):
        raise TypeError(key)
    return value


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
