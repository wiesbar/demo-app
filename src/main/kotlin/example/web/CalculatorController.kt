package example.web

import example.calculator.ArithmeticExpressionCalculator
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Profile("calculator")
class CalculatorController internal constructor(
    private val calculator: ArithmeticExpressionCalculator,
) {
    @PostMapping("/calculate")
    fun calculate(
        @RequestBody expression: String,
    ): String = calculator.calculate(expression).toString()
}
