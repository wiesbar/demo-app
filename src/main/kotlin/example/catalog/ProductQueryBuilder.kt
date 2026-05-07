package example.catalog

import co.elastic.clients.elasticsearch._types.KnnSearch
import co.elastic.clients.elasticsearch._types.query_dsl.Query
import org.springframework.data.elasticsearch.client.elc.NativeQuery

private const val KNN_NUM_CANDIDATES_MULTIPLIER = 10

internal class ProductQueryBuilder(
    private val scorer: SemanticScorer,
) {
    fun build(
        text: String,
        category: Category?,
        size: Int,
    ): NativeQuery =
        NativeQuery
            .builder()
            .withQuery(boolQuery(text, category))
            .withMaxResults(size)
            .build()

    fun buildKnn(
        query: String,
        limit: Int,
        minScore: Double,
    ): NativeQuery? {
        val qv = scorer.embedQuery(query).toList()
        if (qv.all { it == 0.0f }) return null
        return NativeQuery
            .builder()
            .withKnnSearches(knn(qv, limit))
            .withMaxResults(limit)
            .withMinScore(minScore.toFloat())
            .build()
    }

    private fun knn(
        queryVector: List<Float>,
        limit: Int,
    ): KnnSearch =
        KnnSearch.of { ks ->
            ks
                .field("embedding")
                .queryVector(queryVector)
                .k(limit)
                .numCandidates(limit * KNN_NUM_CANDIDATES_MULTIPLIER)
        }

    private fun boolQuery(
        text: String,
        category: Category?,
    ): Query =
        Query.of { q ->
            q.bool { b ->
                b.must(multiMatch(text))
                category?.let { b.filter(categoryFilter(it)) }
                b
            }
        }

    private fun multiMatch(text: String): Query =
        Query.of { q ->
            q.multiMatch { mm ->
                mm.query(text).fields("name^3", "description").fuzziness("AUTO")
            }
        }

    private fun categoryFilter(category: Category): Query =
        Query.of { q ->
            q.term { t -> t.field("category").value(category.name) }
        }
}
