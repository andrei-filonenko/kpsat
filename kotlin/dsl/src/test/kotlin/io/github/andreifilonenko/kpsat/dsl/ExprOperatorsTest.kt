package io.github.andreifilonenko.kpsat.dsl

import arrow.core.getOrElse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for all DSL operators on Expr.
 * Uses DirectEvaluator to verify operator semantics.
 */
class ExprOperatorsTest {

    private val evaluator = DirectEvaluator()

    // Helper to create a variable expression and set its value
    private fun varExpr(id: Int, value: Long): Expr {
        evaluator.setVar(id, value)
        return Expr(ExprNode.Var(id, VarType.INT))
    }

    // Helper to create a constant expression
    private fun constExpr(value: Long): Expr = Expr(ExprNode.Const(value))
    private fun constExpr(value: Double): Expr = Expr(ExprNode.ConstDouble(value))

    // Helper extension functions for tests
    private fun DirectEvaluator.evalLong(expr: Expr): Long = evaluateLong(expr).getOrElse { fail(it.message) }
    private fun DirectEvaluator.evalDouble(expr: Expr): Double = evaluateDouble(expr).getOrElse { fail(it.message) }

    @Nested
    inner class ArithmeticOperators {

        @Test
        fun `Expr plus Expr`() {
            val x = varExpr(0, 10)
            val y = varExpr(1, 5)
            val result = x + y
            assertEquals(15L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr plus Long`() {
            val x = varExpr(0, 10)
            val result = x + 7L
            assertEquals(17L, evaluator.evalLong(result))
        }

        @Test
        fun `Long plus Expr`() {
            val x = varExpr(0, 10)
            val result = 3L + x
            assertEquals(13L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr plus Double`() {
            val x = varExpr(0, 10)
            val result = x + 2.5
            assertEquals(12.5, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `Double plus Expr`() {
            val x = varExpr(0, 10)
            val result = 1.5 + x
            assertEquals(11.5, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `Expr minus Expr`() {
            val x = varExpr(0, 10)
            val y = varExpr(1, 3)
            val result = x - y
            assertEquals(7L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr minus Long`() {
            val x = varExpr(0, 10)
            val result = x - 4L
            assertEquals(6L, evaluator.evalLong(result))
        }

        @Test
        fun `Long minus Expr`() {
            val x = varExpr(0, 3)
            val result = 10L - x
            assertEquals(7L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr times Expr`() {
            val x = varExpr(0, 4)
            val y = varExpr(1, 5)
            val result = x * y
            assertEquals(20L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr times Long`() {
            val x = varExpr(0, 6)
            val result = x * 7L
            assertEquals(42L, evaluator.evalLong(result))
        }

        @Test
        fun `Long times Expr`() {
            val x = varExpr(0, 8)
            val result = 3L * x
            assertEquals(24L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr times Double`() {
            val x = varExpr(0, 4)
            val result = x * 2.5
            assertEquals(10.0, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `Double times Expr`() {
            val x = varExpr(0, 5)
            val result = 1.5 * x
            assertEquals(7.5, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `Expr div Expr`() {
            val x = varExpr(0, 20)
            val y = varExpr(1, 4)
            val result = x / y
            assertEquals(5L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr div Long`() {
            val x = varExpr(0, 15)
            val result = x / 3L
            assertEquals(5L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr rem Expr`() {
            val x = varExpr(0, 17)
            val y = varExpr(1, 5)
            val result = x % y
            assertEquals(2L, evaluator.evalLong(result))
        }

        @Test
        fun `Expr rem Long`() {
            val x = varExpr(0, 23)
            val result = x % 7L
            assertEquals(2L, evaluator.evalLong(result))
        }

        @Test
        fun `unary minus`() {
            val x = varExpr(0, 42)
            val result = -x
            assertEquals(-42L, evaluator.evalLong(result))
        }

        @Test
        fun `unary minus on negative`() {
            val x = varExpr(0, -10)
            val result = -x
            assertEquals(10L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class ComparisonOperators {

        @Test
        fun `eq returns true when equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 5)
            val result = x eq y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `eq returns false when not equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 6)
            val result = x eq y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `eq with Long`() {
            val x = varExpr(0, 42)
            val result = x eq 42L
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `neq returns true when not equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 6)
            val result = x neq y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `neq returns false when equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 5)
            val result = x neq y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `neq with Long`() {
            val x = varExpr(0, 5)
            val result = x neq 10L
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `leq returns true when less`() {
            val x = varExpr(0, 3)
            val y = varExpr(1, 5)
            val result = x leq y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `leq returns true when equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 5)
            val result = x leq y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `leq returns false when greater`() {
            val x = varExpr(0, 7)
            val y = varExpr(1, 5)
            val result = x leq y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `leq with Long`() {
            val x = varExpr(0, 5)
            val result = x leq 10L
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `leq with Double`() {
            val x = varExpr(0, 5)
            val result = x leq 5.5
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `geq returns true when greater`() {
            val x = varExpr(0, 7)
            val y = varExpr(1, 5)
            val result = x geq y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `geq returns true when equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 5)
            val result = x geq y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `geq returns false when less`() {
            val x = varExpr(0, 3)
            val y = varExpr(1, 5)
            val result = x geq y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `lt returns true when less`() {
            val x = varExpr(0, 3)
            val y = varExpr(1, 5)
            val result = x lt y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `lt returns false when equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 5)
            val result = x lt y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `gt returns true when greater`() {
            val x = varExpr(0, 7)
            val y = varExpr(1, 5)
            val result = x gt y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `gt returns false when equal`() {
            val x = varExpr(0, 5)
            val y = varExpr(1, 5)
            val result = x gt y
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class LogicalOperators {

        @Test
        fun `and returns true when both true`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 1)
            val result = x and y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `and returns false when first false`() {
            val x = varExpr(0, 0)
            val y = varExpr(1, 1)
            val result = x and y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `and returns false when second false`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 0)
            val result = x and y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `and returns false when both false`() {
            val x = varExpr(0, 0)
            val y = varExpr(1, 0)
            val result = x and y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `or returns true when both true`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 1)
            val result = x or y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `or returns true when first true`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 0)
            val result = x or y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `or returns true when second true`() {
            val x = varExpr(0, 0)
            val y = varExpr(1, 1)
            val result = x or y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `or returns false when both false`() {
            val x = varExpr(0, 0)
            val y = varExpr(1, 0)
            val result = x or y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `not returns true for false`() {
            val x = varExpr(0, 0)
            val result = !x
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `not returns false for true`() {
            val x = varExpr(0, 1)
            val result = !x
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `xor returns true when exactly one true`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 0)
            val result = x xor y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `xor returns false when both true`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 1)
            val result = x xor y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `xor returns false when both false`() {
            val x = varExpr(0, 0)
            val y = varExpr(1, 0)
            val result = x xor y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `implies returns true when antecedent false`() {
            val x = varExpr(0, 0)
            val y = varExpr(1, 0)
            val result = x implies y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `implies returns true when both true`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 1)
            val result = x implies y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `implies returns false when antecedent true and consequent false`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 0)
            val result = x implies y
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `iff returns true when both true`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 1)
            val result = x iff y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `iff returns true when both false`() {
            val x = varExpr(0, 0)
            val y = varExpr(1, 0)
            val result = x iff y
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `iff returns false when different`() {
            val x = varExpr(0, 1)
            val y = varExpr(1, 0)
            val result = x iff y
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class ConditionalOperators {

        @Test
        fun `iif returns ifTrue when condition true`() {
            val cond = varExpr(0, 1)
            val result = iif(cond, constExpr(10), constExpr(20))
            assertEquals(10L, evaluator.evalLong(result))
        }

        @Test
        fun `iif returns ifFalse when condition false`() {
            val cond = varExpr(0, 0)
            val result = iif(cond, constExpr(10), constExpr(20))
            assertEquals(20L, evaluator.evalLong(result))
        }

        @Test
        fun `iif with Long values`() {
            val cond = varExpr(0, 1)
            val result = iif(cond, 100L, 200L)
            assertEquals(100L, evaluator.evalLong(result))
        }

        @Test
        fun `iif with mixed Expr and Long`() {
            val cond = varExpr(0, 1)
            val x = varExpr(1, 50)
            val result = iif(cond, x, 100L)
            assertEquals(50L, evaluator.evalLong(result))
        }

        @Test
        fun `iif with Double values`() {
            val cond = varExpr(0, 1)
            val result = iif(cond, 1.5, 2.5)
            assertEquals(1.5, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `then otherwise syntax returns ifTrue when true`() {
            val cond = varExpr(0, 1)
            val x = varExpr(1, 42)
            val result = cond then x otherwise constExpr(0)
            assertEquals(42L, evaluator.evalLong(result))
        }

        @Test
        fun `then otherwise syntax returns ifFalse when false`() {
            val cond = varExpr(0, 0)
            val x = varExpr(1, 42)
            val result = cond then x otherwise constExpr(99)
            assertEquals(99L, evaluator.evalLong(result))
        }

        @Test
        fun `then otherwise with Long values`() {
            val cond = varExpr(0, 1)
            val result = cond then 100L otherwise 200L
            assertEquals(100L, evaluator.evalLong(result))
        }

        @Test
        fun `onlyIf returns value when condition true`() {
            val x = varExpr(0, 50)
            val cond = varExpr(1, 1)
            val result = x onlyIf cond
            assertEquals(50L, evaluator.evalLong(result))
        }

        @Test
        fun `onlyIf returns zero when condition false`() {
            val x = varExpr(0, 50)
            val cond = varExpr(1, 0)
            val result = x onlyIf cond
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `Long onlyIf returns value when condition true`() {
            val cond = varExpr(0, 1)
            val result = 100L onlyIf cond
            assertEquals(100L, evaluator.evalLong(result))
        }

        @Test
        fun `Long onlyIf returns zero when condition false`() {
            val cond = varExpr(0, 0)
            val result = 100L onlyIf cond
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class MathFunctions {

        @Test
        fun `abs of positive number`() {
            val x = varExpr(0, 5)
            val result = abs(x)
            assertEquals(5L, evaluator.evalLong(result))
        }

        @Test
        fun `abs of negative number`() {
            val x = varExpr(0, -7)
            val result = abs(x)
            assertEquals(7L, evaluator.evalLong(result))
        }

        @Test
        fun `abs of zero`() {
            val x = varExpr(0, 0)
            val result = abs(x)
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `sqrt of perfect square`() {
            val x = constExpr(16.0)
            val result = sqrt(x)
            assertEquals(4.0, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `sqrt of non-perfect square`() {
            val x = constExpr(2.0)
            val result = sqrt(x)
            assertEquals(1.414, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `pow with Expr exponent`() {
            val base = varExpr(0, 2)
            val exp = varExpr(1, 10)
            val result = pow(base, exp)
            assertEquals(1024.0, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `pow with Long exponent`() {
            val base = varExpr(0, 3)
            val result = pow(base, 4L)
            assertEquals(81.0, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `pow with Double exponent`() {
            val base = varExpr(0, 4)
            val result = pow(base, 0.5)
            assertEquals(2.0, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `exp function`() {
            val x = constExpr(1.0)
            val result = exp(x)
            assertEquals(2.718, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `log function`() {
            val x = constExpr(kotlin.math.E)
            val result = log(x)
            assertEquals(1.0, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `ceil function`() {
            val x = constExpr(1.3)
            val result = ceil(x)
            assertEquals(2L, evaluator.evalLong(result))
        }

        @Test
        fun `floor function`() {
            val x = constExpr(1.9)
            val result = floor(x)
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `round function rounds down`() {
            val x = constExpr(1.4)
            val result = round(x)
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `round function rounds up`() {
            val x = constExpr(1.6)
            val result = round(x)
            assertEquals(2L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class CollectionOperators {

        @Test
        fun `containsElem with Long element that exists`() {
            evaluator.setList(0, listOf(1L, 2L, 3L, 4L, 5L))
            val arr = Expr(ExprNode.Var(0, VarType.LIST))
            val result = arr.containsElem(3L)
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `containsElem with Long element that does not exist`() {
            evaluator.setList(0, listOf(1L, 2L, 3L, 4L, 5L))
            val arr = Expr(ExprNode.Var(0, VarType.LIST))
            val result = arr.containsElem(10L)
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `isIn operator with Long`() {
            evaluator.setList(0, listOf(10L, 20L, 30L))
            val arr = Expr(ExprNode.Var(0, VarType.LIST))
            val result = 20L isIn arr
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `array indexing with Expr`() {
            val arr = Expr(ExprNode.ArrayLiteral(longArrayOf(100, 200, 300)))
            val idx = varExpr(0, 1)
            val result = arr[idx]
            assertEquals(200L, evaluator.evalLong(result))
        }

        @Test
        fun `array indexing with Long`() {
            val arr = Expr(ExprNode.ArrayLiteral(longArrayOf(100, 200, 300)))
            val result = arr[2L]
            assertEquals(300L, evaluator.evalLong(result))
        }

        @Test
        fun `array indexing with Int`() {
            val arr = Expr(ExprNode.ArrayLiteral(longArrayOf(10, 20, 30, 40)))
            val result = arr[0]
            assertEquals(10L, evaluator.evalLong(result))
        }

        @Test
        fun `count on list`() {
            evaluator.setList(0, listOf(1L, 2L, 3L, 4L, 5L))
            val arr = Expr(ExprNode.Var(0, VarType.LIST))
            val result = arr.count()
            assertEquals(5L, evaluator.evalLong(result))
        }

        @Test
        fun `card on set`() {
            evaluator.setSet(0, setOf(1L, 2L, 3L))
            val set = Expr(ExprNode.Var(0, VarType.SET))
            val result = set.card()
            assertEquals(3L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class SetOperators {

        @Test
        fun `intersect two sets`() {
            evaluator.setSet(0, setOf(1L, 2L, 3L))
            evaluator.setSet(1, setOf(2L, 3L, 4L))
            val set1 = Expr(ExprNode.Var(0, VarType.SET))
            val set2 = Expr(ExprNode.Var(1, VarType.SET))
            val result = set1 intersect set2
            val resultSet = evaluator.evaluate(result).getOrElse { fail(it.message) } as Set<*>
            assertEquals(setOf(2L, 3L), resultSet)
        }

        @Test
        fun `union two sets`() {
            evaluator.setSet(0, setOf(1L, 2L))
            evaluator.setSet(1, setOf(3L, 4L))
            val set1 = Expr(ExprNode.Var(0, VarType.SET))
            val set2 = Expr(ExprNode.Var(1, VarType.SET))
            val result = set1 union set2
            val resultSet = evaluator.evaluate(result).getOrElse { fail(it.message) } as Set<*>
            assertEquals(setOf(1L, 2L, 3L, 4L), resultSet)
        }

        @Test
        fun `disjoint returns true when sets have no common elements`() {
            evaluator.setSet(0, setOf(1L, 2L))
            evaluator.setSet(1, setOf(3L, 4L))
            val set1 = Expr(ExprNode.Var(0, VarType.SET))
            val set2 = Expr(ExprNode.Var(1, VarType.SET))
            val result = set1 disjoint set2
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `disjoint returns false when sets have common elements`() {
            evaluator.setSet(0, setOf(1L, 2L, 3L))
            evaluator.setSet(1, setOf(3L, 4L, 5L))
            val set1 = Expr(ExprNode.Var(0, VarType.SET))
            val set2 = Expr(ExprNode.Var(1, VarType.SET))
            val result = set1 disjoint set2
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class DomainOperators {

        @Test
        fun `inDomain returns true when value in domain`() {
            val x = varExpr(0, 5)
            val result = x inDomain longArrayOf(1, 3, 5, 7, 9)
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `inDomain returns false when value not in domain`() {
            val x = varExpr(0, 4)
            val result = x inDomain longArrayOf(1, 3, 5, 7, 9)
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class FactoryFunctions {

        @Test
        fun `expr from Long creates Const node`() {
            val e = expr(42L)
            assertTrue(e.node is ExprNode.Const)
            assertEquals(42L, (e.node as ExprNode.Const).value)
        }

        @Test
        fun `expr from Double creates ConstDouble node`() {
            val e = expr(3.14)
            assertTrue(e.node is ExprNode.ConstDouble)
            assertEquals(3.14, (e.node as ExprNode.ConstDouble).value)
        }

        @Test
        fun `arrayOf creates ArrayOf node`() {
            val arr = arrayOf(listOf(expr(1L), expr(2L), expr(3L)))
            assertTrue(arr.node is ExprNode.ArrayOf)
            assertEquals(3, (arr.node as ExprNode.ArrayOf).elements.size)
        }
    }

    @Nested
    inner class RangeHelpers {

        @Test
        fun `range with Int creates IntRange`() {
            val r = range(5)
            assertEquals(0 until 5, r)
        }

        @Test
        fun `range with Long creates LongRange`() {
            val r = range(5L)
            assertEquals(0L until 5L, r)
        }

        @Test
        fun `range with start and end Int creates IntRange`() {
            val r = range(2, 7)
            assertEquals(2 until 7, r)
        }

        @Test
        fun `range with start and end Long creates LongRange`() {
            val r = range(10L, 20L)
            assertEquals(10L until 20L, r)
        }
    }
}

