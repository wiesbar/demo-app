package example.catalog

import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.data.elasticsearch.core.search

internal const val FURNITURE_INDEX = "furniture"

internal class FurnitureRepository(
    private val template: ElasticsearchOperations,
    private val productSerializer: ProductSerializer,
    private val queryBuilder: ProductQueryBuilder,
) : FurnitureSearchEngine,
    FurnitureIndexer {
    private val coordinates: IndexCoordinates = IndexCoordinates.of(FURNITURE_INDEX)

    override fun index(product: Product) {
        val source = productSerializer.serialize(product)
        val query = IndexQueryBuilder().withId(product.id).withSource(source).build()
        template.index(query, coordinates)
    }

    override fun deleteIfExists(id: String): Boolean {
        if (!template.exists(id, coordinates)) return false
        template.delete(id, coordinates)
        return true
    }

    override fun search(
        query: String,
        category: Category?,
        size: Int,
    ): List<SearchHit> {
        val nativeQuery = queryBuilder.build(query, category, size)
        val hits = template.search<ProductDocument>(nativeQuery, coordinates)
        return hits.searchHits.map { hit ->
            val id = checkNotNull(hit.id) { "search hit missing id" }
            SearchHit(hit.content.toDomain(id), hit.score.toDouble())
        }
    }
}
