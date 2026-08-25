# Spring AI Framework

Spring AI is a framework for integrating artificial intelligence models and AI-related infrastructure into Spring applications.

The framework provides portable abstractions over model providers, vector databases, tool calling, retrieval-augmented generation, conversation memory, structured output, and other common generative AI patterns.

Spring AI follows familiar Spring design principles:

- Portable interfaces with provider-specific implementations
- Dependency injection and Spring Boot auto-configuration
- Fluent client APIs
- Modular integrations
- Observability
- Testable abstractions
- Provider portability

---

## 1. Architecture

At a high level, Spring AI applications are composed of several layers.

```text
Application
    |
    v
ChatClient
    |
    +---- Advisors
    |       |
    |       +---- Memory
    |       +---- RAG
    |       +---- Logging
    |       +---- Custom Advisors
    |
    v
ChatModel
    |
    +---- OpenAI
    +---- Anthropic
    +---- Ollama
    +---- Google
    +---- Amazon Bedrock
    +---- Azure
    +---- Other providers
```

Additional framework components include:

```text
EmbeddingModel
      |
      v
  VectorStore
      |
      v
Documents / Semantic Search / RAG

Tool Definitions
      |
      v
   AI Model
      |
      v
Tool Execution

DocumentReader
      |
DocumentTransformer
      |
DocumentWriter
      |
  VectorStore
```

---

# 2. Model API

Spring AI defines portable APIs for interacting with different categories of AI models.

Major model abstractions include:

| Model API | Purpose |
|---|---|
| `ChatModel` | Text and conversational generation |
| `EmbeddingModel` | Generate vector embeddings |
| `ImageModel` | Generate images |
| Audio models | Speech transcription and generation |
| Moderation models | Content moderation |

Provider implementations expose these interfaces while also allowing access to provider-specific options.

This makes it possible for application code to depend primarily on Spring AI abstractions rather than directly on a vendor SDK.

---

# 3. Chat Model

`ChatModel` is the primary low-level abstraction for interacting with conversational language models.

```java
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

ChatResponse response = chatModel.call(
    new Prompt("Explain dependency injection")
);
```

The returned `ChatResponse` contains model generations and response metadata.

Conceptually:

```text
Prompt
   |
   v
ChatModel
   |
   v
ChatResponse
```

Different model providers implement the same core contract.

---

# 4. Prompt API

A prompt represents the input sent to an AI model.

A prompt can contain:

- User messages
- System messages
- Additional conversational messages
- Model options
- Tool definitions
- Runtime context

Example:

```java
Prompt prompt = new Prompt(
    "Explain Spring dependency injection."
);

ChatResponse response = chatModel.call(prompt);
```

Prompts can also contain multiple messages.

```java
Prompt prompt = new Prompt(
    List.of(
        new SystemMessage(
            "You are a Java framework expert."
        ),
        new UserMessage(
            "Explain the BeanFactory."
        )
    )
);
```

---

# 5. Messages

Spring AI represents conversational input using message objects.

Common message types include:

```text
Message
├── SystemMessage
├── UserMessage
├── AssistantMessage
└── ToolResponseMessage
```

## System Message

A system message provides instructions controlling model behavior.

```java
new SystemMessage("""
    You are a technical documentation assistant.
    Answer using concise Java examples.
    """);
```

## User Message

A user message contains input originating from the user.

```java
new UserMessage(
    "What is ApplicationContext?"
);
```

Messages provide a portable representation that Spring AI converts into the format required by the selected model provider.

---

# 6. ChatClient

`ChatClient` is the higher-level fluent API for interacting with chat models.

It provides an API similar in spirit to other fluent Spring clients.

```java
String answer = chatClient
    .prompt()
    .user("What is Spring AI?")
    .call()
    .content();
```

A system instruction can be added independently.

```java
String answer = chatClient
    .prompt()
    .system("""
        You are a Java technical writer.
        Return concise framework documentation.
        """)
    .user("Explain ChatModel")
    .call()
    .content();
```

The client is responsible for constructing the underlying prompt and invoking the configured model.

---

# 7. Prompt Templates

Prompts frequently contain dynamic values.

Instead of manually concatenating strings, Spring AI supports prompt templates.

