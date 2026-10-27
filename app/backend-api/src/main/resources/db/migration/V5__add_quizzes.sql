-- Job assíncrono de geração de quiz por conversation.
-- result: [{"question": "...", "options": ["..."], "correctIndex": 0, "explanation": "..."}]
CREATE TABLE tb_quizzes (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES tb_conversations(id) ON DELETE CASCADE,
    status          VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    result          JSONB,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tb_quizzes_conversation_id ON tb_quizzes (conversation_id);
