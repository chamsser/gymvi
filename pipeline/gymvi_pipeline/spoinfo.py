from __future__ import annotations

import json
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from typing import Any
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from gymvi_pipeline import __version__


CATALOG_DATASET_ID = "data-go-kr-15096288"
CATALOG_NAME = "전국체육시설표준데이터"
CATALOG_URL = (
    "https://www.data.go.kr/data/15096288/standard.do?recommendDataYn=Y"
)
PROVIDER_NAME = "서울올림픽기념국민체육진흥공단"
PROVIDER_SYSTEM_NAME = "체육시설알리미"
PROVIDER_SYSTEM_URL = "https://www.spoinfo.or.kr/"
FACILITY_SEARCH_ENDPOINT = "https://www.spoinfo.or.kr/map/getFaciListJson.do"
SOURCE_LICENSE = "이용허락범위 제한 없음"
SOURCE_MODIFIED_DATE = "2025-05-19"


class SourceResponseError(ValueError):
    """Raised when the official response cannot satisfy the source contract."""


@dataclass(frozen=True)
class FetchResult:
    requested_url: str
    status_code: int
    content_type: str
    body: bytes
    transport: str = "live"


@dataclass(frozen=True)
class FacilityRequest:
    sido_code: str = "1100000000"
    sigungu_code: str = "1150000000"
    center_latitude: str = "37.5509"
    center_longitude: str = "126.8495"
    national_physical_center_only: bool = True
    page_number: int = 1
    requested_rows: int = 30

    def parameters(self) -> dict[str, str]:
        # These names mirror the provider system's public facility-search request.
        # Empty filters are retained so metadata fully describes the request.
        return {
            "pageNo": str(self.page_number),
            "numOfRows": str(self.requested_rows),
            "faciNm": "",
            "locbase": "",
            "searchType": "faciCustomize",
            "lat": self.center_latitude,
            "lng": self.center_longitude,
            "locbase2": "",
            "param1X": "",
            "param1Y": "",
            "param2X": "",
            "param2Y": "",
            "cpCd": self.sido_code,
            "cpbCd": self.sigungu_code,
            "sports": "",
            "sportsEtc": "",
            "sex": "",
            "age": "",
            "dietYn": "",
            "adultYn": "",
            "physicalYn": "",
            "mentalYn": "",
            "spaerYn": "",
            "healthYn": "",
            "parkingYn": "",
            "shuttleYn": "",
            "leaderYn": "",
            "guardYn": "",
            "weekendYn": "",
            "programYn": "",
            "schkTotGrdCd": "",
            "natPhycFaciYn": (
                "Y" if self.national_physical_center_only else ""
            ),
            "safetyFaciYn": "",
            "sportProgramFaciYn": "",
            "sportProgramFaciYn1": "",
            "safeMngmtCrtFcFcltyYn": "",
            "safeSecureCertFcltYn": "",
            "sortType": "",
            "mainYn": "N",
        }

    def url(self) -> str:
        return f"{FACILITY_SEARCH_ENDPOINT}?{urlencode(self.parameters())}"


@dataclass(frozen=True)
class ParsedFacilityResponse:
    rows: tuple[dict[str, Any], ...]
    total_count: int
    page_number: int
    result_code: str
    result_message: str


Fetcher = Callable[[FacilityRequest, float], FetchResult]


def fetch_facilities(request: FacilityRequest, timeout_seconds: float) -> FetchResult:
    requested_url = request.url()
    http_request = Request(
        requested_url,
        headers={
            "Accept": "application/json",
            "Referer": "https://www.spoinfo.or.kr/map/map.do",
            "User-Agent": (
                f"GymviPublicDataPipeline/{__version__} (+{CATALOG_URL})"
            ),
        },
        method="GET",
    )
    with urlopen(http_request, timeout=timeout_seconds) as response:
        return FetchResult(
            requested_url=requested_url,
            status_code=response.status,
            content_type=response.headers.get("Content-Type", ""),
            body=response.read(),
        )


def parse_facility_response(payload: bytes) -> ParsedFacilityResponse:
    try:
        document = json.loads(payload.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SourceResponseError("시설 원본 응답이 UTF-8 JSON이 아닙니다.") from exc

    try:
        response = _mapping(document, "response")
        header = _mapping(response, "header")
        body = _mapping(response, "body")
        items = _mapping(body, "items")
    except (KeyError, TypeError) as exc:
        raise SourceResponseError(
            "시설 원본 응답의 response/header/body/items 구조가 없습니다."
        ) from exc

    result_code = str(header.get("resultCode", ""))
    result_message = str(header.get("resultMsg", ""))
    if result_code not in {"00", "0000"}:
        raise SourceResponseError(
            f"시설 원본 응답 실패: resultCode={result_code or 'missing'}"
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
        raise SourceResponseError("시설 원본 item이 객체 또는 객체 배열이 아닙니다.")

    total_count = _nonnegative_int(items.get("totalCount", len(rows)), "totalCount")
    page_number = _positive_int(items.get("pageNo", 1), "pageNo")
    if total_count < len(rows):
        raise SourceResponseError("totalCount가 실제 응답 행 수보다 작습니다.")

    return ParsedFacilityResponse(
        rows=tuple(rows),
        total_count=total_count,
        page_number=page_number,
        result_code=result_code,
        result_message=result_message,
    )


def _mapping(parent: Mapping[str, Any], key: str) -> Mapping[str, Any]:
    value = parent[key]
    if not isinstance(value, Mapping):
        raise TypeError(key)
    return value


def _nonnegative_int(value: Any, field: str) -> int:
    try:
        number = int(value)
    except (TypeError, ValueError) as exc:
        raise SourceResponseError(f"{field}가 정수가 아닙니다.") from exc
    if number < 0:
        raise SourceResponseError(f"{field}가 음수입니다.")
    return number


def _positive_int(value: Any, field: str) -> int:
    number = _nonnegative_int(value, field)
    if number == 0:
        raise SourceResponseError(f"{field}가 0입니다.")
    return number
