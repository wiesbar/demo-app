package example.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.data.elasticsearch.core.search

internal const val FURNITURE_INDEX = "furniture"

internal class FurnitureRepository(
    private val template: ElasticsearchOperations,
    private val productSerializer: ProductSerializer,
    private val queryBuilder: ProductQueryBuilder,
    private val dispatcher: CoroutineDispatcher,
) : FurnitureSearchEngine,
    FurnitureIndexer {
    private val coordinates: IndexCoordinates = IndexCoordinates.of(FURNITURE_INDEX)

    override suspend fun index(product: Product) =
        withContext(dispatcher) {
            val source = productSerializer.serialize(product)
            val query = IndexQueryBuilder().withId(product.id).withSource(source).build()
            template.index(query, coordinates)
            Unit
        }

    override suspend fun deleteIfExists(id: String): Boolean =
        withContext(dispatcher) {
            if (!template.exists(id, coordinates)) return@withContext false
            template.delete(id, coordinates)
            true
        }

    override suspend fun search(
        query: String,
        category: Category?,
        size: Int,
    ): List<SearchHit> =
        withContext(dispatcher) {
            val nativeQuery = queryBuilder.build(query, category, size)
            val hits = template.search<ProductDocument>(nativeQuery, coordinates)
            hits.searchHits.map { hit ->
                val id = checkNotNull(hit.id) { "search hit missing id" }
                SearchHit(hit.content.toDomain(id), hit.score.toDouble())
            }
        }

    // todo - missing feature
    // Category filtering is not supported through this path; the HTTP contract
    // exposes no `category` parameter for semantic search.
    override suspend fun semanticSearch(
        query: String,
        limit: Int,
        minScore: Double,
    ): List<SearchHit> =
        withContext(dispatcher) {
            val nq = queryBuilder.buildKnn(query, limit, minScore) ?: return@withContext emptyList()
            val hits = template.search<ProductDocument>(nq, coordinates)
            hits.searchHits.map { hit ->
                val id = checkNotNull(hit.id) { "search hit missing id" }
                SearchHit(hit.content.toDomain(id), hit.score.toDouble())
            }
        }
}
