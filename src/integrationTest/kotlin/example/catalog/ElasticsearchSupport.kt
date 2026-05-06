package example.catalog

import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.test.context.DynamicPropertyRegistry

internal fun resetFurnitureIndex(template: ElasticsearchOperations) {
    val ops = template.indexOps(IndexCoordinates.of(FURNITURE_INDEX))
    if (ops.exists()) ops.delete()
    ops.create(emptyMap(), Document.parse(FURNITURE_MAPPING_JSON))
}

internal fun registerElasticsearchUri(registry: DynamicPropertyRegistry) {
    registry.add("spring.elasticsearch.uris") {
        "http://${ElasticsearchTestContainer.instance.httpHostAddress}"
    }
}
