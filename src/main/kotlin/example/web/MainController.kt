package example.web

import example.calculator.ArithmeticExpressionCalculator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class MainController internal constructor(
    private val calculator: ArithmeticExpressionCalculator,
) {
    @GetMapping("/")
    @Suppress("FunctionOnlyReturningConstant")
    fun index(): String = "The Demo Service is running!"

    @PostMapping("/calculate")
    fun calculate(
        @RequestBody expression: String,
    ): String = calculator.calculate(expression).toString()
}
