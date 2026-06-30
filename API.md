# API — system-design-rag-enterprise

Funcionalidades e contratos dos endpoints REST.
Documentos relacionados: [ARCHITECTURE.md](ARCHITECTURE.md) · [DOMAIN.md](DOMAIN.md)

---

## Convenções Gerais

**Base URL:** `/api/v1`

**Header obrigatório em todos os endpoints protegidos:**
```
Authorization: Bearer <cognito_access_token>
```

**Formato de erro padrão (4xx / 5xx):**
```json
{
  "error": "ERROR_CODE",
  "message": "Descrição legível do problema"
}
```

**Tabela de códigos de erro:**

| HTTP | `error` | Situação |
|------|---------|---------|
| `400` | `VALIDATION_ERROR` | Campo obrigatório ausente ou inválido |
| `400` | `INVALID_SOURCE_IDS` | Sources informadas não pertencem ao notebook |
| `401` | `UNAUTHORIZED` | JWT ausente, expirado, ou inválido |
| `404` | `NOTEBOOK_NOT_FOUND` | Notebook inexistente ou de outro usuário |
| `404` | `SOURCE_NOT_FOUND` | Source inexistente ou de outro notebook/usuário |
| `404` | `CONVERSATION_NOT_FOUND` | Conversa inexistente ou de outro usuário |
| `415` | `UNSUPPORTED_FILE_TYPE` | Formato de arquivo não suportado |
| `500` | `INTERNAL_ERROR` | Erro inesperado do servidor |

**Padrão de status HTTP por operação:**

| Operação | Status |
|----------|--------|
| `GET` recurso único | `200 OK` |
| `GET` coleção | `200 OK` + array (nunca null — `[]` quando vazio) |
| `POST` criação síncrona | `201 Created` |
| `POST` que dispara processo async | `202 Accepted` |
| `PATCH` atualização | `200 OK` |
| `DELETE` | `204 No Content` |

Listagens não implementam paginação na v1. Ordenação padrão: `created_at DESC`.

---

## Notebooks

### `GET /api/v1/notebooks`
Lista todos os notebooks do usuário autenticado.

**Response `200 OK`:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Projeto Alpha",
    "description": "Documentos do projeto Alpha",
    "createdAt": "2024-01-15T10:30:00Z",
    "updatedAt": "2024-01-15T10:30:00Z"
  }
]
```

---

### `POST /api/v1/notebooks`
Cria um novo notebook.

**Request body:**
```json
{
  "name": "Projeto Alpha",
  "description": "Documentos do projeto Alpha"
}
```

| Campo | Tipo | Obrigatório | Restrições |
|-------|------|-------------|-----------|
| `name` | string | sim | 1–256 caracteres |
| `description` | string | não | máx. 2048 caracteres |

**Response `201 Created`:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Projeto Alpha",
  "description": "Documentos do projeto Alpha",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

**Erros:** `400` campo `name` inválido · `401` JWT inválido

---

### `GET /api/v1/notebooks/{notebookId}`
Retorna um notebook com suas sources.

**Response `200 OK`:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Projeto Alpha",
  "description": "Documentos do projeto Alpha",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "sources": [
    {
      "id": "661f9511-f30c-52e5-b827-557766551111",
      "name": "contrato.pdf",
      "type": "FILE",
      "status": "READY",
      "createdAt": "2024-01-15T11:00:00Z"
    }
  ]
}
```

**Erros:** `404` notebook não existe ou é de outro usuário

---

### `PATCH /api/v1/notebooks/{notebookId}`
Atualiza nome e/ou descrição. Apenas os campos enviados são modificados.

**Request body:**
```json
{
  "name": "Novo Nome",
  "description": "Nova descrição"
}
```

**Response `200 OK`:** objeto notebook atualizado (mesmo formato do GET)

**Erros:** `404` notebook não existe ou é de outro usuário

---

### `DELETE /api/v1/notebooks/{notebookId}`
Deleta o notebook e todos os dados associados em cascata (sources, chunks, conversas, mensagens, arquivos S3).

**Response `204 No Content`**

**Erros:** `404` notebook não existe ou é de outro usuário

---

## Sources

### `GET /api/v1/notebooks/{notebookId}/sources`
Lista todas as sources do notebook com status de processamento.

**Response `200 OK`:**
```json
[
  {
    "id": "661f9511-f30c-52e5-b827-557766551111",
    "name": "contrato.pdf",
    "type": "FILE",
    "status": "READY",
    "errorMessage": null,
    "createdAt": "2024-01-15T11:00:00Z"
  },
  {
    "id": "772a0622-g41d-63f6-c938-668877662222",
    "name": "https://docs.example.com/api",
    "type": "URL",
    "status": "PROCESSING",
    "errorMessage": null,
    "createdAt": "2024-01-15T11:05:00Z"
  }
]
```

---

### `POST /api/v1/notebooks/{notebookId}/sources`
Adiciona uma source ao notebook. Aceita **arquivo** (multipart) ou **URL** (JSON).
O processamento acontece de forma assíncrona — retorna `202 Accepted` imediatamente.

