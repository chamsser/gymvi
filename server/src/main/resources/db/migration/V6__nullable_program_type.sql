-- The official program CSV has rows without a program type. Keep them unknown.
ALTER TABLE facility_program
    ALTER COLUMN program_type_name DROP NOT NULL;
