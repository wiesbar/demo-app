package example.config

import example.catalog.FrozenTfIdfSemanticScorer
import example.catalog.FurnitureRepository
import example.catalog.IndexInitializer
import example.catalog.ProductQueryBuilder
import example.catalog.ProductSerializer
import example.catalog.SemanticScorer
import kotlinx.coroutines.Dispatchers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import tools.jackson.databind.ObjectMapper

@Configuration
@Profile("catalog")
internal class CatalogConfig {
    @Bean
    internal fun semanticScorer(): SemanticScorer = FrozenTfIdfSemanticScorer()

    @Bean
    internal fun productSerializer(
        mapper: ObjectMapper,
        scorer: SemanticScorer,
    ) = ProductSerializer(mapper, scorer)

    @Bean
    internal fun productQueryBuilder(scorer: SemanticScorer) = ProductQueryBuilder(scorer)

    @Bean
    internal fun furnitureRepository(
        template: ElasticsearchOperations,
        productSerializer: ProductSerializer,
        productQueryBuilder: ProductQueryBuilder,
    ) = FurnitureRepository(template, productSerializer, productQueryBuilder, Dispatchers.IO)

    @Bean
    internal fun indexInitializer(template: ElasticsearchOperations) = IndexInitializer(template)
}