```text
Explain {technology} to a {audience}.
```

Values can be supplied at runtime:

```java
String response = chatClient
    .prompt()
    .user(u -> u
        .text("Explain {technology} to a {audience}.")
        .param("technology", "Spring AI")
        .param("audience", "Spring developer"))
    .call()
    .content();
```

Templates are useful for creating reusable prompt definitions while keeping variable data separate from instructions.

---

# 8. Model Options

Model behavior can be configured using model options.

Typical settings include:

```text
model
temperature
max tokens
top-p
stop sequences
provider-specific parameters
```

Conceptually:

```java
Prompt prompt = new Prompt(
    "Generate three product names.",
    modelOptions
);
```

Spring AI provides common option abstractions while provider implementations can expose additional settings.

---

# 9. Structured Output

Language models normally produce text.

Application code often requires typed Java objects instead.

Spring AI provides structured-output support for converting model responses into Java types.

For example:

```java
record Framework(
    String name,
    String language,
    String description
) {}
```

A response can be mapped directly to the type:

```java
Framework framework = chatClient
    .prompt()
    .user("Describe the Spring Framework")
    .call()
    .entity(Framework.class);
```

This allows AI output to participate more naturally in typed application workflows.

Collections and parameterized types can also be represented when more complex output structures are required.

---

# 10. Advisors

Advisors provide a mechanism for intercepting and modifying AI interactions.

They can contribute behavior before or after a model request.

```text
User Request
     |
     v
 Advisor 1
     |
 Advisor 2
     |
 Advisor 3
     |
     v
 ChatModel
     |
     v
 Response
```

Advisors can implement reusable AI patterns such as:

- Conversation memory
- Retrieval-augmented generation
- Request logging
- Prompt augmentation
- Policy enforcement
- Context injection
- Response processing

Example:

```java
String response = chatClient
    .prompt()
    .advisors(myAdvisor)
    .user("Explain Spring AI")
    .call()
    .content();
```

Advisors keep cross-cutting AI behavior separate from business logic.

---

# 11. Chat Memory

Large language models do not inherently maintain application-level conversation state between independent requests.

Spring AI provides chat-memory abstractions for storing conversational history.

Conceptually:

```text
Request 1
User: Hello
AI: Hello!

        |
        v

ChatMemory
[
    UserMessage,
    AssistantMessage
]

        |
        v

Request 2
User: What did I just say?
```

Memory implementations can be combined with Advisors so previous messages are automatically included in subsequent model interactions.

A conversation identifier can be used to keep multiple conversations isolated.

```text
conversation-a -> messages [...]
conversation-b -> messages [...]
conversation-c -> messages [...]
```

---

# 12. Embeddings

Embeddings convert content into numerical vector representations.

Conceptually:

```text
"Spring AI framework"
        |
        v
EmbeddingModel
        |
        v
[0.124, -0.531, 0.882, ...]
```

The main abstraction is:

```java
EmbeddingModel
```

Example:

```java
float[] vector = embeddingModel.embed(
    "Spring AI provides portable AI abstractions."
);
```

Embeddings allow applications to compare content by semantic similarity rather than exact text matching.

Common use cases include:

- Semantic search
- Document retrieval
- Classification
- Recommendations
- Retrieval-augmented generation

---

# 13. Documents

Spring AI uses the `Document` abstraction to represent content used by data-processing and retrieval pipelines.

A document typically contains:

```text
Document
├── id
├── text
├── metadata
└── optional media
```

Example:

```java
Document document = new Document(
    "Spring AI provides abstractions for AI models.",
    Map.of(
        "category", "framework",
        "source", "documentation"
    )
);
```

Metadata can later be used for filtering and retrieval.

---

# 14. VectorStore

`VectorStore` provides a portable abstraction over vector databases.

```java
vectorStore.add(documents);
```

Documents can then be retrieved using semantic similarity.

```java
List<Document> results =
    vectorStore.similaritySearch(
        SearchRequest.builder()
            .query("AI framework for Java")
            .topK(5)
            .build()
    );
```

Conceptually:

```text
Document
   |
   v
EmbeddingModel
   |
   v
Vector
   |
   v
VectorStore
```

At query time:

