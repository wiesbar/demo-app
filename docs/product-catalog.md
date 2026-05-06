# Product catalog

The `example.catalog` package implements a furniture catalog backed by Elasticsearch. It exposes two collaborator interfaces — `FurnitureIndexer` (write side: upsert / delete) and `FurnitureSearchEngine` (read side: full-text search) — both implemented by `FurnitureRepository`. The HTTP surface lives in `example.web.CatalogController`, with request/response payloads in `example.web.ProductDto`.

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
  ],
  "total": 1
}
```

`total` is the number of hits in the response, capped by `size`.

Responses:

- `200 OK` — search executed; `hits` may be empty.
- `400 Bad Request` — `q` blank or `size` out of range.

## Domain model

All catalog types live in `example.catalog` and are `internal` to keep Spring/HTTP concerns out of the package. DTOs in `example.web.ProductDto` map 1:1 to the domain and the controller does the conversion.

| Type           | Notes                                                                                       |
|----------------|---------------------------------------------------------------------------------------------|
| `Product`      | `id`, `category`, `name`, `description`, `dimensions`.                                      |
| `Dimensions`   | Aggregate of `width`, `height`, `depth`, each a `Dimension`.                                |
| `Dimension`    | `value: Int`, `unit: UnitOfMeasure`. Init block enforces `value > 0`.                       |
| `Category`     | Enum: `TABLE`, `CHAIR`. Wire form is the Kotlin name (`"TABLE"` / `"CHAIR"`).               |
| `UnitOfMeasure`| Enum: `MILLIMETER`, `CENTIMETER`. Wire form is `"Millimeter"` / `"Centimeter"` via Jackson. |

`UnitOfMeasure` uses `@JsonProperty` per constant so the JSON wire format stays PascalCase (`"Millimeter"`, `"Centimeter"`) while the Kotlin constants follow `UPPER_SNAKE_CASE`. Validation in `Dimension.init` throws the domain `InvalidProductException` (not `IllegalArgumentException`), which `GlobalExceptionHandler` maps to 400.

## Search behavior

`FurnitureRepository.search` builds a `bool` query with a single `must` clause:

- `multi_match` over `name^3` and `description`, with `fuzziness: AUTO`. Hits on `name` score 3× higher than hits on `description`.

When a `category` is supplied, a `term` filter on the `category` keyword field is added under `filter` (so it constrains the result set without affecting score).

`size` is passed straight through as the maximum number of hits. The controller rejects `size` outside `1..100`; the default is `20`.

## Indexing

The index is named `furniture`. `IndexInitializer` runs as an `ApplicationRunner` and creates the index with an explicit mapping if it does not already exist; the call is idempotent across restarts. The mapping (loaded from a JSON literal in `IndexInitializer.kt`) is:

| Field                             | Type      | Notes                                                |
|-----------------------------------|-----------|------------------------------------------------------|
| `category`                        | `keyword` | Used by the optional `term` filter.                  |
| `name`                            | `text`    | Plus a `name.keyword` subfield (`keyword`).          |
| `description`                     | `text`    | Uses the built-in `english` analyzer.                |
| `dimensions.{width,height,depth}.value` | `integer` | Raw numeric value.                              |
| `dimensions.{width,height,depth}.unit`  | `keyword` | `Millimeter` or `Centimeter`.                   |

Documents are written via `ElasticsearchOperations.index(...)` with the client-supplied `id` as the document `_id`, so PUT semantics are upsert.

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
  - Search validation: *query 'q' must not be blank*, *query 'size' must be in 1..100, got 0*.
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

Delete a product:

```sh
curl -X DELETE http://localhost:8080/products/table-001
# 204 No Content
```
