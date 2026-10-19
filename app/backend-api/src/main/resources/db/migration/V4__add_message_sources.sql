-- Snapshot das sources/chunks usados pelo RAG em cada resposta do assistente:
-- [{"sourceId": "...", "name": "...", "chunks": [{"chunkIndex": 0, "snippet": "..."}]}]
ALTER TABLE tb_conversation_messages
    ADD COLUMN sources JSONB NOT NULL DEFAULT '[]'::jsonb;
