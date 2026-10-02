ALTER TABLE facility ADD COLUMN phone text;

UPDATE facility AS f
SET phone = CASE
    WHEN btrim(sr.raw_record ->> 'faciTel') ~ '^[0-9+() -]{3,40}$'
        THEN btrim(sr.raw_record ->> 'faciTel')
    ELSE NULL
END
FROM source_record AS sr
WHERE sr.id = f.source_record_id;
