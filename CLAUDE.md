# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Spring Boot 4.0.5 + Kotlin 2.2.21 on JDK 21, built with Gradle (Kotlin DSL). 
All plugin and library versions are declared in the Gradle version catalog at `gradle/libs.versions.toml`; 
Spring Boot, Kotest, and Testcontainers are applied as BOMs via Gradle's native `platform(...)` mechanism (no `io.spring.dependency-management` plugin). 
Uses Spring Boot's new `spring-boot-starter-webmvc` (servlet MVC) and Jackson via `tools.jackson.module:jackson-module-kotlin` (Jackson 3.x coordinates — not `com.fasterxml.jackson`). 
Persistence/search uses Spring Data Elasticsearch with the Elastic Java client 8.x; integration tests spin up a real Elasticsearch via the Testcontainers `elasticsearch` module.

## Commands

**Prerequisite**: Docker Desktop (or any Docker daemon) must be running for `./gradlew integrationTest` and `./gradlew check` — the Elasticsearch testcontainer needs a Docker daemon to start.

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

Kotlin is compiled with `-Xjsr305=strict` and `-Xannotation-default-target=param-property`; 
keep that in mind when adding annotated constructor parameters (the annotation lands on both the param and the generated property). 
The detekt configuration pins the Kotlin compiler dependency to 2.0.21 via the `kotlin-detekt-pin` version in `libs.versions.toml`, 
applied through a `resolutionStrategy` in `build.gradle.kts` — leave that pin alone unless you are deliberately upgrading detekt.

## Architecture

Spring Boot web service split across packages under root package `example`:

- `example` — `DemoApplication` (the `@SpringBootApplication` entry point in `DemoApplication.kt`). The annotation lists the imperative Elasticsearch autoconfig classes in its `exclude = [...]` so they only activate via the `catalog` profile (see `example.config`). No `@Bean` factories live here — all wiring is delegated to the profile-gated configurations.
- `example.config` — profile-gated Spring `@Configuration` classes. `CalculatorConfig` (`@Profile("calculator")`) wires `calculator()` → `DefaultArithmeticExpressionCalculator`. `CatalogConfig` (`@Profile("catalog")`) wires `semanticScorer()`, `productSerializer(...)`, `productQueryBuilder(...)`, `furnitureRepository(...)` (binds `Dispatchers.IO` as the coroutine dispatcher), and `indexInitializer(...)`. It also `@ImportAutoConfiguration`s the ES autoconfigs (`ElasticsearchClientAutoConfiguration`, `ElasticsearchRestClientAutoConfiguration`, `ElasticsearchRestHealthContributorAutoConfiguration`, `DataElasticsearchAutoConfiguration`, `DataElasticsearchRepositoriesAutoConfiguration`) that `DemoApplication` excludes, so Elasticsearch is only configured when the `catalog` profile is active. Keeping the wiring here means `example.calculator` and `example.catalog` stay free of Spring annotations.
- `example.web` — HTTP surface.
  - `HealthController` — `GET /` returns the static string `"The Demo Service is running!"`. No profile gate; this endpoint is always available.
  - `CalculatorController` — `@Profile("calculator")`. `POST /calculate` runs the request body through the injected `ArithmeticExpressionCalculator` and returns the result as a string (e.g. `"7.0"`). The constructor is `internal` so the public class doesn't expose the internal calculator interface; Spring still resolves it via reflection inside the same module.
  - `CatalogController` — `@Profile("catalog")`. `PUT /products/{id}` (index a `ProductDto`, returns 204), `DELETE /products/{id}` (returns 204 or 404 via `ProductNotFoundException`), `GET /products/search?q=&category=&size=` (BM25 keyword search; defaults `size=20`, max 100), `POST /products/semantic-search` (kNN cosine search; body is `SemanticSearchRequest(query, limit?=10, minScore?=0.0)`, returns `SearchResponseDto(hits)` with no `total` field). Validation: `query`/`q` non-blank, `limit`/`size ∈ 1..100`, `minScore ∈ 0.0..1.0`; failures throw `InvalidProductException` which is mapped to HTTP 400 by `GlobalExceptionHandler`.
  - `ProductDto.kt` holds the public DTOs (`ProductDto`, `ProductWithIdDto` with `@JsonUnwrapped product`, `DimensionDto`, `DimensionsDto`, `SearchHitDto`, `SearchResponseDto`, `SemanticSearchRequest`) plus internal `toDomain` / `toDto` extension functions.
  - `GlobalExceptionHandler` — `@RestControllerAdvice` with handlers ordered most-specific first: `InvalidArithmeticExpressionException` and `InvalidProductException` → HTTP 400; `HttpMessageNotReadableException` → HTTP 400 (uses `mostSpecificCause.message`); `ProductNotFoundException` → HTTP 404; `CancellationException` is rethrown so coroutine cancellation is not swallowed; any other `Exception` → HTTP 500 with body `{status: "500", error: "Internal Server Error", message}`. The 400 handlers are `internal` so the public class doesn't expose the internal exception types; Spring resolves them via reflection inside the same module. Because the catch-all still matches `Exception`, framework errors like `HttpRequestMethodNotSupportedException` (e.g. `GET /calculate`) are returned as 500 — see `UnsupportedOperationsTest`. If you add more specific handlers, order matters — Spring picks the most specific, but do not broaden the catch-all further.
