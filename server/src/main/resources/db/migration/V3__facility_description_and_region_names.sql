ALTER TABLE facility ADD COLUMN description text;
ALTER TABLE facility ADD COLUMN sido_name text;
ALTER TABLE facility ADD COLUMN sigungu_name text;

ALTER TABLE facility ALTER COLUMN sido_code DROP NOT NULL;
ALTER TABLE facility ALTER COLUMN sigungu_code DROP NOT NULL;
