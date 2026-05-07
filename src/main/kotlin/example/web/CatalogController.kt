package example.web

import example.catalog.Category
import example.catalog.FurnitureIndexer
import example.catalog.FurnitureSearchEngine
import example.catalog.InvalidProductException
import example.catalog.ProductNotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CatalogController internal constructor(
    private val indexer: FurnitureIndexer,
    private val searchEngine: FurnitureSearchEngine,
) {
    @PutMapping("/products/{id}")
    suspend fun put(
        @PathVariable id: String,
        @RequestBody body: ProductDto,
    ): ResponseEntity<Void> {
        indexer.index(body.toDomain(id))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/products/{id}")
    suspend fun delete(
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        if (id.isBlank()) throw InvalidProductException("product id must not be blank")
        if (!indexer.deleteIfExists(id)) throw ProductNotFoundException(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/products/search")
    suspend fun search(
        @RequestParam q: String,
        @RequestParam(required = false) category: Category?,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): SearchResponseDto {
        validateQuery(q, size)
        val hits = searchEngine.search(q, category, size).map { it.toDto() }
        return SearchResponseDto(hits)
    }

    @PostMapping("/products/semantic-search")
    suspend fun semanticSearch(
        @RequestBody body: SemanticSearchRequest,
    ): SearchResponseDto {
        val limit = body.limit ?: DEFAULT_SEMANTIC_LIMIT
        val minScore = body.minScore ?: 0.0
        validateSemantic(body.query, limit, minScore)
        val hits = searchEngine.semanticSearch(body.query, limit, minScore).map { it.toDto() }
        return SearchResponseDto(hits)
    }
}

private fun validateQuery(
    q: String,
    size: Int,
) {
    if (q.isBlank()) throw InvalidProductException("query 'q' must not be blank")
    if (size !in 1..MAX_SEARCH_SIZE) {
        throw InvalidProductException("query 'size' must be in 1..$MAX_SEARCH_SIZE, got $size")
    }
}

private fun validateSemantic(
    query: String,
    limit: Int,
    minScore: Double,
) {
    semanticError(query, limit, minScore)?.let {
        throw InvalidProductException(it)
    }
}

private fun semanticError(
    query: String,
    limit: Int,
    minScore: Double,
): String? =
    when {
        query.isBlank() -> "query 'query' must not be blank"
        limit !in 1..MAX_SEARCH_SIZE -> "query 'limit' must be in 1..$MAX_SEARCH_SIZE, got $limit"
        minScore !in 0.0..1.0 -> "query 'minScore' must be in 0.0..1.0, got $minScore"
        else -> null
    }

private const val MAX_SEARCH_SIZE = 100
private const val DEFAULT_SEMANTIC_LIMIT = 10
