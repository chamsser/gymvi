CREATE TABLE enrichment_dataset_version (
    id text PRIMARY KEY,
    dataset_kind text NOT NULL CHECK (dataset_kind IN ('PROGRAM', 'SAFETY', 'CONTENT')),
    source_dataset_id text NOT NULL,
    schema_version text NOT NULL,
    raw_sha256 char(64) NOT NULL CHECK (raw_sha256 ~ '^[0-9a-f]{64}$'),
    as_of timestamptz NOT NULL,
    status text NOT NULL CHECK (status IN ('STAGED', 'ACTIVE', 'SUPERSEDED', 'REJECTED')),
    license_evidence jsonb,
    activated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status = 'ACTIVE' AND activated_at IS NOT NULL) OR status <> 'ACTIVE')
);

CREATE UNIQUE INDEX enrichment_dataset_version_one_active_per_kind
    ON enrichment_dataset_version (dataset_kind)
    WHERE status = 'ACTIVE';

CREATE TABLE enrichment_source_record (
    id text PRIMARY KEY,
    enrichment_dataset_version_id text NOT NULL
        REFERENCES enrichment_dataset_version(id) ON DELETE CASCADE,
    provider_record_id text NOT NULL,
    source_url text NOT NULL,
    catalog_url text NOT NULL,
    raw_record jsonb NOT NULL,
    as_of timestamptz NOT NULL,
    UNIQUE (enrichment_dataset_version_id, provider_record_id)
);

CREATE TABLE facility_program (
    program_id text NOT NULL,
    enrichment_dataset_version_id text NOT NULL
        REFERENCES enrichment_dataset_version(id) ON DELETE CASCADE,
    facility_dataset_version_id text NOT NULL,
    facility_id text NOT NULL,
    source_record_id text NOT NULL
        REFERENCES enrichment_source_record(id) ON DELETE RESTRICT,
    join_state text NOT NULL CHECK (join_state IN ('EXACT', 'REVIEWED', 'CANDIDATE', 'REJECTED')),
    join_reason_codes text[] NOT NULL DEFAULT '{}',
    program_type_name text NOT NULL,
    program_name text NOT NULL,
    target_name text,
    begin_date date,
    end_date date,
    weekdays text[] NOT NULL DEFAULT '{}',
    source_time_value text,
    recruitment_count integer CHECK (recruitment_count IS NULL OR recruitment_count >= 0),
    price_won integer CHECK (price_won IS NULL OR price_won >= 0),
    price_type_name text,
    homepage_url text,
    operation_state text NOT NULL DEFAULT 'UNKNOWN'
        CHECK (operation_state IN ('OPEN', 'CLOSED', 'UNKNOWN')),
    operation_reason text NOT NULL DEFAULT 'NO_CURRENT_OPERATION_EVIDENCE',
    application_state text NOT NULL DEFAULT 'UNKNOWN'
        CHECK (application_state IN ('AVAILABLE', 'CLOSED', 'UNKNOWN')),
    application_reason text NOT NULL DEFAULT 'NO_CURRENT_APPLICATION_EVIDENCE',
    PRIMARY KEY (enrichment_dataset_version_id, program_id),
    FOREIGN KEY (facility_dataset_version_id, facility_id)
        REFERENCES facility(dataset_version_id, facility_id) ON DELETE RESTRICT
);

CREATE INDEX facility_program_facility_lookup
    ON facility_program (facility_dataset_version_id, facility_id, join_state);

CREATE TABLE facility_safety_evidence (
    safety_record_id text NOT NULL,
    enrichment_dataset_version_id text NOT NULL
        REFERENCES enrichment_dataset_version(id) ON DELETE CASCADE,
    facility_dataset_version_id text NOT NULL,
    facility_id text NOT NULL,
    source_record_id text NOT NULL
        REFERENCES enrichment_source_record(id) ON DELETE RESTRICT,
    join_state text NOT NULL CHECK (join_state IN ('EXACT', 'REVIEWED', 'CANDIDATE', 'REJECTED')),
    join_reason_codes text[] NOT NULL DEFAULT '{}',
    grade_code text,
    grade_name text,
    inspection_date date,
    publication_date date,
    base_date date,
    PRIMARY KEY (enrichment_dataset_version_id, safety_record_id),
    FOREIGN KEY (facility_dataset_version_id, facility_id)
        REFERENCES facility(dataset_version_id, facility_id) ON DELETE RESTRICT
);

CREATE INDEX facility_safety_evidence_facility_lookup
    ON facility_safety_evidence (facility_dataset_version_id, facility_id, join_state);
