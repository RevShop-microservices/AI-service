# AI Service

The **AI Service** is a Spring Boot microservice that powers the intelligent shopping assistant — **NexShop AI** — in the e-commerce platform. It provides natural language understanding, semantic vector search, LLM-generated product comparisons, and conversational chat history, all backed by a locally running **Ollama** model.

---

## Features

- **Natural Language Query Processing**: A single `/api/ai/query` endpoint accepts free-text user queries and automatically classifies the intent to route to the appropriate handler.
- **Intent Classification (Rule-based)**: An `IntentService` classifies every query into one of 8 intents without requiring an external LLM call for routing:
  - `SEARCH` — semantic product discovery
  - `COMPARE` — side-by-side product comparison with LLM-generated reasoning
  - `RECOMMEND` — top in-stock product recommendations
  - `PRICE_FILTER` — budget-aware filtering (e.g., *"laptops under ₹40000"*)
  - `CATEGORY_BROWSE` — list all categories or filter products by one
  - `ORDER_HELP` — guided order tracking, cancellation, and return instructions
  - `GREET` — welcome message with capability overview
  - `UNKNOWN` — graceful fallback with prompt suggestions
- **Semantic Vector Search (RAG)**: Queries are converted to embeddings via `nomic-embed-text` and ranked against pre-stored product embeddings using **cosine similarity** — no external vector DB required.
- **LLM-Powered Product Comparison**: For `COMPARE` queries, a structured prompt is sent to the **Ollama** LLM (`phi3:mini`) which generates a natural-language comparison with a value-for-money recommendation.
- **Vector Embeddings API**: Exposes a standalone `/api/ai/embeddings` endpoint to generate embeddings for arbitrary text using `nomic-embed-text`.
- **Persistent Chat History**: Every query-response pair is saved to MongoDB (`chat_history` collection) per `userId` for future analytics or session replay.
- **Graceful Degradation**: If Ollama is offline, all non-LLM intents (search, recommend, filter, browse) continue to function normally; only LLM-summary fields will return an offline message.
- **JWT Security**: All endpoints are protected with stateless JWT authentication forwarded from the API Gateway.
- **OpenAPI Docs**: Swagger UI available at `http://localhost:8085/swagger-ui.html`.
- **Distributed Tracing**: Integrated with Zipkin via Micrometer Brave for end-to-end request tracing.

---

## Tech Stack

| Category | Technology |
| :--- | :--- |
| **Core** | Spring Boot 3.3.2, Java 21 |
| **AI / LLM** | Ollama (`phi3:mini` for generation, `nomic-embed-text` for embeddings) |
| **Vector Search** | In-process cosine similarity via `VectorUtils` |
| **Data - Document** | Spring Data MongoDB (`products`, `chat_history`) |
| **Data - Relational** | Spring Data JPA, MySQL 8 (`reviews`) |
| **Security** | Spring Security, JJWT 0.11.5 |
| **Service Discovery** | Spring Cloud Netflix Eureka Client |
| **Centralized Config** | Spring Cloud Config Client |
| **API Docs** | SpringDoc OpenAPI 2.5.0 |
| **Tracing** | Zipkin, Micrometer Brave |
| **Build & Coverage** | Maven Wrapper, Jacoco, SonarQube |

---

## AI Pipeline Architecture

```
User Query (natural language)
        │
        ▼
 IntentService.classify()
 (keyword rule-based, no LLM required)
        │
        ├── SEARCH         ──► EmbeddingService (nomic-embed-text)
        │                      ──► Cosine similarity over product.embedding[]
        │
        ├── COMPARE        ──► EmbeddingService (top-2 products)
        │                      ──► callLLM(phi3:mini) → reasoning + winner
        │
        ├── RECOMMEND      ──► EmbeddingService + in-stock filter
        │
        ├── PRICE_FILTER   ──► Regex price extraction + semantic re-rank
        │
        ├── CATEGORY_BROWSE──► MongoDB category grouping / keyword match
        │
        ├── ORDER_HELP     ──► Static FAQ response
        │
        └── GREET / UNKNOWN──► Static response
                │
                ▼
       ChatHistoryRepository.save(userId, query, response)
                │
                ▼
          AIResponseDTO (JSON response to client)
```

---

## Configuration

The service runs on port **`8085`** and fetches its configuration from the centralized Spring Cloud Config Server.

### Key Properties (`AI-service.properties` in Config Server):

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `spring.data.mongodb.uri` | Atlas connection string | MongoDB URI for the `ecommerceDB` database |
| `spring.datasource.url` | `jdbc:mysql://localhost:3307/EcommerceDB` | MySQL database URL (for reviews) |
| `spring.datasource.username` | `root` | MySQL username |
| `spring.datasource.password` | `root123` | MySQL password |
| `ollama.base-url` | `http://localhost:11434` | Ollama server base URL |
| `ollama.model` | `phi3:mini` | LLM model for text generation |
| `ollama.embed-model` | `nomic-embed-text` | Model for vector embedding generation |

