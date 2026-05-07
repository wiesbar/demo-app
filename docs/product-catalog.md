# Product catalog

The `example.catalog` package implements a furniture catalog backed by Elasticsearch. It exposes two collaborator interfaces — `FurnitureIndexer` (write side: upsert / delete) and `FurnitureSearchEngine` (read side: keyword `search` and `semanticSearch`) — both implemented by `FurnitureRepository`. The HTTP surface lives in `example.web.CatalogController`, with request/response payloads in `example.web.ProductDto`. Embedding generation for both indexing and querying is handled by `SemanticScorer`, with `FrozenTfIdfSemanticScorer` as the production implementation.

## HTTP endpoints

### `PUT /products/{id}`

Upserts a product. Idempotent: re-PUTting the same `id` replaces the existing document.

Request body (`application/json`):

```json
{
  "category": "TABLE",
  "name": "Oak dining table",
  "description": "Solid oak, seats six.",
  "dimensions": {
    "width":  { "value": 180, "unit": "Centimeter" },
    "height": { "value":  75, "unit": "Centimeter" },
    "depth":  { "value":  90, "unit": "Centimeter" }
  }
}
```

Responses:

- `204 No Content` — indexed.
- `400 Bad Request` — id, name, or description blank; any dimension `value <= 0`; unparseable JSON (e.g. unknown `unit` or `category`).

### `DELETE /products/{id}`

Removes a product by id.

Responses:

- `204 No Content` — deleted.
- `400 Bad Request` — id is blank.
- `404 Not Found` — no document with that id is in the index.

### `GET /products/search`

Full-text + fuzzy search across the catalog.

Query parameters:

| Param      | Required | Default | Description                                       |
|------------|----------|---------|---------------------------------------------------|
| `q`        | yes      | —       | Search text. Must be non-blank.                   |
| `category` | no       | —       | Exact-match filter; one of `TABLE`, `CHAIR`.      |
| `size`     | no       | `20`    | Maximum number of hits to return; must be 1..100. |

Response (`application/json`):

```json
{
  "hits": [
    {
      "product": {
        "id": "table-001",
        "category": "TABLE",
        "name": "Oak dining table",
        "description": "Solid oak, seats six.",
        "dimensions": {
          "width":  { "value": 180, "unit": "Centimeter" },
          "height": { "value":  75, "unit": "Centimeter" },
          "depth":  { "value":  90, "unit": "Centimeter" }
        }
      },
      "score": 4.21
    }
  ]
}
```

Responses:

- `200 OK` — search executed; `hits` may be empty.
- `400 Bad Request` — `q` blank or `size` out of range.

### `POST /products/semantic-search`

kNN cosine search over the embedding stored at index time. The query string is embedded with the same scorer and `dense_vector` `similarity: cosine` ranks documents by `(1 + cos)/2 ∈ [0, 1]`.

Request body (`application/json`):

```json
{
  "query": "comfortable armchair",
  "limit": 10,
  "minScore": 0.0
}
```

| Field      | Required | Default | Description                                                                              |
|------------|----------|---------|------------------------------------------------------------------------------------------|
| `query`    | yes      | —       | Free-text query. Must be non-blank.                                                      |
| `limit`    | no       | `10`    | Maximum number of hits; must be 1..100. Used as ES kNN `k`.                              |
| `minScore` | no       | `0.0`   | Minimum ES score (server-side `min_score` filter); must be 0.0..1.0.                     |

Response shape is the same `SearchResponseDto` as `GET /products/search` (a `hits` array, no `total` field).

Responses:

- `200 OK` — search executed; `hits` may be empty (e.g. when every query token is out of vocabulary, the scorer returns the zero vector and the service short-circuits to `[]`).
- `400 Bad Request` — `query` blank, `limit` out of range, `minScore` out of range, or unparseable JSON.

## Domain model

All catalog types live in `example.catalog` and are `internal` to keep Spring/HTTP concerns out of the package. DTOs in `example.web.ProductDto` map 1:1 to the domain and the controller does the conversion.

