package example.catalog

import co.elastic.clients.elasticsearch._types.query_dsl.Query
import org.springframework.data.elasticsearch.client.elc.NativeQuery

internal class ProductQueryBuilder {
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
