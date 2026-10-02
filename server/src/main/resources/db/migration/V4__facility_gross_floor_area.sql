ALTER TABLE facility
    ADD COLUMN gross_floor_area_square_meters double precision
    CHECK (
        gross_floor_area_square_meters IS NULL OR
        gross_floor_area_square_meters > 0 AND
        gross_floor_area_square_meters <= 10000000
    );

UPDATE facility AS f
SET gross_floor_area_square_meters =
    btrim(sr.raw_record ->> 'faci_gfa')::double precision
FROM source_record AS sr
WHERE sr.id = f.source_record_id
  AND btrim(sr.raw_record ->> 'faci_gfa') ~ '^[0-9]+([.][0-9]+)?$'
  AND btrim(sr.raw_record ->> 'faci_gfa')::numeric > 0
  AND btrim(sr.raw_record ->> 'faci_gfa')::numeric <= 10000000;

UPDATE facility AS f
SET gross_floor_area_square_meters =
    btrim(sr.raw_record ->> 'faciGfa')::double precision
FROM source_record AS sr
WHERE sr.id = f.source_record_id
  AND f.gross_floor_area_square_meters IS NULL
  AND btrim(sr.raw_record ->> 'faciGfa') ~ '^[0-9]+([.][0-9]+)?$'
  AND btrim(sr.raw_record ->> 'faciGfa')::numeric > 0
  AND btrim(sr.raw_record ->> 'faciGfa')::numeric <= 10000000;
