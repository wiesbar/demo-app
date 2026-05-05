# demo-application

A small Spring Boot 4 + Kotlin service that exposes an HTTP endpoint backed by an arithmetic expression calculator.

## Build & run

Use the Gradle wrapper (`./gradlew` on Unix/Git Bash, `gradlew.bat` on Windows cmd).

```sh
./gradlew build         # compile + run all checks
./gradlew bootRun       # start the service
./gradlew test          # unit tests only
./gradlew integrationTest   # integration tests only (Spring Boot test context)
./gradlew check         # tests + ktlint + detekt + kover
```

## HTTP API

| Method | Path         | Description                                                    |
|--------|--------------|----------------------------------------------------------------|
| GET    | `/`          | Health check — returns `"The Demo Service is running!"`.       |
| POST   | `/calculate` | Evaluates the request body as an arithmetic expression.        |

`POST /calculate` accepts the expression as a plain `text/plain` body and returns the result as a string:

```sh
curl -X POST http://localhost:8080/calculate -H 'Content-Type: text/plain' --data '1 + 2 * 3'
# 7.0
```

Errors are returned as a JSON body of shape `{ "status", "error", "message" }`:

- **400 Bad Request** — the expression is invalid (any failure from the calculator engine, e.g. unknown character, unmatched parenthesis, missing operand).
- **500 Internal Server Error** — anything else, including framework-level errors such as using the wrong HTTP method on `/calculate`.

## Arithmetic expression calculator

The `/calculate` endpoint is backed by a self-contained expression evaluator in the `example.calculator` package. It supports integer and decimal literals, the binary operators `+`, `-`, `*` (with standard precedence), unary minus, and parentheses for grouping. Invalid input is rejected with a structured error message that points at the offending position.

See [docs/calculator.md](docs/calculator.md) for the evaluator pipeline, the full syntax reference, examples, and the error catalogue.