**Opção A — Arquivo (`multipart/form-data`):**

| Campo | Tipo | Obrigatório | Descrição |
|-------|------|-------------|-----------|
| `file` | binary | sim | PDF, DOCX, ou Markdown |
| `name` | string | não | Nome exibido; padrão: nome do arquivo |

**Opção B — URL (`application/json`):**
```json
{
  "type": "URL",
  "url": "https://docs.example.com/api",
  "name": "Docs da API"
}
```

**Response `202 Accepted`:**
```json
{
  "id": "661f9511-f30c-52e5-b827-557766551111",
  "name": "contrato.pdf",
  "type": "FILE",
  "status": "PENDING",
  "createdAt": "2024-01-15T11:00:00Z"
}
```

**Erros:** `400` parâmetros inválidos · `404` notebook não existe · `415` formato não suportado

---

### `GET /api/v1/notebooks/{notebookId}/sources/{sourceId}`
Retorna detalhes e status de processamento de uma source. Use para polling após upload.

**Response `200 OK`:**
```json
{
  "id": "661f9511-f30c-52e5-b827-557766551111",
  "name": "contrato.pdf",
  "type": "FILE",
  "status": "FAILED",
  "errorMessage": "Arquivo corrompido: não foi possível extrair texto",
  "createdAt": "2024-01-15T11:00:00Z"
}
```

**Erros:** `404` source não existe ou pertence a outro notebook/usuário

---

### `DELETE /api/v1/notebooks/{notebookId}/sources/{sourceId}`
Deleta a source, seus chunks do pgvector, e o arquivo do S3.

**Response `204 No Content`**

**Erros:** `404` source não existe ou pertence a outro notebook/usuário

---

## Conversas e Chat

### `GET /api/v1/notebooks/{notebookId}/conversations`
Lista conversas do notebook, ordenadas por `createdAt` decrescente.

**Response `200 OK`:**
```json
[
  {
    "id": "883b1733-h52e-74g7-d049-779988773333",
    "notebookId": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2024-01-15T12:00:00Z",
    "preview": "Qual o prazo de entrega mencionado no contrat..."
  }
]
```

---

### `POST /api/v1/notebooks/{notebookId}/conversations`
Cria uma nova conversa, com seleção opcional de quais sources ficam ativas para o RAG.

**Request body:**
```json
{
  "activeSourceIds": [
    "661f9511-f30c-52e5-b827-557766551111",
    "772a0622-g41d-63f6-c938-668877662222"
  ]
}
```

> Se `activeSourceIds` for omitido ou `[]`, todas as sources com `status = READY` do notebook ficam ativas.

**Response `201 Created`:**
```json
{
  "id": "883b1733-h52e-74g7-d049-779988773333",
  "notebookId": "550e8400-e29b-41d4-a716-446655440000",
  "activeSourceIds": [
    "661f9511-f30c-52e5-b827-557766551111",
    "772a0622-g41d-63f6-c938-668877662222"
  ],
  "createdAt": "2024-01-15T12:00:00Z"
}
```

**Erros:** `404` notebook não existe · `400` algum `sourceId` não pertence ao notebook

---

### `GET /api/v1/conversations/{conversationId}/messages`
Retorna o histórico completo de mensagens em ordem cronológica.

**Response `200 OK`:**
```json
[
  {
    "id": "994c2844-i63f-85h8-e150-880099884444",
    "role": "user",
    "content": "Qual o prazo de entrega mencionado no contrato?",
    "createdAt": "2024-01-15T12:01:00Z"
  },
  {
    "id": "aa5d3955-j74g-96i9-f261-991100995555",
    "role": "assistant",
    "content": "De acordo com a cláusula 5.2 do contrato, o prazo é de 30 dias úteis após a assinatura.",
    "createdAt": "2024-01-15T12:01:05Z"
  }
]
```

**Erros:** `404` conversa não existe ou pertence a outro usuário

---

### `POST /api/v1/conversations/{conversationId}/messages`
Envia uma mensagem e recebe a resposta do LLM como **SSE stream**.

**Request body:**
```json
{
  "content": "Qual o prazo de entrega mencionado no contrato?"
}
```

**Headers da response:**
```
Content-Type: text/event-stream
Cache-Control: no-cache
X-Accel-Buffering: no
```

**Response `200 OK` — formato SSE (token a token):**
```
data: {"token": "De "}

data: {"token": "acordo "}

data: {"token": "com "}

data: {"token": "a "}

data: {"token": "cláusula "}

data: {"token": "5.2 "}

data: {"done": true, "messageId": "aa5d3955-j74g-96i9-f261-991100995555"}

```

> Cada evento SSE é uma linha `data: <json>\n\n`.
> O evento final `{"done": true}` inclui o `messageId` da mensagem persistida.
> Erros de stream (LLM timeout, etc.) são enviados como `data: {"error": "STREAM_ERROR"}` antes de fechar.

**Erros (retornados antes de iniciar o stream):**
- `400 Bad Request` — `content` ausente
- `404 Not Found` — conversa não existe ou pertence a outro usuário
