package example.calculator

import example.calculator.BinaryOperator.Plus
import example.calculator.BinaryOperator.Times

internal fun toInfix(expression: String): List<InfixToken> = InfixParser(expression).parse()

private class InfixParser(
    expression: String,
) {
    private val cursor = Cursor(expression)
    private val tokens = mutableListOf<InfixToken>()

    fun parse(): List<InfixToken> {
        while (cursor.hasNextChar()) {
            readNext()
        }
        return tokens.toList()
    }

    private fun readNext() {
        when {
            cursor.isWhitespace() -> cursor.advance()
            cursor.isLeftParen() -> consume(Parenthesis.Left(cursor.index))
            cursor.isRightParen() -> consume(Parenthesis.Right(cursor.index))
            cursor.isTimes() -> consume(Times(cursor.index))
            cursor.isPlus() -> consume(Plus(cursor.index))
            cursor.isMinus() -> readMinus()
            cursor.isDigitOrPoint() -> readNumber()
            else -> throw InvalidArithmeticExpressionException(
                "Invalid character '${cursor.currentChar}' at position '${cursor.index}' in expression.",
            )
        }
    }

    private fun consume(token: InfixToken) {
        tokens += token
        cursor.advance()
    }

    private fun readMinus() {
        consume(
            token =
                if (isAtUnaryPosition()) {
                    UnaryOperator.Negate(cursor.index)
                } else {
                    BinaryOperator.Minus(cursor.index)
                },
        )
    }

    private fun isAtUnaryPosition(): Boolean {
        val previous = tokens.lastOrNull()
        return previous == null || previous is Operator || previous is Parenthesis.Left
    }

    private fun readNumber() {
        val start = cursor.index
        while (cursor.hasNextChar() && cursor.isDigitOrPoint()) {
            cursor.advance()
        }
        val literal = cursor.substring(start)
        val value =
            literal.toDoubleOrNull()
                ?: throw InvalidArithmeticExpressionException(
                    "Invalid number literal '$literal' at position '$start' in expression.",
                )
        tokens += Operand(value, start)
    }
}

private class Cursor(
    private val expression: String,
) {
    var index: Int = 0
        private set

    fun advance() {
        index++
    }

    val currentChar: Char get() = expression[index]

    fun hasNextChar(): Boolean = index < expression.length

    fun substring(start: Int): String = expression.substring(start, index)

    fun isWhitespace(): Boolean = currentChar.isWhitespace()

    fun isLeftParen(): Boolean = currentChar == '('

    fun isRightParen(): Boolean = currentChar == ')'

    fun isTimes(): Boolean = currentChar == '*'

    fun isPlus(): Boolean = currentChar == '+'

    fun isMinus(): Boolean = currentChar == '-'

    fun isDigitOrPoint(): Boolean = currentChar.isDigitOrPoint()
}

private fun Char.isDigitOrPoint() = isDigit() || this == '.'
