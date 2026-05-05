# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Spring Boot 4.0.5 + Kotlin 2.2.21 on JDK 21, built with Gradle (Kotlin DSL). Uses Spring Boot's new `spring-boot-starter-webmvc` (servlet MVC) and Jackson via `tools.jackson.module:jackson-module-kotlin` (Jackson 3.x coordinates — not `com.fasterxml.jackson`).

## Commands

Use the Gradle wrapper. On Windows bash, `./gradlew` works; `gradlew.bat` is also available.

- Build: `./gradlew build`
- Run app: `./gradlew bootRun` (starts on the default port; `spring-boot-docker-compose` will try to bring up `compose.yaml`, which currently declares no services)
- Run unit tests: `./gradlew test` (only `src/test`)
- Run integration tests: `./gradlew integrationTest` (only `src/integrationTest`)
- Run a single test class: `./gradlew test --tests "example.calculator.DefaultArithmeticExpressionCalculatorTest"` (use `integrationTest` task for IT classes)
- Filter by Kotest test name: `./gradlew test --tests "*should evaluate valid expression*"` (wildcards required — Kotest test names are strings, not method names)
- Lint / format: `./gradlew ktlintCheck` / `./gradlew ktlintFormat`
- Static analysis: `./gradlew detekt`
- Coverage: `./gradlew koverHtmlReport` (HTML under `build/reports/kover/`) or `./gradlew koverVerify`
- `./gradlew check` runs unit tests + integration tests + ktlint + detekt + kover together. Run `./gradlew ktlintFormat check` so formatting fixes are applied before verification.

Kotlin is compiled with `-Xjsr305=strict` and `-Xannotation-default-target=param-property`; keep that in mind when adding annotated constructor parameters (the annotation lands on both the param and the generated property). The detekt configuration pins the Kotlin compiler dependency to 2.0.21 via a `resolutionStrategy` in `build.gradle.kts` — leave that pin alone unless you are deliberately upgrading detekt.

## Architecture

Spring Boot web service split across two packages under root package `example`:

- `example` — `DemoApplication` (the `@SpringBootApplication` entry point in `DemoApplication.kt`). Also exposes a `@Bean` factory `calculator()` that returns `DefaultArithmeticExpressionCalculator` as `ArithmeticExpressionCalculator` — this keeps the `example.calculator` package free of Spring annotations.
- `example.web` — HTTP surface.
  - `MainController` — `GET /` returns the static string `"The Demo Service is running!"`; `POST /calculate` runs the request body through the injected `ArithmeticExpressionCalculator` and returns the result as a string (e.g. `"7.0"`). The constructor is `internal` so the public class doesn't expose the internal calculator interface; Spring still resolves it via reflection inside the same module.
  - `GlobalExceptionHandler` — `@RestControllerAdvice` with two handlers, ordered most-specific first: `InvalidArithmeticExpressionException` (the calculator's domain exception, declared in `example.calculator`) → HTTP 400 with body `{status: "400", error: "Bad Request", message}`; any other `Exception` → HTTP 500 with body `{status: "500", error: "Internal Server Error", message}`. The 400 handler is `internal` so the public class doesn't expose the internal exception type; Spring resolves it via reflection inside the same module. Because the catch-all still matches `Exception`, framework errors like `HttpRequestMethodNotSupportedException` (e.g. `GET /calculate`) are returned as 500 — see `UnsupportedOperationsTest`. If you add more specific handlers, order matters — Spring picks the most specific, but do not broaden the catch-all further.
- `example.calculator` — pure (non-Spring) arithmetic engine. All types are `internal`. Pipeline: `toInfix(expression)` (tokenizer in `InfixParser.kt`) → `.toPostfix()` (shunting-yard in `PostfixParser.kt`) → `Postfix.calculate()` (stack evaluator in `Postfix.kt`). `Tokens.kt` defines the sealed `Token` hierarchy (`Operand`, `BinaryOperator.{Plus,Minus,Times}`, `UnaryOperator.Negate`, `Parenthesis.{Left,Right}`); each token carries its `positionInExpression` so error messages can point at the offending character. `ArithmeticExpressionCalculator` is the public-ish `fun interface`, with `DefaultArithmeticExpressionCalculator` as the production implementation. All parse/evaluate errors throw the engine's own `InvalidArithmeticExpressionException` (a plain `RuntimeException`) — do not use `require`/`requireNotNull` for engine validation, since those throw `IllegalArgumentException` and break the domain-specific exception contract that `GlobalExceptionHandler` relies on for the 400 mapping. Supported syntax: integers/decimals (including leading `.`), whitespace, `+`, `-` (binary and unary), `*`, parentheses. Unary `+` is rejected. There is no `/` operator yet.

`application.yaml` sets `spring.application.name=calculator` and disables the Spring whitelabel error page so `GlobalExceptionHandler` is the sole error renderer.

## Code style rules

- Function bodies should not exceed 15 lines.

## Testing conventions

- **Kotest** (`FunSpec` style, runs on the JUnit Platform via `kotest-runner-junit5`). Use Kotest matchers (`shouldBe`, `shouldThrow`, etc.) for assertions.
- **Source set split**: unit tests live in `src/test/kotlin` and run under `./gradlew test`; integration tests live in `src/integrationTest/kotlin` and run under `./gradlew integrationTest`. Both are wired into `./gradlew check`. The `integrationTest` source set extends `testImplementation`/`testRuntimeOnly`, so Kotest, RestTestClient, and Spring Boot test starters are available without re-declaring them.
- **Naming**: both unit and integration test classes use the `*Test` suffix. Disambiguate by location (source set), not by suffix. One class per HTTP endpoint or behavioral concern (e.g. `RootPathTest`, `CalculatorEndpointTest`, `UnsupportedOperationsTest`) rather than one per controller — this keeps each spec narrowly focused and lets Spring's test context cache reuse the same `ApplicationContext` across all of them.
- Integration tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` on the class, with `extension(SpringExtension)` inside the spec body and constructor-injected `@Autowired` dependencies (the `-Xannotation-default-target=param-property` flag puts `@Autowired` on both the param and the generated property). HTTP assertions go through `RestTestClient` (Spring Boot 4's new test client). See `CalculatorEndpointTest` for the pattern.
- Test names are Kotest `test("…")` / `context("…")` strings — sentence case, no backticks.
- Avoid code duplicates.
- Use Kotest `withData(...)` for table-driven tests; pass `nameFn = { … }` so reports show the input rather than `[1] …`. See `DefaultArithmeticExpressionCalculatorTest` for the pattern (it uses `nameFn` for both the success and failure tables).
- Error-path tests in `DefaultArithmeticExpressionCalculatorTest` assert the full exception message string verbatim, so when you change a message you must update its test row in lockstep.

## TDD Rules
- Always use strict TDD (Red-Green-Refactor) for code changes.
- Write a failing test first.
- Implement only what is necessary to pass the test.
- Refactor after passing.

## Notes for future changes

- The `tmp/` directory is gitignored and should be treated as scratch space.
- `compose.yaml` exists only so `spring-boot-docker-compose` is happy; it currently declares `services: { }`.