| Type           | Notes                                                                                       |
|----------------|---------------------------------------------------------------------------------------------|
| `Product`      | `id`, `category`, `name`, `description`, `dimensions`.                                      |
| `Dimensions`   | Aggregate of `width`, `height`, `depth`, each a `Dimension`.                                |
| `Dimension`    | `value: Int`, `unit: UnitOfMeasure`. Init block enforces `value > 0`. Use the `Int.centimeters` / `Int.millimeters` extension getters in `Dimension.kt` for literal construction (e.g. `100.centimeters`). |
| `Category`     | Enum: `TABLE`, `CHAIR`. Wire form is the Kotlin name (`"TABLE"` / `"CHAIR"`).               |
| `UnitOfMeasure`| Enum: `MILLIMETER`, `CENTIMETER`. Wire form is `"Millimeter"` / `"Centimeter"` via Jackson. |

`UnitOfMeasure` uses `@JsonProperty` per constant so the JSON wire format stays PascalCase (`"Millimeter"`, `"Centimeter"`) while the Kotlin constants follow `UPPER_SNAKE_CASE`. Validation in `Dimension.init` throws the domain `InvalidProductException` (not `IllegalArgumentException`), which `GlobalExceptionHandler` maps to 400.

## Keyword search behavior

`FurnitureRepository.search` builds a `bool` query with a single `must` clause:

- `multi_match` over `name^3` and `description`, with `fuzziness: AUTO`. Hits on `name` score 3× higher than hits on `description`.

When a `category` is supplied, a `term` filter on the `category` keyword field is added under `filter` (so it constrains the result set without affecting score).

`size` is passed straight through as the maximum number of hits. The controller rejects `size` outside `1..100`; the default is `20`.

## Semantic search behavior

`FurnitureRepository.semanticSearch` delegates to `ProductQueryBuilder.buildKnn(query, limit, minScore)`, which embeds the query via the injected `SemanticScorer` and either:

- returns `null` if the embedding is the zero vector (every token is out of vocabulary), so the repository short-circuits to `emptyList()`; or
- builds a `NativeQuery` with `withKnnSearches(KnnSearch(field = "embedding", queryVector, k = limit, numCandidates = limit * 10))` and `withMinScore(minScore.toFloat())` so Elasticsearch filters server-side.

The `category` filter is intentionally not supported on the semantic-search path — the HTTP contract has no `category` parameter.

### Embedding model

`FrozenTfIdfSemanticScorer` is the production implementation of `SemanticScorer`. It produces a fixed-dimension `FloatArray` per document and per query:

- **Vocabulary**: `EmbeddingVocabulary` derives a 22-stem vocabulary and an IDF map at class init from a hard-coded 7-document seed corpus. The vocabulary is *frozen* — terms outside it are dropped.
- **Tokenization**: lowercase, regex-split on non-alphanumeric, drop a small English stopword list, suffix-`s` stemming (`chairs → chair`), then synonym mapping (`couch → sofa`, `armchair → chair`, …).
- **Document embedding** (`embedDocument(name, description)`): TF over name tokens with `NAME_BOOST = 3` plus TF over description tokens with weight 1, multiplied by the frozen IDF, then L2-normalized.
- **Query embedding** (`embedQuery(query)`): same pipeline but no name boost (queries have no name field).

