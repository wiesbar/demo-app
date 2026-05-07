package example

import example.calculator.ArithmeticExpressionCalculator
import example.calculator.DefaultArithmeticExpressionCalculator
import example.catalog.FrozenTfIdfSemanticScorer
import example.catalog.FurnitureRepository
import example.catalog.ProductQueryBuilder
import example.catalog.ProductSerializer
import example.catalog.SemanticScorer
import kotlinx.coroutines.Dispatchers
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import tools.jackson.databind.ObjectMapper

@SpringBootApplication
class DemoApplication {
    @Bean
    internal fun calculator(): ArithmeticExpressionCalculator = DefaultArithmeticExpressionCalculator()

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
    internal fun semanticScorer(): SemanticScorer = FrozenTfIdfSemanticScorer()
}

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
