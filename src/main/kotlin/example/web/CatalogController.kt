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
        return SearchResponseDto(hits, hits.size)
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

private const val MAX_SEARCH_SIZE = 100
