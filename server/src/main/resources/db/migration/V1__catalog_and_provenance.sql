CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE dataset_version (
    id text PRIMARY KEY,
    source_dataset_id text NOT NULL,
    schema_version text NOT NULL,
    raw_sha256 char(64) NOT NULL CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    as_of timestamptz NOT NULL,
    status text NOT NULL CHECK (status IN ('STAGED', 'ACTIVE', 'SUPERSEDED', 'REJECTED')),
    activated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status = 'ACTIVE' AND activated_at IS NOT NULL) OR status <> 'ACTIVE')
);

CREATE UNIQUE INDEX dataset_version_one_active
    ON dataset_version ((status))
    WHERE status = 'ACTIVE';

CREATE TABLE source_record (
    id text PRIMARY KEY,
    dataset_version_id text NOT NULL REFERENCES dataset_version(id) ON DELETE CASCADE,
    source_dataset_id text NOT NULL,
    provider_namespace text NOT NULL,
    provider_record_id text NOT NULL,
    source_url text NOT NULL,
    catalog_url text NOT NULL,
    raw_record jsonb NOT NULL,
    as_of timestamptz NOT NULL,
    UNIQUE (dataset_version_id, provider_namespace, provider_record_id)
);

CREATE TABLE facility (
    facility_id text NOT NULL,
    dataset_version_id text NOT NULL REFERENCES dataset_version(id) ON DELETE CASCADE,
    source_record_id text NOT NULL REFERENCES source_record(id) ON DELETE RESTRICT,
    name text NOT NULL,
    search_name text NOT NULL,
    facility_class_code text,
    facility_class_name text,
    facility_type_code text,
    facility_type_name text,
    road_address text,
    lot_address text,
    sido_code text NOT NULL,
    sigungu_code text NOT NULL,
    location geometry(Point, 4326) NOT NULL,
    operation_state text NOT NULL CHECK (operation_state IN ('OPEN', 'CLOSED', 'UNKNOWN')),
    operation_reason text NOT NULL,
    PRIMARY KEY (dataset_version_id, facility_id)
);

CREATE INDEX facility_location_gist ON facility USING gist (location);
CREATE INDEX facility_dataset_id_order ON facility (dataset_version_id, facility_id);

CREATE TABLE evidence_value (
    id text PRIMARY KEY,
    dataset_version_id text NOT NULL REFERENCES dataset_version(id) ON DELETE CASCADE,
    facility_id text NOT NULL,
    source_record_id text NOT NULL REFERENCES source_record(id) ON DELETE RESTRICT,
    field_name text NOT NULL,
    source_field text NOT NULL,
    value_json jsonb NOT NULL,
    observed_at timestamptz NOT NULL,
    expires_at timestamptz,
    confidence text NOT NULL CHECK (confidence IN ('SOURCE', 'DERIVED', 'REVIEWED')),
    FOREIGN KEY (dataset_version_id, facility_id)
        REFERENCES facility(dataset_version_id, facility_id) ON DELETE CASCADE,
    UNIQUE (dataset_version_id, facility_id, field_name)
);