### Environment Variables (Docker / K8s override):

```
MONGO_URI
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
OLLAMA_URL
OLLAMA_MODEL
OLLAMA_EMBED_MODEL
EUREKA_SERVER_URL
CONFIG_SERVER_URL
```

---

## REST API Documentation

### AI Endpoints (`/api/ai`)

| Method | Path | Auth Required | Description |
| :--- | :--- | :---: | :--- |
| **POST** | `/api/ai/query` | ✓ | Process a natural language query. Auto-classifies intent and returns products, comparisons, or guidance. Accepts `userId` query param and raw text body. |
| **POST** | `/api/ai/embeddings` | ✓ | Generate a vector embedding (`List<Double>`) for arbitrary input text using `nomic-embed-text`. |
| **GET** | `/api/ai/health` | ✗ | Liveness probe — returns current model name and running status. |

### Sample Queries by Intent

| Intent | Example Query |
| :--- | :--- |
| `SEARCH` | `"Find me wireless earbuds"` |
| `COMPARE` | `"Compare iPhone vs Samsung Galaxy"` |
| `RECOMMEND` | `"Best gaming mouse under ₹3000"` |
| `PRICE_FILTER` | `"Laptops under ₹40000"` |
| `CATEGORY_BROWSE` | `"Browse electronics"` / `"What categories do you have?"` |
| `ORDER_HELP` | `"How do I track my order?"` / `"Can I cancel my order?"` |
| `GREET` | `"Hi"` / `"Hello"` / `"What can you do?"` |

---

## Response Structure (`AIResponseDTO`)

```json
{
  "intent": "COMPARE",
  "message": "⚖️ Comparing iPhone 15 vs Samsung Galaxy S24",
  "products": [],
  "compareResult": {
    "product1": { "id": "...", "name": "iPhone 15", "price": 79999.0, ... },
    "product2": { "id": "...", "name": "Samsung Galaxy S24", "price": 74999.0, ... },
    "winner": "Samsung Galaxy S24",
    "reasoning": "Samsung offers a slightly better price point..."
  },
  "llmSummary": "Both are flagship smartphones... Samsung wins on value for money."
}
```

---

## Data Models

### `products` collection (MongoDB) — read-only by AI Service

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | MongoDB document ID |
| `name` | `String` | Product name |
| `brand` | `String` | Product brand |
| `category` | `String` | Product category |
| `price` | `Double` | Product price (INR) |
| `stock` | `Integer` | Available stock count |
| `description` | `String` | Text description used for embedding |
| `tags` | `List<String>` | Searchable tags |
| `images` | `List<String>` | Image URLs |
| `embedding` | `List<Double>` | Pre-computed vector embedding |

### `chat_history` collection (MongoDB)

| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | MongoDB document ID |
| `userId` | `Long` | ID of the querying user |
| `query` | `String` | Original user query |
| `response` | `String` | AI response message |
| `timestamp` | `LocalDateTime` | When the query was made |

---

## Ollama Setup (Required for LLM features)

Install and run [Ollama](https://ollama.com) locally or in a container, then pull the required models:

```bash
# Pull the LLM generation model
ollama pull phi3:mini

# Pull the embedding model
ollama pull nomic-embed-text

# Verify Ollama is running
curl http://localhost:11434/api/tags
```

> **Docker Compose**: The `docker-compose.yml` includes an `ollama` service pre-configured. Models must be pulled after the container starts.

> **Graceful Degradation**: If Ollama is unavailable, SEARCH, RECOMMEND, PRICE_FILTER, and CATEGORY_BROWSE still function using keyword matching. Only `COMPARE` will return an "AI model offline" message.

---

## Build, Test, and Run

### 1. Compile and Package
```bash
./mvnw clean package
```
> Tests are skipped by default (`skipTests=true` in `pom.xml`).

### 2. Run Tests
```bash
./mvnw test -DskipTests=false
```

### 3. Run Locally
```bash
./mvnw spring-boot:run
```
> Ensure the following services are running before starting:
> - Config Server (`8888`)
> - Eureka Server (`8761`)
> - MongoDB (`27017`)
> - MySQL (`3307`)
> - Ollama (`11434`) — required for LLM and embedding features

### 4. Build Docker Image
```bash
docker build -t ai-service .
```
> The `Dockerfile` uses `eclipse-temurin:17-jre-alpine` and copies the pre-built JAR from `target/`.

### 5. Run via Docker Compose
```bash
docker compose up ai-service ollama
```
> Refer to the root `docker-compose.yml` for the full environment variable configuration.

---

## CI/CD

The GitHub Actions workflow at [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) automatically:
1. Checks out the code.
2. Sets up JDK 17 (Temurin) with Maven dependency caching.
3. Builds the service with `./mvnw clean package -DskipTests=false`.
4. Builds and pushes the Docker image to **GitHub Container Registry (GHCR)** on every push to `main`, `master`, or `develop`.
