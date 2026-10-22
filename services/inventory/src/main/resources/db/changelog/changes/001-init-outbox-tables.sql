--liquibase formatted sql

--changeset smartrenthub:001-init-outbox-tables
CREATE TABLE IF NOT EXISTS outbox (
    id             uuid PRIMARY KEY,
    aggregate_type text        NOT NULL,
    aggregate_id   text        NOT NULL,
    event_type     text        NOT NULL,
    schema_version int         NOT NULL DEFAULT 1,
    correlation_id text,
    traceparent    text,
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    payload        jsonb       NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS processed_event (
    consumer     text        NOT NULL,
    event_id     uuid        NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
