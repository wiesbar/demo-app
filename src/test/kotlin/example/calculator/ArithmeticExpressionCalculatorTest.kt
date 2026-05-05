package example.calculator

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class ArithmeticExpressionCalculatorTest :
    FunSpec({
        val calculator: ArithmeticExpressionCalculator = DefaultArithmeticExpressionCalculator()

        context("should evaluate valid expression") {
            withData(
                nameFn = { (expression, _) -> expression },
                listOf(
                    "1 + 2" to 3.0,
                    "5 - 3" to 2.0,
                    "1.5 + 2.25" to 3.75,
                    "1 + 2 + 3" to 6.0,
                    "10 - 3 - 2" to 5.0,
                    "1 - 2 + 3" to 2.0,
                    "-1 + 2" to 1.0,
                    "1 - -2" to 3.0,
                    "(1 + 2) - 3" to 0.0,
                    "1 - (2 - 3)" to 2.0,
                    "((1))" to 1.0,
                    "-(1 + 2)" to -3.0,
                    "1 + -(2 - 3)" to 2.0,
                    "  7+8  " to 15.0,
                    "1" to 1.0,
                    "(42.5)" to 42.5,
                    "-3" to -3.0,
                    ".5 + 1" to 1.5,
                    "(-1 + 2)" to 1.0,
                    "--1" to 1.0,
                    "2 * 3" to 6.0,
                    "2 + 3 * 4" to 14.0,
                    "2 * 3 + 4" to 10.0,
                    "(2 + 3) * 4" to 20.0,
                    "2 * 3 * 4" to 24.0,
                    "-2 * 3" to -6.0,
                    "2 * -3" to -6.0,
                ),
            ) { (expression, expected) ->
                calculator.calculate(expression) shouldBe expected
            }
        }

        context("should reject invalid expression") {
            withData(
                nameFn = { (expression, _) -> expression.ifBlank { "<blank>" } },
                listOf(
                    "" to "Invalid empty expression.",
                    "1 +" to "Missing operand for '+' operator at position '2' in expression.",
                    "1 + -" to "Missing operand for '+' operator at position '2' in expression.",
                    "-" to "Missing operand for '-' operator at position '0' in expression.",
                    "*" to "Missing operand for '*' operator at position '0' in expression.",
                    "+" to "Missing operand for '+' operator at position '0' in expression.",
                    "+3 - 1" to "Missing operand for '+' operator at position '0' in expression.",
                    "1 + 2 3" to "Unexpected operand at position '6' in expression.",
                    "a + b" to "Invalid character 'a' at position '0' in expression.",
                    "(1 + 2" to "Unmatched '(' at position '0' in expression.",
                    "1 + 2)" to "Unmatched ')' at position '5' in expression.",
                    "()" to "Empty parentheses at position '0' in expression.",
                    ")" to "Unmatched ')' at position '0' in expression.",
                    "1..2" to "Invalid number literal '1..2' at position '0' in expression.",
                ),
            ) { (expression, expectedMessage) ->
                shouldThrow<InvalidArithmeticExpressionException> {
                    calculator.calculate(expression)
                }.run {
                    message shouldBe expectedMessage
                    cause shouldBe null
                }
            }
        }
    })
