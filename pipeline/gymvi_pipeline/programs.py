from __future__ import annotations

import csv
import hashlib
import io
import json
import math
import re
import unicodedata
from collections.abc import Iterator, Mapping, Sequence
from dataclasses import dataclass
from datetime import date
from typing import Any, TextIO

from gymvi_pipeline.spoinfo import SourceResponseError


CATALOG_DATASET_ID = "culture-bigdata-c3b8fb69-307d-4ae7-ab42-d0314c89ef47"
CATALOG_NAME = "공공체육시설 프로그램 정보"
CATALOG_URL = (
    "https://www.bigdata-culture.kr/bigdata/user/data_market/detail.do"
    "?id=c3b8fb69-307d-4ae7-ab42-d0314c89ef47"
)
PUBLICATION_REQUIRES_LICENSE_EVIDENCE = True
WEEKDAY_TOKEN = re.compile(r"(?:요일)?([월화수목금토일]+)")

REQUIRED_COLUMNS = {
    "FCLTY_NM",
    "PROGRM_TY_NM",
    "PROGRM_NM",
    "PROGRM_BEGIN_DE",
    "PROGRM_END_DE",
    "PROGRM_ESTBL_WKDAY_NM",
    "PROGRM_ESTBL_TIZN_VALUE",
    "PROGRM_RCRIT_NMPR_CO",
    "PROGRM_PRC",
    "PROGRM_PRC_TY_NM",
}


@dataclass(frozen=True)
class ProgramRecord:
    program_id: str
    facility_name: str
    facility_type_name: str | None
    industry_name: str | None
    program_type_name: str | None
    program_name: str
    target_name: str | None
    begin_date: str | None
    end_date: str | None
    weekdays: tuple[str, ...]
    time_value: str | None
    recruitment_count: int | None
    price_won: int | None
    price_type_name: str | None
    homepage_url: str | None
    road_address: str | None
    phone: str | None
    latitude: float | None
    longitude: float | None
    raw: dict[str, Any]


@dataclass(frozen=True)
class ProgramFacilityJoin:
    program_id: str
    facility_id: str | None
    state: str
    reason_codes: tuple[str, ...]


@dataclass
class ProgramCsvStats:
    input_rows: int = 0
    normalized_rows: int = 0
    duplicate_rows: int = 0
    variant_duplicate_rows: int = 0
    missing_type_rows: int = 0


def parse_program_csv(payload: bytes) -> tuple[ProgramRecord, ...]:
    try:
        text = payload.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise SourceResponseError("프로그램 원본 CSV가 UTF-8이 아닙니다.") from exc
    return tuple(iter_program_csv(io.StringIO(text)))


def iter_program_csv(stream: TextIO) -> Iterator[ProgramRecord]:
    for _, program in iter_program_csv_rows(stream):
        yield program