A document whose tokens are entirely out of vocabulary embeds to the zero vector. Such products are still indexed (the `embedding` field is omitted from the JSON source so Elasticsearch's `dense_vector` with `similarity: cosine`, which rejects zero-magnitude vectors, accepts the document) but they will not surface in semantic-search results until at least one in-vocabulary term is added.

## Indexing

The index is named `furniture`. `IndexInitializer` runs as an `ApplicationRunner` and creates the index with an explicit mapping if it does not already exist; the call is idempotent across restarts. The mapping (loaded from a JSON literal in `IndexInitializer.kt`) is:

| Field                                   | Type           | Notes                                                                                                       |
|-----------------------------------------|----------------|-------------------------------------------------------------------------------------------------------------|
| `category`                              | `keyword`      | Used by the optional `term` filter.                                                                         |
| `name`                                  | `text`         | Plus a `name.keyword` subfield (`keyword`).                                                                 |
| `description`                           | `text`         | Uses the built-in `english` analyzer.                                                                       |
| `dimensions.{width,height,depth}.value` | `integer`      | Raw numeric value.                                                                                          |
| `dimensions.{width,height,depth}.unit`  | `keyword`      | `Millimeter` or `Centimeter`.                                                                               |
| `embedding`                             | `dense_vector` | `dims = EmbeddingVocabulary.size` (22), `similarity: cosine`. Omitted from the source JSON if the vector is all zeros (`@JsonInclude(NON_EMPTY)` on `ProductDocument.embedding` plus an explicit guard in `ProductSerializer`). |

Documents are written via `ElasticsearchOperations.index(...)` with the client-supplied `id` as the document `_id`, so PUT semantics are upsert. `ProductSerializer.serialize(product)` calls `scorer.embedDocument(name, description)` and embeds the resulting vector in the source under the `embedding` field; `IndexInitializer` declares the matching `dense_vector` mapping at startup.

## Configuration

`application.yaml`:

```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URI:http://localhost:9200}
    connection-timeout: 2s
    socket-timeout: 5s
```

Override the cluster URI with the `ELASTICSEARCH_URI` environment variable. The local Elasticsearch service in `compose.yaml` is started automatically by `spring-boot-docker-compose` when `bootRun` is invoked.

## Errors

All catalog errors are returned through `GlobalExceptionHandler` as a JSON body of shape `{ "status", "error", "message" }`:

- `400 Bad Request` — `InvalidProductException`. Sources:
  - `Dimension.init`: *dimension value must be positive, got 0*.
  - Controller validation: *product id must not be blank*, *product name must not be blank*, *product description must not be blank*.
  - Keyword search validation: *query 'q' must not be blank*, *query 'size' must be in 1..100, got 0*.
  - Semantic search validation: *query 'query' must not be blank*, *query 'limit' must be in 1..100, got 0*, *query 'minScore' must be in 0.0..1.0, got -0.1*.
- `400 Bad Request` — `HttpMessageNotReadableException`. Triggered by unparseable JSON, e.g. `"unit": "Inches"` or `"category": "DESK"`. The response message is the most-specific Jackson cause.
- `404 Not Found` — `ProductNotFoundException`, raised by `DELETE /products/{id}` when the id is not in the index. Message: *product with id '...' not found*.
- `500 Internal Server Error` — catch-all. Elasticsearch connectivity or query failures fall through here; ES internals are not exposed.

## Examples

Upsert a table:

```sh
curl -X PUT http://localhost:8080/products/table-001 \
  -H 'Content-Type: application/json' \
  --data '{
    "category": "TABLE",
    "name": "Oak dining table",
    "description": "Solid oak, seats six.",
    "dimensions": {
      "width":  { "value": 180, "unit": "Centimeter" },
      "height": { "value":  75, "unit": "Centimeter" },
      "depth":  { "value":  90, "unit": "Centimeter" }
    }
  }'
# 204 No Content
```

Upsert a chair:

```sh
curl -X PUT http://localhost:8080/products/chair-001 \
  -H 'Content-Type: application/json' \
  --data '{
    "category": "CHAIR",
    "name": "Beech kitchen chair",
    "description": "Stackable beech-wood chair.",
    "dimensions": {
      "width":  { "value": 450, "unit": "Millimeter" },
      "height": { "value": 900, "unit": "Millimeter" },
      "depth":  { "value": 500, "unit": "Millimeter" }
    }
  }'
# 204 No Content
```

Free-text search:

```sh
curl 'http://localhost:8080/products/search?q=oak+dining'
```

Search restricted to chairs:

```sh
curl 'http://localhost:8080/products/search?q=beech&category=CHAIR&size=10'
```

Semantic search:

```sh
curl -X POST http://localhost:8080/products/semantic-search \
  -H 'Content-Type: application/json' \
  --data '{ "query": "comfortable armchair", "limit": 5 }'
```

Delete a product:

```sh
curl -X DELETE http://localhost:8080/products/table-001
# 204 No Content
```
