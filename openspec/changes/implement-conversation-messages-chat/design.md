## Context

`GET /conversations/{id}/messages` já existe (`ConversationMessageController` → `ConversationMessageService` → `ConversationMessageRepository`), com blindagem IDOR via `findByIdAndNotebook_Owner_Id` / join até `owner_id`. `pom.xml` já traz `spring-ai-starter-model-openai` (Spring AI 2.0.0) e `application.yml` já expõe `spring.ai.openai.base-url/api-key/chat.options.model` por env var — nenhum código de config novo pro provider, só as env vars precisam apontar pro OpenRouter em runtime.

App é **Spring MVC servlet** (`spring-boot-starter-webmvc`), não WebFlux. `ChatClient.stream()` retorna `Flux<String>` (Reactor está no classpath via Spring AI) — o bridging pra SSE em ambiente servlet precisa de `SseEmitter` com dispatch assíncrono, já que a thread de request não pode ficar bloqueada esperando o Flux completar.

RAG (similarity search em `source_chunks`/pgvector) ainda não está implementado neste ponto do projeto — capability `rag-retrieval` existe como spec mas sem implementação. A spec `chat` atual exige RAG em toda mensagem; este change reduz temporariamente esse requirement pra permitir chat funcional sem bloquear em RAG.

## Goals / Non-Goals

**Goals:**
- `POST /conversations/{id}/messages` funcional, streaming SSE token-a-token, conforme já documentado em `API.md`
- Histórico de conversa (últimas 10 mensagens) incluído no prompt, mantendo a regra de domínio existente
- Persistência correta: mensagem do usuário imediata, mensagem do assistente só após stream completo
- Erro de LLM (timeout, falha do provider) não deixa mensagem parcial/corrompida persistida

**Non-Goals:**
- RAG: nenhuma similarity search no pgvector, nenhum chunk de source no prompt
- Tools / function calling
- Multi-turn com system prompt customizável por notebook (system message fixo por ora)
- Retry automático de chamada ao LLM em caso de falha

## Decisions

**1. `ChatClient` como bean dedicado em `@Configuration`, não injeção direta do `Builder` no service**
Mantém o service livre de lógica de configuração do provider; se no futuro precisar de `ChatClient` diferente por capability (ex: um com RAG advisor, outro sem), já tem ponto de extensão. Alternativa (`ChatClient.Builder` direto no service) foi descartada — decisão já tomada com o usuário na fase de exploração.

**2. SSE via `SseEmitter` + subscribe manual do `Flux`, não WebFlux/`Flux<ServerSentEvent>` como retorno do controller**
App inteiro é MVC; migrar só este endpoint pra WebFlux introduziria dois stacks reativos coexistindo (custo alto pra um endpoint). `SseEmitter` é o mecanismo padrão MVC pra SSE; subscribe do `Flux` roda em `Schedulers.boundedElastic()` pra não bloquear a thread que devolveu o `SseEmitter`.

```
Controller retorna SseEmitter imediatamente (fora da thread de servlet)
        │
        ▼
Flux<String> (chatClient.stream()) .subscribe(
    onNext  → emitter.send({"token": chunk}) + acumula StringBuilder
    onError → emitter.send({"error": "STREAM_ERROR"}) + emitter.completeWithError(e)
    onComplete → persiste ConversationMessage(assistant, acumulado)
                 emitter.send({"done": true, "messageId": ...})
                 emitter.complete()
)
```

**3. Histórico: busca últimas 10 mensagens ANTES de persistir a nova mensagem do usuário, monta lista de `Message` do Spring AI, então persiste a mensagem do usuário**
Ordem importa: se persistir primeiro e buscar depois, a query de "últimas 10" precisa excluir a mensagem recém-criada ou duplicar. Mais simples: buscar histórico existente (0 a 10 mensagens antigas), montar `SystemMessage + histórico + UserMessage(novo conteúdo)`, chamar o LLM, e só então persistir a mensagem do usuário (pode ser em paralelo à leitura do histórico, mas antes da chamada ao LLM — sem risco de a nova mensagem "se ver" no próprio histórico).

**4. `MessageRole` (enum `user`/`assistant`) mapeado manualmente pra `UserMessage`/`AssistantMessage` do Spring AI**
Sem dependência de conversão automática — são dois enums de domínios diferentes (persistência vs. Spring AI `Message`), mapeamento explícito e óbvio no service.

**5. System message fixo, hardcoded no service (não vem de config nem de notebook)**
Não existe requirement de customização por notebook ainda. Texto simples tipo "Você é um assistente que ajuda o usuário a entender os documentos do notebook." — sem menção a RAG/contexto já que não tem.

## Risks / Trade-offs

- **[Risco] Bridging `Flux` → `SseEmitter` manual é código sem precedente no projeto, propenso a vazar threads/conexões se mal feito** → Mitigação: usar `emitter.onCompletion()`/`onTimeout()`/`onError()` callbacks pra garantir cleanup; timeout do `SseEmitter` configurado explicitamente (ex: 60s) em vez do default.
- **[Risco] Cobertura de mutation testing (80%, pitest) em código assíncrono de streaming é difícil de exercitar em teste unitário puro** → Mitigação: testar `ChatModel`/`ChatClient` mockado com resposta síncrona simulando o Flux (`Flux.just(...)`) via `StepVerifier` ou teste MVC com `MockMvc` + `MockMvcResultMatchers.request().asyncStarted()`; se algum branch ficar STUCK, documentar exclusão justificada no `pom.xml` conforme convenção já usada no projeto.
- **[Risco] Sem RAG, resposta do assistente pode alucinar sobre conteúdo de sources que o usuário espera que estejam "no contexto"** → Mitigação: aceito conscientemente pro escopo deste change (documentado na proposal); RAG entra em change futuro que restaura o requirement completo da spec `chat`.
- **[Trade-off] Sem retry em falha do LLM** → usuário reenvia manualmente a mensagem; aceitável pro escopo atual, mensagem do usuário já foi persistida então não se perde.

## Migration Plan

Endpoint novo, sem dado existente pra migrar. Deploy normal: mergear, garantir `SPRING_AI_OPENAI_BASE_URL`/`API_KEY`/`CHAT_OPTIONS_MODEL` configurados no ambiente (OpenRouter) antes do rollout. Rollback: reverter o deploy — nenhuma mudança de schema envolvida.

## Open Questions

- Nenhuma pendente — decisões de streaming, histórico e config já fechadas com o usuário na fase de exploração.