def iter_program_csv_rows(
    stream: TextIO,
    *,
    skip_duplicates: bool = False,
    stats: ProgramCsvStats | None = None,
) -> Iterator[tuple[int, ProgramRecord]]:
    reader = csv.DictReader(stream)
    if reader.fieldnames is None:
        raise SourceResponseError("프로그램 원본 CSV 헤더가 없습니다.")
    missing = REQUIRED_COLUMNS - set(reader.fieldnames)
    if missing:
        raise SourceResponseError(
            "프로그램 원본 CSV 필수 열이 없습니다: " + ",".join(sorted(missing))
        )
    seen_ids: dict[str, bytes] = {}
    for source_row_number, row in enumerate(reader, start=2):
        if stats is not None:
            stats.input_rows += 1
        if None in row:
            raise SourceResponseError(
                "프로그램 원본 CSV 행에 헤더보다 많은 열이 있습니다."
            )
        program = _normalize_program_row(dict(row))
        row_digest = hashlib.sha256(
            json.dumps(
                [row.get(key) or "" for key in reader.fieldnames],
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")
        ).digest()
        previous_digest = seen_ids.get(program.program_id)
        if previous_digest is not None:
            if not skip_duplicates:
                raise SourceResponseError("프로그램 원본에서 같은 프로그램 키가 중복됩니다.")
            if stats is not None:
                stats.duplicate_rows += 1
                if previous_digest != row_digest:
                    stats.variant_duplicate_rows += 1
            continue
        seen_ids[program.program_id] = row_digest
        if stats is not None:
            stats.normalized_rows += 1
            if program.program_type_name is None:
                stats.missing_type_rows += 1
        yield source_row_number, program


def parse_program_json(payload: bytes) -> tuple[ProgramRecord, ...]:
    try:
        document = json.loads(payload.decode("utf-8-sig"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise SourceResponseError("프로그램 원본 JSON 형식이 올바르지 않습니다.") from exc
    rows: Any = document
    if isinstance(document, Mapping):
        rows = document.get("data", document.get("items", document.get("records")))
    if not isinstance(rows, list) or not all(isinstance(row, Mapping) for row in rows):
        raise SourceResponseError("프로그램 원본 JSON 행 배열이 없습니다.")
    return normalize_program_rows(tuple(dict(row) for row in rows))


def normalize_program_rows(rows: Sequence[dict[str, Any]]) -> tuple[ProgramRecord, ...]:
    seen_ids: set[str] = set()
    normalized: list[ProgramRecord] = []
    for row in rows:
        program = _normalize_program_row(row)
        if program.program_id in seen_ids:
            raise SourceResponseError("프로그램 원본에서 같은 프로그램 키가 중복됩니다.")
        seen_ids.add(program.program_id)
        normalized.append(program)
    return tuple(normalized)


def _normalize_program_row(row: dict[str, Any]) -> ProgramRecord:
    facility_name = _required_text(row, "FCLTY_NM")
    program_type = _text(row.get("PROGRM_TY_NM"))
    program_name = _required_text(row, "PROGRM_NM")
    begin_date = _date(row.get("PROGRM_BEGIN_DE"), "PROGRM_BEGIN_DE")
    end_date = _date(row.get("PROGRM_END_DE"), "PROGRM_END_DE")
    if begin_date and end_date and begin_date > end_date:
        raise SourceResponseError("프로그램 시작일이 종료일보다 늦습니다.")
    target_name = _text(row.get("PROGRM_TRGET_NM"))
    weekdays_value = _text(row.get("PROGRM_ESTBL_WKDAY_NM"))
    time_value = _text(row.get("PROGRM_ESTBL_TIZN_VALUE"))
    recruitment_count = _nonnegative_int_or_none(
        row.get("PROGRM_RCRIT_NMPR_CO"), "PROGRM_RCRIT_NMPR_CO"
    )
    price_won = _nonnegative_int_or_none(row.get("PROGRM_PRC"), "PROGRM_PRC")
    price_type_name = _text(row.get("PROGRM_PRC_TY_NM"))
    homepage_url = _http_url_or_none(row.get("HMPG_URL"))
    road_address = _text(row.get("FCLTY_ADDR"))
    phone = _phone_or_none(row.get("FCLTY_TEL_NO"))
    canonical = json.dumps(
        [
            facility_name,
            road_address or "",
            phone or "",
            program_type or "",
            program_name,
            target_name or "",
            begin_date or "",
            end_date or "",
            weekdays_value or "",
            time_value or "",
            str(recruitment_count) if recruitment_count is not None else "",
            str(price_won) if price_won is not None else "",
            price_type_name or "",
            homepage_url or "",
        ],
        ensure_ascii=False,
        separators=(",", ":"),
    )
    program_id = "kspo-program:" + hashlib.sha256(
        canonical.encode("utf-8")
    ).hexdigest()[:24]
    return ProgramRecord(
        program_id=program_id,
        facility_name=facility_name,
        facility_type_name=_text(row.get("FCLTY_TY_NM")),
        industry_name=_text(row.get("INDUTY_NM")),
        program_type_name=program_type,
        program_name=program_name,
        target_name=target_name,
        begin_date=begin_date,
        end_date=end_date,
        weekdays=_weekdays(weekdays_value),
        time_value=time_value,
        recruitment_count=recruitment_count,
        price_won=price_won,
        price_type_name=price_type_name,
        homepage_url=homepage_url,
        road_address=road_address,
        phone=phone,
        latitude=_coordinate_or_none(row.get("FCLTY_LA"), latitude=True),
        longitude=_coordinate_or_none(row.get("FCLTY_LO"), latitude=False),
        raw=dict(row),
    )


def join_program_to_facilities(
    program: ProgramRecord,
    facilities: Sequence[dict[str, Any]],
    reviewed_matches: Mapping[str, str] | None = None,
) -> ProgramFacilityJoin:
    reviewed_facility_id = (reviewed_matches or {}).get(program.program_id)
    if reviewed_facility_id is not None:
        if not any(_facility_id(row) == reviewed_facility_id for row in facilities):
            raise SourceResponseError("검토된 프로그램 결합 대상 시설이 없습니다.")
        return ProgramFacilityJoin(
            program.program_id,
            reviewed_facility_id,
            "REVIEWED",
            ("MANUAL_REVIEW_MAPPING",),
        )

    candidates: list[tuple[int, str, tuple[str, ...]]] = []
    normalized_program_name = _normalize_text(program.facility_name)
    normalized_program_address = _normalize_text(program.road_address or "")
    normalized_program_phone = _digits(program.phone)
    for facility in facilities:
        facility_id = _facility_id(facility)
        if facility_id is None:
            continue
        reasons: list[str] = []
        exact_name = _normalize_text(_facility_name(facility)) == normalized_program_name
        if exact_name:
            reasons.append("NAME_EXACT")
        address = _normalize_text(_facility_address(facility))
        if normalized_program_address and address == normalized_program_address:
            reasons.append("ADDRESS_EXACT")
        phone = _digits(_facility_phone(facility))
        if normalized_program_phone and phone == normalized_program_phone:
            reasons.append("PHONE_EXACT")
        distance = _facility_distance(program, facility)
        if distance is not None and distance <= 50:
            reasons.append("COORDINATE_WITHIN_50M")
        if exact_name and any(
            reason in reasons
            for reason in ("ADDRESS_EXACT", "PHONE_EXACT", "COORDINATE_WITHIN_50M")
        ):
            candidates.append((2, facility_id, tuple(reasons)))
        elif exact_name or len(reasons) >= 2:
            candidates.append((1, facility_id, tuple(reasons)))

    exact = sorted(candidate for candidate in candidates if candidate[0] == 2)
    if len(exact) == 1:
        _, facility_id, reasons = exact[0]
        return ProgramFacilityJoin(program.program_id, facility_id, "EXACT", reasons)
    plausible = sorted(candidates, key=lambda item: (-item[0], item[1]))
    if plausible:
        _, facility_id, reasons = plausible[0]
        reason_codes = reasons + (("AMBIGUOUS_MATCH",) if len(plausible) > 1 else ())
        return ProgramFacilityJoin(
            program.program_id, facility_id, "CANDIDATE", reason_codes
        )
    return ProgramFacilityJoin(
        program.program_id, None, "REJECTED", ("NO_TRUSTWORTHY_MATCH",)
    )


def _required_text(row: Mapping[str, Any], field_name: str) -> str:
    value = _text(row.get(field_name))
    if value is None:
        raise SourceResponseError(f"프로그램 원본 {field_name} 값이 없습니다.")
    return value


def _text(value: Any) -> str | None:
    if value is None:
        return None
    result = str(value).strip()
    return result or None


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


def _weekdays(value: Any) -> tuple[str, ...]:
    text = _text(value)
    if text is None:
        return ()
    # The official CSV mostly packs days together ("화목", "요일월수금"). Tokens that are
    # not pure day names stay out of weekdays; the source value remains in raw.
    days: list[str] = []
    for part in re.split(r"[,/|\s]+", text):
        match = WEEKDAY_TOKEN.fullmatch(part.strip())
        if match:
            days.extend(match.group(1))
    return tuple(dict.fromkeys(days))


def _nonnegative_int_or_none(value: Any, field_name: str) -> int | None:
    text = _text(value)
    if text is None:
        return None
    normalized = text.replace(",", "").replace("원", "")
    if not re.fullmatch(r"[0-9]+(?:\.0+)?", normalized):
        raise SourceResponseError(f"{field_name} 값이 음이 아닌 정수가 아닙니다.")
    return int(normalized.split(".", 1)[0])


def _http_url_or_none(value: Any) -> str | None:
    text = _text(value)
    if text is None:
        return None
    if not text.startswith(("https://", "http://")):
        raise SourceResponseError("HMPG_URL 형식이 올바르지 않습니다.")
    return text


def _phone_or_none(value: Any) -> str | None:
    text = _text(value)
    if text is None:
        return None
    if not re.fullmatch(r"[0-9+()\-\s]{3,40}", text):
        raise SourceResponseError("FCLTY_TEL_NO 형식이 올바르지 않습니다.")
    return text


def _coordinate_or_none(value: Any, *, latitude: bool) -> float | None:
    text = _text(value)
    if text is None:
        return None
    try:
        result = float(text)
    except ValueError as exc:
        raise SourceResponseError("프로그램 시설 좌표 형식이 올바르지 않습니다.") from exc
    bounds = (-90.0, 90.0) if latitude else (-180.0, 180.0)
    if not math.isfinite(result) or not bounds[0] <= result <= bounds[1]:
        raise SourceResponseError("프로그램 시설 좌표 범위가 올바르지 않습니다.")
    return result


def _normalize_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value).lower()
    return re.sub(r"[^0-9a-z가-힣]", "", normalized)


def _digits(value: str | None) -> str:
    return re.sub(r"\D", "", value or "")


def _facility_id(facility: Mapping[str, Any]) -> str | None:
    return _text(facility.get("facility_id"))


def _facility_name(facility: Mapping[str, Any]) -> str:
    name = facility.get("name")
    if isinstance(name, Mapping):
        return _text(name.get("value")) or ""
    return _text(name) or ""


def _facility_address(facility: Mapping[str, Any]) -> str:
    address = facility.get("address")
    if isinstance(address, Mapping):
        road = address.get("road")
        if isinstance(road, Mapping):
            return _text(road.get("value")) or ""
        return _text(road) or _text(address.get("normalized")) or ""
    return _text(facility.get("road_address")) or ""


def _facility_phone(facility: Mapping[str, Any]) -> str | None:
    contact = facility.get("contact")
    if isinstance(contact, Mapping):
        phone = contact.get("phone")
        if isinstance(phone, Mapping):
            return _text(phone.get("value"))
        return _text(phone)
    return _text(facility.get("phone"))


def _facility_distance(
    program: ProgramRecord, facility: Mapping[str, Any]
) -> float | None:
    if program.latitude is None or program.longitude is None:
        return None
    location = facility.get("location")
    if not isinstance(location, Mapping):
        return None
    try:
        latitude = float(location["latitude"])
        longitude = float(location["longitude"])
    except (KeyError, TypeError, ValueError):
        return None
    latitude_delta = math.radians(latitude - program.latitude)
    longitude_delta = math.radians(longitude - program.longitude)
    origin_latitude = math.radians(program.latitude)
    destination_latitude = math.radians(latitude)
    haversine = math.sin(latitude_delta / 2) ** 2 + math.cos(
        origin_latitude
    ) * math.cos(destination_latitude) * math.sin(longitude_delta / 2) ** 2
    return 6_371_000.0 * 2 * math.asin(math.sqrt(min(1.0, max(0.0, haversine))))
