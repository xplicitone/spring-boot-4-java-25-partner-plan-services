CREATE TABLE partner_channel_config (
    partner_id      VARCHAR(64) PRIMARY KEY,
    channel_type    VARCHAR(16)  NOT NULL,
    sqs_queue_url   VARCHAR(512),
    legacy_endpoint VARCHAR(512),
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE delivery (
    id              UUID PRIMARY KEY,
    partner_id      VARCHAR(64)  NOT NULL,
    application_id  VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    channel         VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    dispatched_at   TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(1024),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_delivery_event UNIQUE (partner_id, application_id, event_type)
);

CREATE INDEX idx_delivery_partner_status ON delivery (partner_id, status);
CREATE INDEX idx_delivery_status_created ON delivery (status, created_at);