- `example.calculator` — pure (non-Spring) arithmetic engine. All types are `internal`. Pipeline: `toInfix(expression)` (tokenizer in `InfixParser.kt`) → `.toPostfix()` (shunting-yard in `PostfixParser.kt`) → `Postfix.calculate()` (stack evaluator in `Postfix.kt`). `Tokens.kt` defines the sealed `Token` hierarchy (`Operand`, `BinaryOperator.{Plus,Minus,Times}`, `UnaryOperator.Negate`, `Parenthesis.{Left,Right}`); each token carries its `positionInExpression` so error messages can point at the offending character. `ArithmeticExpressionCalculator` is the public-ish `fun interface`, with `DefaultArithmeticExpressionCalculator` as the production implementation. All parse/evaluate errors throw the engine's own `InvalidArithmeticExpressionException` (a plain `RuntimeException`) — do not use `require`/`requireNotNull` for engine validation, since those throw `IllegalArgumentException` and break the domain-specific exception contract that `GlobalExceptionHandler` relies on for the 400 mapping. Supported syntax: integers/decimals (including leading `.`), whitespace, `+`, `-` (binary and unary), `*`, parentheses. Unary `+` is rejected. There is no `/` operator yet.
- `example.catalog` — pure (non-Spring, annotation-free) furniture catalog + search engine. All types are `internal` (the `Category` and `UnitOfMeasure` enums are public because they appear on public DTOs). `IndexInitializer` lives here but is wired as a `@Bean` from `CatalogConfig` rather than carrying `@Component`, keeping the package annotation-free.
  - **Domain types**: `Product` (id/category/name/description/dimensions; `init` validates non-blank id/name/description and throws `InvalidProductException` so it maps cleanly to HTTP 400 via `GlobalExceptionHandler` — do not use `require` here, same reasoning as the calculator engine), `Category` (`TABLE`, `CHAIR`), `Dimension`/`Dimensions` (`Dimension.init` rejects non-positive values via `InvalidProductException`), `UnitOfMeasure` (`MILLIMETER`/`CENTIMETER` with `@JsonProperty("Millimeter")` / `@JsonProperty("Centimeter")` for the wire representation). The `Int.centimeters` and `Int.millimeters` extension getters in `Dimension.kt` are the preferred way to construct `Dimension` literals in tests (e.g. `100.centimeters`). `InvalidProductException` and `ProductNotFoundException` live in `Exceptions.kt`. The package exposes two ports: `FurnitureSearchEngine.search(query, category?, size)` and `FurnitureSearchEngine.semanticSearch(query, limit, minScore)`; `FurnitureIndexer.index(product)` and `FurnitureIndexer.deleteIfExists(id)`. Both ports are `suspend` and `CatalogController` injects them as a single port pair.
  - **Persistence**: `FurnitureRepository` implements both ports as one class; every method is `suspend` and runs through `withContext(dispatcher)` (production wires `Dispatchers.IO`) so the blocking `ElasticsearchOperations` calls don't pin servlet threads. `ProductDocument` is the ES-side representation (with `DimensionsDoc`/`DimensionDoc`); `ProductDocument.toDomain(id)` reverses the mapping. `IndexInitializer` (an `ApplicationRunner`) creates the `furniture` index at startup if it does not yet exist, using the mapping in `FURNITURE_MAPPING_JSON` (keyword `category`, text `name` with `keyword` sub-field, text `description` with the `english` analyzer, a `dense_vector` field of `EmbeddingVocabulary.size` dimensions with `similarity: cosine`, and nested `dimensions.{width,height,depth}` int+keyword pairs).
  - **Serialization**: `ProductSerializer` is constructed with the Spring-managed `ObjectMapper` and a `SemanticScorer`. `serialize(product)` calls `scorer.embedDocument(name, description)`, then collapses the result to `emptyList()` if every component is zero (otherwise `embedding.toList()`), builds a `ProductDocument`, and writes the JSON source for ES indexing. `ProductDocument.embedding` is also annotated `@JsonInclude(NON_EMPTY)` as a defensive backup so empty embedding lists are omitted from the JSON. The two layers together ensure out-of-vocab products serialize with no `embedding` field at all — an ES `dense_vector` with `similarity: cosine` rejects zero-magnitude vectors, so omitting the field is the only correct option for OOV products. (Note: `@JsonInclude(NON_EMPTY)` alone is insufficient because a 22-float list of zeros is non-empty by Jackson's definition; the explicit `if` collapse in `ProductSerializer` is what actually triggers the `NON_EMPTY` check.)
  - **Query building**: `ProductQueryBuilder.build(text, category, size)` produces a BM25 `multi_match` query over `name^3` and `description` with `fuzziness: AUTO`, optionally filtered by `category` (term filter) and capped at `size` results. `buildKnn(query, limit, minScore)` embeds the query through the scorer and returns `null` when the query embedding is all zeros (the only OOV case); the caller (`FurnitureRepository.semanticSearch`) short-circuits to `emptyList()`. Otherwise it builds a `NativeQuery` with `withKnnSearches(...)` (k = `limit`, num_candidates = `limit * 10`) and `withMinScore(minScore)`.
  - **Semantic scoring**: `SemanticScorer` is the port (`dimension` / `embedDocument(name, description)` / `embedQuery(query)`); `FrozenTfIdfSemanticScorer` is the production implementation. `EmbeddingVocabulary` (in `EmbeddingVocabulary.kt`) is the source of truth: it computes a frozen 22-stem vocabulary and an IDF map at class init from a hard-coded 7-doc seed corpus, so dimension and IDFs are deterministic. The same file holds the tokenization helpers (`normalize` does lowercase + non-alphanumeric split + stopword filter via `STOP_WORDS` + simple suffix-`s` stem (`stem`, gated by `MIN_STEMMABLE_LENGTH = 4`) + synonym map via `SYNONYMS`). Documents apply `NAME_BOOST = 3.0` to name tokens; queries do not. Tests can refer to `EmbeddingVocabulary.size` directly instead of injecting `SemanticScorer` just to read `dimension`.
  - **Search assembly**: `FurnitureSearchEngine` exposes both `search` (BM25) and `semanticSearch` (kNN cosine) on one port so `CatalogController` injects a single dependency for both endpoints. `semanticSearch` does not accept a `category` filter — the HTTP contract has no `category` parameter for that endpoint.

`application.yaml` sets `spring.application.name=calculator` and disables the Spring whitelabel error page so `GlobalExceptionHandler` is the sole error renderer. It also sets `spring.profiles.default=calculator,catalog` so both profiles are active when nothing else is configured — disabling one is done by setting `SPRING_PROFILES_ACTIVE` to the other.

## Code style rules

- Function bodies should not exceed 15 lines.

## Testing conventions

- **Kotest** (`FunSpec` style, runs on the JUnit Platform via `kotest-runner-junit5`). Use Kotest matchers (`shouldBe`, `shouldThrow`, etc.) for assertions.
- **Source set split**: unit tests live in `src/test/kotlin` and run under `./gradlew test`; integration tests live in `src/integrationTest/kotlin` and run under `./gradlew integrationTest`. Both are wired into `./gradlew check`. The `integrationTest` source set extends `testImplementation`/`testRuntimeOnly`, so Kotest, RestTestClient, and Spring Boot test starters are available without re-declaring them.
- **Naming**: both unit and integration test classes use the `*Test` suffix. Disambiguate by location (source set), not by suffix. One class per HTTP endpoint or behavioral concern (e.g. `RootPathTest`, `CalculatorEndpointTest`, `UnsupportedOperationsTest`) rather than one per controller — this keeps each spec narrowly focused and lets Spring's test context cache reuse the same `ApplicationContext` across all of them.
- Integration tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureRestTestClient` on the class, with `extension(SpringExtension)` inside the spec body and constructor-injected `@Autowired` dependencies (the `-Xannotation-default-target=param-property` flag puts `@Autowired` on both the param and the generated property). HTTP assertions go through `RestTestClient` (Spring Boot 4's new test client). See `CalculatorEndpointTest` for the pattern.
- Integration tests activate only the profile they need via a class-level `@ActiveProfiles(...)`. Tests that exercise the calculator endpoints (or `GET /` health) use `@ActiveProfiles("calculator")` so they do not pull in Elasticsearch autoconfig; tests that exercise `/products/**` or `IndexInitializer` use `@ActiveProfiles("catalog")`.
- Test names are Kotest `test("…")` / `context("…")` strings — sentence case, no backticks.
- Avoid code duplicates.
- Use Kotest `withData(...)` for table-driven tests; pass `nameFn = { … }` so reports show the input rather than `[1] …`. See `DefaultArithmeticExpressionCalculatorTest` for the pattern (it uses `nameFn` for both the success and failure tables). Where the row label is not the same as the input, name the rows after the property under test (e.g. `"synonym (couch -> sofa)"`) and keep the input as separate row data — see `TfIdfSemanticScorerTest`.
- Error-path tests in `DefaultArithmeticExpressionCalculatorTest` assert the full exception message string verbatim, so when you change a message you must update its test row in lockstep.
- Tests use `shouldNotBeNull()` (with `withClue("…")` to preserve message context) instead of `checkNotNull(...)`. The `checkNotNull` form is reserved for `src/main` code. For nullable-return assertions inside `RestTestClient` `.value { body -> … }` blocks, prefer chaining `body.shouldNotBeNull().run { … }` (multi-assertion blocks) or `body.shouldNotBeNull().<chain>` (single-assertion lines).

## TDD Rules
- Always use strict TDD (Red-Green-Refactor) for code changes.
- Write a failing test first.
- Implement only what is necessary to pass the test.
- Refactor after passing.

## Notes for future changes

- The `tmp/` directory is gitignored and should be treated as scratch space.
- `compose.yaml` exists only so `spring-boot-docker-compose` is happy; it currently declares `services: { }`.