```text
User Query
    |
    v
Embedding
    |
    v
Similarity Search
    |
    v
Relevant Documents
```

Spring AI supports multiple vector-store implementations behind this API.

---

# 15. Metadata Filtering

Vector searches can be constrained using document metadata.

For example, documents might contain:

```json
{
  "product": "spring-ai",
  "language": "java",
  "version": "2"
}
```

A retrieval operation can then combine semantic similarity with metadata filtering.

```text
similar content
AND
product == "spring-ai"
```

This is useful when a single vector database contains documents belonging to different:

- Users
- Tenants
- Products
- Versions
- Categories
- Security domains

---

# 16. Retrieval-Augmented Generation

Retrieval-Augmented Generation, or RAG, supplies external information to a language model before generating an answer.

A typical RAG pipeline is:

```text
User Question
      |
      v
Embedding Model
      |
      v
Vector Store
      |
      v
Relevant Documents
      |
      v
Prompt Augmentation
      |
      v
Chat Model
      |
      v
Answer
```

This makes it possible to answer questions using data that is not contained in the model's training data.

Typical sources include:

- Product documentation
- Internal knowledge bases
- Support articles
- Policies
- Source code
- Database records
- PDFs
- Web content

Spring AI provides RAG integrations through the Advisor API as well as lower-level retrieval components for custom pipelines.

---

# 17. RAG Advisors

A retrieval advisor can automatically perform document retrieval before invoking the model.

Conceptually:

```java
String answer = chatClient
    .prompt()
    .advisors(retrievalAdvisor)
    .user(question)
    .call()
    .content();
```

The advisor performs approximately the following workflow:

```text
question
   |
   +--> retrieve relevant documents
   |
   +--> add documents to context
   |
   +--> construct augmented prompt
   |
   +--> invoke model
   |
   `--> return answer
```

Advanced RAG pipelines can additionally perform:

- Query rewriting
- Query expansion
- Document filtering
- Document ranking
- Re-ranking
- Context compression
- Post-retrieval processing

---

# 18. Tool Calling

Tool calling allows a model to request that application code execute a function.

A tool can expose application capabilities such as:

```text
getWeather()
findCustomer()
lookupOrder()
searchProducts()
createTicket()
calculatePrice()
```

Example:

```java
class WeatherTools {

    @Tool(description = "Get the current weather for a city")
    Weather weather(String city) {
        return weatherService.getWeather(city);
    }
}
```

The tool can be made available during a model interaction.

```java
String answer = chatClient
    .prompt()
    .user("What is the weather in Paris?")
    .tools(new WeatherTools())
    .call()
    .content();
```

The logical flow is:

```text
User
 |
 v
Model
 |
 | requests tool
 v
Spring AI
 |
 v
Application Tool
 |
 v
Tool Result
 |
 v
Model
 |
 v
Final Response
```

The model chooses whether a registered tool should be invoked. The application remains responsible for the actual tool implementation and its side effects.

---

# 19. ETL Pipeline

Spring AI provides document-processing abstractions for preparing data for retrieval systems.

The core ETL model consists of:

```text
DocumentReader
      |
      v
DocumentTransformer
      |
      v
DocumentWriter
```

## DocumentReader

Reads data from an external source and produces documents.

Possible sources include:

```text
PDF
HTML
Markdown
JSON
Text
Web pages
Other document formats
```

## DocumentTransformer

Transforms documents before storage.

Common transformations include:

```text
Text splitting
Metadata enrichment
Content filtering
Token-based chunking
```

## DocumentWriter

Writes processed documents to a destination.

A `VectorStore` can act as a document writer.

A typical ingestion process is:

```text
PDF
 |
 v
DocumentReader
 |
 v
Documents
 |
 v
TextSplitter
 |
 v
Document Chunks
 |
 v
VectorStore
```

---

# 20. Model Context Protocol

Spring AI includes support for the Model Context Protocol (MCP).

MCP defines a protocol through which AI applications can interact with externally exposed:

- Tools
- Resources
- Prompts
- Context providers

Conceptually:

```text
Spring AI Application
        |
        v
    MCP Client
        |
        v
    MCP Server
        |
        +---- Tools
        +---- Resources
        +---- External Systems
