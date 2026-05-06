# demo-application

A small Spring Boot 4 + Kotlin service that exposes an arithmetic expression calculator and a furniture catalog with full-text search backed by Elasticsearch.

## Build & run

Use the Gradle wrapper (`./gradlew` on Unix/Git Bash, `gradlew.bat` on Windows cmd).

```sh
./gradlew build         # compile + run all checks
./gradlew bootRun       # start the service
./gradlew test          # unit tests only
./gradlew integrationTest   # integration tests only (Spring Boot test context)
./gradlew check         # tests + ktlint + detekt + kover
```

`bootRun` starts the local Elasticsearch container declared in `compose.yaml` automatically via `spring-boot-docker-compose`.

## HTTP API

| Method | Path                  | Description                                                              |
|--------|-----------------------|--------------------------------------------------------------------------|
| GET    | `/`                   | Health check — returns `"The Demo Service is running!"`.                 |
| POST   | `/calculate`          | Evaluates the request body as an arithmetic expression.                  |
| PUT    | `/products/{id}`      | Upserts a product into the furniture catalog (idempotent).               |
| DELETE | `/products/{id}`      | Removes a product from the catalog by id.                                |
| GET    | `/products/search`    | Full-text + fuzzy search over the catalog; optional `category` filter.   |

Errors are returned as a JSON body of shape `{ "status", "error", "message" }`. The status mapping is:

- **400 Bad Request** — invalid input from a client (calculator parse error, invalid product payload, blank required field, unparseable JSON, blank `q`, `size` out of range).
- **404 Not Found** — `DELETE /products/{id}` for an id that is not in the index.
- **500 Internal Server Error** — anything else, including framework-level errors such as using the wrong HTTP method on `/calculate` or Elasticsearch connectivity failures.

## Arithmetic expression calculator

The `/calculate` endpoint is backed by a self-contained expression evaluator in the `example.calculator` package. It supports integer and decimal literals, the binary operators `+`, `-`, `*` (with standard precedence), unary minus, and parentheses for grouping. Invalid input is rejected with a structured error message that points at the offending position.

`POST /calculate` accepts the expression as a plain `text/plain` body and returns the result as a string:

```sh
curl -X POST http://localhost:8080/calculate -H 'Content-Type: text/plain' --data '1 + 2 * 3'
# 7.0
```

See [docs/calculator.md](docs/calculator.md) for the evaluator pipeline, the full syntax reference, examples, and the error catalogue.

## Product catalog

The `/products/**` endpoints are backed by the `example.catalog` package, which talks to a local Elasticsearch instance. Search uses a `multi_match` query over `name` (boosted 3×) and `description` with `AUTO` fuzziness, and an optional exact-match `category` filter. The index is created on startup with an explicit mapping if it does not already exist.

The local Elasticsearch service in `compose.yaml` is started automatically by `spring-boot-docker-compose` when you run `./gradlew bootRun`. The cluster URI can be overridden with the `ELASTICSEARCH_URI` environment variable (default `http://localhost:9200`).

See [docs/product-catalog.md](docs/product-catalog.md) for the domain model, HTTP request/response shapes, search behavior, indexing details, and the error catalogue.
