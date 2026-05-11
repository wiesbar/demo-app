# demo-application

A small Spring Boot 4 + Kotlin service that exposes an arithmetic expression calculator and a furniture catalog with full-text and semantic search backed by Elasticsearch (BM25 `multi_match` plus kNN cosine over a frozen TF-IDF embedding).

## Build & run

Use the Gradle wrapper (`./gradlew` on Unix/Git Bash, `gradlew.bat` on Windows cmd). A running Docker daemon is required for `bootRun`, `integrationTest`, and `check` — the integration tests start Elasticsearch via Testcontainers, and `bootRun` starts the local container declared in `compose.yaml` via `spring-boot-docker-compose`.

```sh
./gradlew build         # compile + run all checks
./gradlew bootRun       # start the service (requires Docker)
./gradlew test          # unit tests only
./gradlew integrationTest   # integration tests only (requires Docker)
./gradlew check         # tests + ktlint + detekt + kover (requires Docker)
```

### Dependency management

All plugin and library versions are declared in the Gradle version catalog at `gradle/libs.versions.toml`. 
The Spring Boot, Kotest, and Testcontainers versions are applied via BOMs imported with Gradle's native `platform(...)` mechanism in `build.gradle.kts`, 
so most coordinates inside `dependencies { }` are unversioned. 
The legacy `io.spring.dependency-management` plugin is not used.

## HTTP API

| Method | Path                          | Description                                                                |
|--------|-------------------------------|----------------------------------------------------------------------------|
| GET    | `/`                           | Health check — returns `"The Demo Service is running!"`.                   |
| POST   | `/calculate`                  | Evaluates the request body as an arithmetic expression.                    |
| PUT    | `/products/{id}`              | Upserts a product into the furniture catalog (idempotent).                 |
| DELETE | `/products/{id}`              | Removes a product from the catalog by id.                                  |
| GET    | `/products/search`            | Full-text + fuzzy search over the catalog; optional `category` filter.     |
| POST   | `/products/semantic-search`   | kNN cosine search over the embedding stored at index time.                 |

Errors are returned as a JSON body of shape `{ "status", "error", "message" }`. The status mapping is:

- **400 Bad Request** — invalid input from a client (calculator parse error, invalid product payload, blank required field, unparseable JSON, blank `q`/`query`, `size`/`limit` out of range, `minScore` out of range).
- **404 Not Found** — `DELETE /products/{id}` for an id that is not in the index.
- **500 Internal Server Error** — anything else, including framework-level errors such as using the wrong HTTP method on `/calculate` or Elasticsearch connectivity failures.

## Profiles

The application is split across two Spring profiles, each gating a self-contained feature set:

- `calculator` — registers the `POST /calculate` endpoint and the underlying `ArithmeticExpressionCalculator` bean.
- `catalog` — registers the `/products/**` endpoints, the furniture repository / serializer / query builder / semantic scorer / index initializer beans, and the Spring Boot Elasticsearch autoconfigurations (`ElasticsearchClientAutoConfiguration`, `ElasticsearchRestClientAutoConfiguration`, `ElasticsearchRestHealthContributorAutoConfiguration`, `DataElasticsearchAutoConfiguration`, `DataElasticsearchRepositoriesAutoConfiguration`). With this profile inactive, Elasticsearch is not contacted at all.

Both profiles are active by default via `spring.profiles.default=calculator,catalog` in `application.yaml`. To disable one, set `SPRING_PROFILES_ACTIVE` explicitly:

```sh
SPRING_PROFILES_ACTIVE=catalog   ./gradlew bootRun   # /calculate is disabled
SPRING_PROFILES_ACTIVE=calculator ./gradlew bootRun  # /products/** is disabled, no ES required
```

`GET /` (health check) is registered on `HealthController` without a profile gate, so it responds regardless of which profiles are active. Disabled endpoints are not registered, so requests against them are unmapped and fall through to the catch-all `GlobalExceptionHandler` as HTTP 500 (not 404) — matching the framework-error mapping documented above.

## Arithmetic expression calculator

The `/calculate` endpoint is backed by a self-contained expression evaluator in the `example.calculator` package. It supports integer and decimal literals, the binary operators `+`, `-`, `*` (with standard precedence), unary minus, and parentheses for grouping. Invalid input is rejected with a structured error message that points at the offending position.

`POST /calculate` accepts the expression as a plain `text/plain` body and returns the result as a string:

```sh
curl -X POST http://localhost:8080/calculate -H 'Content-Type: text/plain' --data '1 + 2 * 3'
# 7.0
```

See [docs/calculator.md](docs/calculator.md) for the evaluator pipeline, the full syntax reference, examples, and the error catalogue.

## Product catalog

The `/products/**` endpoints are backed by the `example.catalog` package, which talks to a local Elasticsearch instance. Keyword search uses a `multi_match` query over `name` (boosted 3×) and `description` with `AUTO` fuzziness, and an optional exact-match `category` filter. Semantic search runs a kNN cosine query against a `dense_vector` field (`embedding`) populated at index time by a frozen TF-IDF scorer (`FrozenTfIdfSemanticScorer` over a 22-stem vocabulary in `EmbeddingVocabulary`); the same scorer embeds the query at search time. The index is created on startup with an explicit mapping if it does not already exist.

The local Elasticsearch service in `compose.yaml` is started automatically by `spring-boot-docker-compose` when you run `./gradlew bootRun`. The cluster URI can be overridden with the `ELASTICSEARCH_URI` environment variable (default `http://localhost:9200`).

See [docs/product-catalog.md](docs/product-catalog.md) for the domain model, HTTP request/response shapes, search behavior, indexing details, and the error catalogue.
