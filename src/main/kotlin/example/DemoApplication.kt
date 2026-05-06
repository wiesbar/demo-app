package example

import example.calculator.ArithmeticExpressionCalculator
import example.calculator.DefaultArithmeticExpressionCalculator
import example.catalog.FurnitureRepository
import example.catalog.ProductQueryBuilder
import example.catalog.ProductSerializer
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
    internal fun productSerializer(mapper: ObjectMapper) = ProductSerializer(mapper)

    @Bean
    internal fun productQueryBuilder() = ProductQueryBuilder()

    @Bean
    internal fun furnitureRepository(
        template: ElasticsearchOperations,
        productSerializer: ProductSerializer,
        productQueryBuilder: ProductQueryBuilder,
    ) = FurnitureRepository(template, productSerializer, productQueryBuilder)
}

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
