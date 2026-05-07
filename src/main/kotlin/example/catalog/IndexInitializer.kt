package example.catalog

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.stereotype.Component

@Component
internal class IndexInitializer(
    private val template: ElasticsearchOperations,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val ops = template.indexOps(IndexCoordinates.of(FURNITURE_INDEX))
        if (!ops.exists()) {
            ops.create(emptyMap(), Document.parse(FURNITURE_MAPPING_JSON))
        }
    }
}

internal val FURNITURE_MAPPING_JSON: String =
    """
    {
      "properties": {
        "category":    { "type": "keyword" },
        "name":        { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
        "description": { "type": "text", "analyzer": "english" },
        "embedding":   { "type": "dense_vector", "dims": ${EmbeddingVocabulary.size}, "similarity": "cosine" },
        "dimensions": {
          "properties": {
            "width":  { "properties": { "value": { "type": "integer" }, "unit": { "type": "keyword" } } },
            "height": { "properties": { "value": { "type": "integer" }, "unit": { "type": "keyword" } } },
            "depth":  { "properties": { "value": { "type": "integer" }, "unit": { "type": "keyword" } } }
          }
        }
      }
    }
    """.trimIndent()
