package example.calculator

internal fun interface ArithmeticExpressionCalculator {
    fun calculate(expression: String): Double
}

internal class DefaultArithmeticExpressionCalculator : ArithmeticExpressionCalculator {
    override fun calculate(expression: String): Double = toInfix(expression).toPostfix().calculate()
}
