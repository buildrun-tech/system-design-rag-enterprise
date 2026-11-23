-- Outbox transacional: eventos de saída gravados na mesma tx do dado e publicados por relay (polling + SKIP LOCKED).
-- payload: {"sourceId": "...", "requestId": "...", "userId": "..."}
CREATE TABLE tb_outbox (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(64)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_tb_outbox_pending ON tb_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_tb_outbox_sent ON tb_outbox (sent_at) WHERE status = 'SENT';
