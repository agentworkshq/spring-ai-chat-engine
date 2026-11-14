CREATE TABLE conversations (
    id            UUID PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    system_prompt TEXT,
    model         VARCHAR(255),
    temperature   DOUBLE PRECISION,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE messages (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_messages_conversation_created
    ON messages (conversation_id, created_at);
