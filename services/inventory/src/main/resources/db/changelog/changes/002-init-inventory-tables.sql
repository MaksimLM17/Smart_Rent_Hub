--liquibase formatted sql

--changeset smartrenthub:002-init-inventory-tables
CREATE TABLE IF NOT EXISTS sku (
    sku_id                   uuid PRIMARY KEY,
    category_id              uuid          NOT NULL,
    brand                    text          NOT NULL,
    model                    text          NOT NULL,
    name                     text          NOT NULL,
    description              text,
    attributes               jsonb         NOT NULL DEFAULT '{}',
    replacement_value        numeric(12,2) NOT NULL,
    same_day_turnaround      boolean       NOT NULL DEFAULT false,
    expected_life_wear_days  int,
    residual_value_percent   numeric(5,2),
    repair_rate_per_wear_day numeric(10,2),
    version                  bigint        NOT NULL DEFAULT 0,
    updated_at               timestamptz   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS unit (
    unit_id                 uuid PRIMARY KEY,
    sku_id                  uuid         NOT NULL REFERENCES sku(sku_id),
    serial_number           text         NOT NULL UNIQUE,
    status                  text         NOT NULL CHECK (status IN (
        'AVAILABLE','ASSIGNED','IN_USE','RETURNED_PENDING_INSPECTION',
        'MAINTENANCE','IN_REPAIR','DECOMMISSIONED','LOST')),
    maintenance_kind        text         CHECK (maintenance_kind IN ('ROUTINE_CLEANING','TECHNICAL_SERVICE')),
    grade                   char(1)      NOT NULL DEFAULT 'A' CHECK (grade IN ('A','B')),
    assigned_order_id       uuid,
    total_wear_days         numeric(8,1) NOT NULL DEFAULT 0,
    service_wear_days       numeric(8,1) NOT NULL DEFAULT 0,
    acquisition_cost        numeric(12,2) NOT NULL,
    acquisition_date        date         NOT NULL,
    residual_value_percent  numeric(5,2) NOT NULL,
    expected_life_wear_days int          NOT NULL,
    version                 bigint       NOT NULL DEFAULT 0,
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    CHECK ((status = 'MAINTENANCE') = (maintenance_kind IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS unit_pick ON unit (sku_id, grade, status, total_wear_days);

CREATE TABLE IF NOT EXISTS unit_status_history (
    id                     bigserial PRIMARY KEY,
    unit_id                uuid NOT NULL,
    from_status            text,
    to_status              text NOT NULL,
    actor                  text NOT NULL,
    acted_under_permission text,
    reason                 text,
    at                     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS wear_ledger (
    wear_id     uuid PRIMARY KEY,
    order_id    uuid         NOT NULL,
    unit_id     uuid         NOT NULL,
    actual_days numeric(6,1) NOT NULL,
    coefficient numeric(3,1) NOT NULL,
    wear_days   numeric(6,1) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (order_id, unit_id)
);

CREATE TABLE IF NOT EXISTS kit (
    kit_id  uuid PRIMARY KEY,
    name    text   NOT NULL,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS kit_item (
    kit_id    uuid    NOT NULL REFERENCES kit(kit_id),
    sku_id    uuid    NOT NULL REFERENCES sku(sku_id),
    qty       int     NOT NULL CHECK (qty > 0),
    mandatory boolean NOT NULL DEFAULT true,
    PRIMARY KEY (kit_id, sku_id)
);

CREATE TABLE IF NOT EXISTS consumable_stock (
    sku_id  uuid PRIMARY KEY REFERENCES sku(sku_id),
    on_hand int    NOT NULL CHECK (on_hand >= 0),
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS handover_record (
    handover_id        uuid PRIMARY KEY,
    order_id           uuid        NOT NULL,
    type               text        NOT NULL CHECK (type IN ('HANDOVER','RETURN')),
    checklist          jsonb       NOT NULL,
    identity_checked   boolean     NOT NULL,
    performed_by       text        NOT NULL,
    customer_signed_at timestamptz,
    act_object_key     text,
    created_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS media (
    media_id   uuid PRIMARY KEY,
    owner_type text        NOT NULL CHECK (owner_type IN ('UNIT','HANDOVER','DEFECT','SKU')),
    owner_id   uuid        NOT NULL,
    object_key text        NOT NULL,
    sha256     text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS inspection (
    inspection_id uuid PRIMARY KEY,
    order_id      uuid        NOT NULL,
    unit_id       uuid        NOT NULL,
    result        text        NOT NULL CHECK (result IN ('OK','DEFECT')),
    started_at    timestamptz NOT NULL,
    finished_at   timestamptz,
    performed_by  text        NOT NULL,
    overdue_flag  boolean     NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS claim (
    claim_id        uuid PRIMARY KEY,
    order_id        uuid          NOT NULL,
    unit_id         uuid          NOT NULL,
    status          text          NOT NULL CHECK (status IN (
        'OPENED','ASSESSED','CLIENT_RESPONSE','RESOLVED')),
    assessment      numeric(12,2),
    franchise       numeric(12,2),
    claim_amount    numeric(12,2),
    protection_plan boolean       NOT NULL,
    resolved_by     text,
    response_due_at timestamptz,
    version         bigint        NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS maintenance_job (
    job_id      uuid PRIMARY KEY,
    unit_id     uuid          NOT NULL,
    kind        text          NOT NULL CHECK (kind IN ('ROUTINE_CLEANING','TECHNICAL_SERVICE','REPAIR')),
    verdict     text,
    cost        numeric(12,2),
    started_at  timestamptz   NOT NULL,
    finished_at timestamptz
);

CREATE TABLE IF NOT EXISTS pickup_config (
    id            int PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    work_start    time   NOT NULL,
    work_end      time   NOT NULL,
    slot_minutes  int    NOT NULL,
    slot_capacity int    NOT NULL,
    holidays      date[] NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS approval_request (
    request_id   uuid PRIMARY KEY,
    action       text        NOT NULL,
    subject_id   uuid        NOT NULL,
    requested_by text        NOT NULL,
    mode         text        NOT NULL CHECK (mode IN ('SECOND_APPROVER','LOG_ONLY')),
    status       text        NOT NULL CHECK (status IN ('PENDING','GRANTED','REJECTED','EXPIRED')),
    decided_by   text,
    comment      text,
    created_at   timestamptz NOT NULL DEFAULT now()
);
