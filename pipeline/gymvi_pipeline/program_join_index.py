from __future__ import annotations

from collections import defaultdict
from collections.abc import Iterable, Mapping
from typing import Any

from gymvi_pipeline.programs import (
    ProgramFacilityJoin,
    ProgramRecord,
    _digits,
    _facility_address,
    _facility_id,
    _facility_name,
    _facility_phone,
    _normalize_text,
    join_program_to_facilities,
)
from gymvi_pipeline.spoinfo import SourceResponseError


class FacilityJoinIndex:
    """Find a small superset of the facilities the authoritative join may accept."""

    def __init__(self, facilities: Iterable[dict[str, Any]]) -> None:
        self._by_id: dict[str, dict[str, Any]] = {}
        self._by_name: dict[str, set[str]] = defaultdict(set)
        self._by_address: dict[str, set[str]] = defaultdict(set)
        self._by_phone: dict[str, set[str]] = defaultdict(set)

        for facility in facilities:
            facility_id = _facility_id(facility)
            if facility_id is None:
                continue
            if facility_id in self._by_id:
                raise SourceResponseError("시설 결합 인덱스에 중복 시설 ID가 있습니다.")
            self._by_id[facility_id] = facility

            name = _normalize_text(_facility_name(facility))
            address = _normalize_text(_facility_address(facility))
            phone = _digits(_facility_phone(facility))
            if name:
                self._by_name[name].add(facility_id)
            if address:
                self._by_address[address].add(facility_id)
            if phone:
                self._by_phone[phone].add(facility_id)

    def join(
        self,
        program: ProgramRecord,
        reviewed_matches: Mapping[str, str] | None = None,
    ) -> ProgramFacilityJoin:
        candidate_ids: set[str] = set()
        normalized_name = _normalize_text(program.facility_name)
        if normalized_name:
            candidate_ids.update(self._by_name.get(normalized_name, ()))
        else:
            # The scalar join considers two empty normalized names equal.
            candidate_ids.update(self._by_id)
        if program.road_address:
            candidate_ids.update(
                self._by_address.get(_normalize_text(program.road_address), ())
            )
        if program.phone:
            candidate_ids.update(self._by_phone.get(_digits(program.phone), ()))

        reviewed_id = (reviewed_matches or {}).get(program.program_id)
        if reviewed_id is not None and reviewed_id in self._by_id:
            candidate_ids.add(reviewed_id)

        candidates = [self._by_id[facility_id] for facility_id in sorted(candidate_ids)]
        return join_program_to_facilities(program, candidates, reviewed_matches)

    def facility(self, facility_id: str) -> dict[str, Any] | None:
        return self._by_id.get(facility_id)
