package example

import example.calculator.ArithmeticExpressionCalculator
import example.calculator.DefaultArithmeticExpressionCalculator
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class DemoApplication {
    @Bean
    internal fun calculator(): ArithmeticExpressionCalculator = DefaultArithmeticExpressionCalculator()
}

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}