```

Spring-based services can also expose capabilities through MCP for use by compatible AI clients.

---

# 21. Observability

AI interactions introduce operational concerns such as:

- Model latency
- Token usage
- Request counts
- Errors
- Tool executions
- Vector-store operations
- Advisor execution
- Model provider performance

Spring AI integrates AI operations with Spring's observability infrastructure.

This enables AI calls to participate in application monitoring and tracing rather than existing as opaque external operations.

Typical telemetry can be associated with:

```text
ChatModel
EmbeddingModel
ImageModel
VectorStore
ChatClient
Tool Calling
```

---

# 22. Evaluation

Generated output is non-deterministic and should often be evaluated differently from traditional application output.

Spring AI provides evaluation abstractions that can be used to examine AI responses.

Possible evaluation criteria include:

- Relevance
- Correctness
- Faithfulness to supplied context
- Completeness
- Consistency

For RAG applications, evaluation can help determine whether an answer is supported by the documents retrieved for the request.

---

# 23. Spring Boot Integration

Spring AI modules provide Spring Boot auto-configuration for supported model providers and infrastructure integrations.

At runtime, an application can typically work with abstractions such as:

```java
ChatModel
ChatClient.Builder
EmbeddingModel
VectorStore
ChatMemory
```

rather than manually constructing every provider client.

The overall dependency structure is:

```text
Spring Application
       |
       v
Spring AI API
       |
       v
Provider Integration
       |
       v
External AI Service
```

This separation is one of the primary mechanisms Spring AI uses to provide portability across providers.

---

# 24. Choosing the Correct API Level

Spring AI exposes multiple levels of abstraction.

## Use `ChatModel` when

You need:

- Direct model access
- Explicit `Prompt` construction
- Access to raw model responses
- Lower-level control

```text
Application -> ChatModel -> Provider
```

## Use `ChatClient` when

You need:

- Fluent prompt creation
- Advisors
- Structured output
- Tool registration
- Reusable default configuration

```text
Application -> ChatClient -> ChatModel -> Provider
```

## Use Advisors when

You need reusable behavior around model calls.

```text
Application
    |
    v
ChatClient
    |
    v
Advisors
    |
    v
ChatModel
```

## Use `VectorStore` when

You need:

- Semantic search
- Document retrieval
- Embedding-backed storage
- RAG

## Use the ETL APIs when

You need to ingest and prepare external documents for retrieval.

---

# 25. Framework Component Summary

```text
+-------------------------------------------------------+
|                    Spring AI                          |
+-------------------------------------------------------+
|                                                       |
|  ChatClient                                           |
|      |                                                |
|      +---- Advisors                                   |
|      |       +---- Memory                             |
|      |       +---- RAG                                |
|      |       +---- Custom Processing                  |
|      |                                                |
|      +---- Structured Output                          |
|      +---- Tools                                      |
|      |                                                |
|      v                                                |
|  ChatModel ----------------------------------------+  |
|                                                   |  |
|  EmbeddingModel                                   |  |
|      |                                            |  |
|      v                                            |  |
|  VectorStore                                      |  |
|                                                   |  |
|  Document ETL                                     |  |
|      +---- Reader                                 |  |
|      +---- Transformer                            |  |
|      +---- Writer                                 |  |
|                                                   |  |
|  MCP                                              |  |
|  Evaluation                                       |  |
|  Observability                                    |  |
|                                                   |  |
+---------------------------------------------------|--+
                                                    |
                                                    v
                                         AI Model Providers
```

---

# 26. Core Concepts

The central Spring AI programming model can be summarized as:

```text
Prompt
  -> Messages
  -> ChatClient
  -> Advisors
  -> ChatModel
  -> AI Provider
  -> ChatResponse
```

For knowledge-based applications:

```text
External Data
  -> DocumentReader
  -> DocumentTransformer
  -> EmbeddingModel
  -> VectorStore
  -> Retriever
  -> RAG Advisor
  -> ChatClient
  -> ChatModel
```

For actions:

```text
Application Method
  -> Tool Definition
  -> Model Tool Request
  -> Tool Execution
  -> Tool Result
  -> Model Response
```

Together, these abstractions provide a Spring-oriented framework for building AI systems without binding application architecture directly to a single model or infrastructure provider.