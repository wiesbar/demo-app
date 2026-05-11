package example.config

import example.calculator.ArithmeticExpressionCalculator
import example.calculator.DefaultArithmeticExpressionCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("calculator")
internal class CalculatorConfig {
    @Bean
    internal fun calculator(): ArithmeticExpressionCalculator = DefaultArithmeticExpressionCalculator()
}
