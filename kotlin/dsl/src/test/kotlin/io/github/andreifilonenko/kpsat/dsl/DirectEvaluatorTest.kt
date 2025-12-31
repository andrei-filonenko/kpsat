package io.github.andreifilonenko.kpsat.dsl

import arrow.core.getOrElse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * Comprehensive tests for DirectEvaluator.
 */
class DirectEvaluatorTest {

    private lateinit var evaluator: DirectEvaluator

    @BeforeEach
    fun setUp() {
        evaluator = DirectEvaluator()
    }

    private fun intVar(id: Int, value: Long): Expr {
        evaluator.setVar(id, value)
        return Expr(ExprNode.Var(id, VarType.INT))
    }

    private fun boolVar(id: Int, value: Boolean): Expr {
        evaluator.setVar(id, value)
        return Expr(ExprNode.Var(id, VarType.BOOL))
    }

    private fun doubleVar(id: Int, value: Double): Expr {
        evaluator.setVar(id, value)
        return Expr(ExprNode.Var(id, VarType.FLOAT))
    }

    private fun DirectEvaluator.evalLong(expr: Expr): Long = evaluateLong(expr).getOrElse { fail(it.message) }
    private fun DirectEvaluator.evalDouble(expr: Expr): Double = evaluateDouble(expr).getOrElse { fail(it.message) }
    private fun DirectEvaluator.evalBool(expr: Expr): Boolean = evaluateBool(expr).getOrElse { fail(it.message) }
    private fun DirectEvaluator.eval(expr: Expr): Any = evaluate(expr).getOrElse { fail(it.message) }

    @Nested
    inner class BasicEvaluation {

        @Test
        fun `evaluate constant Long`() {
            val expr = Expr(ExprNode.Const(42L))
            assertEquals(42L, evaluator.evalLong(expr))
        }

        @Test
        fun `evaluate constant Double`() {
            val expr = Expr(ExprNode.ConstDouble(3.14))
            assertEquals(3.14, evaluator.evalDouble(expr), 0.001)
        }

        @Test
        fun `evaluate variable`() {
            val x = intVar(0, 100)
            assertEquals(100L, evaluator.evalLong(x))
        }

        @Test
        fun `evaluate boolean variable as true`() {
            val x = boolVar(0, true)
            assertTrue(evaluator.evalBool(x))
        }

        @Test
        fun `evaluate boolean variable as false`() {
            val x = boolVar(0, false)
            assertFalse(evaluator.evalBool(x))
        }

        @Test
        fun `evaluate double variable`() {
            val x = doubleVar(0, 2.5)
            assertEquals(2.5, evaluator.evalDouble(x), 0.001)
        }

        @Test
        fun `returns Left on unset variable`() {
            val expr = Expr(ExprNode.Var(99, VarType.INT))
            assertTrue(evaluator.evaluateLong(expr).isLeft())
        }

        @Test
        fun `clear removes all variables`() {
            evaluator.setVar(0, 10L)
            evaluator.setVar(1, 20L)
            evaluator.clear()
            
            assertTrue(evaluator.evaluateLong(Expr(ExprNode.Var(0, VarType.INT))).isLeft())
        }
    }

    @Nested
    inner class ArithmeticEvaluation {

        @Test
        fun `sum of multiple values`() {
            val x = intVar(0, 10)
            val y = intVar(1, 20)
            val z = intVar(2, 30)
            val result = sum(listOf(x, y, z))
            assertEquals(60L, evaluator.evalLong(result))
        }

        @Test
        fun `sum with mixed Long and Double`() {
            val x = intVar(0, 10)
            val y = doubleVar(1, 5.5)
            val result = x + y
            assertEquals(15.5, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `subtraction`() {
            val x = intVar(0, 50)
            val y = intVar(1, 30)
            val result = x - y
            assertEquals(20L, evaluator.evalLong(result))
        }

        @Test
        fun `subtraction with Double`() {
            val x = intVar(0, 10)
            val y = doubleVar(1, 3.5)
            val result = x - y
            assertEquals(6.5, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `product of multiple values`() {
            val x = intVar(0, 2)
            val y = intVar(1, 3)
            val z = intVar(2, 4)
            val result = prod(listOf(x, y, z))
            assertEquals(24L, evaluator.evalLong(result))
        }

        @Test
        fun `product with Double`() {
            val x = intVar(0, 4)
            val y = doubleVar(1, 2.5)
            val result = x * y
            assertEquals(10.0, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `division`() {
            val x = intVar(0, 100)
            val y = intVar(1, 5)
            val result = x / y
            assertEquals(20L, evaluator.evalLong(result))
        }

        @Test
        fun `division with Double`() {
            val x = doubleVar(0, 10.0)
            val y = intVar(1, 4)
            val result = x / y
            assertEquals(2.5, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `modulo`() {
            val x = intVar(0, 17)
            val y = intVar(1, 5)
            val result = x % y
            assertEquals(2L, evaluator.evalLong(result))
        }

        @Test
        fun `negation of Long`() {
            val x = intVar(0, 42)
            val result = -x
            assertEquals(-42L, evaluator.evalLong(result))
        }

        @Test
        fun `negation of Double`() {
            val x = doubleVar(0, 3.14)
            val result = -x
            assertEquals(-3.14, evaluator.evalDouble(result), 0.001)
        }
    }

    @Nested
    inner class ComparisonEvaluation {

        @Test
        fun `equality true`() {
            val x = intVar(0, 5)
            val y = intVar(1, 5)
            assertEquals(1L, evaluator.evalLong(x eq y))
        }

        @Test
        fun `equality false`() {
            val x = intVar(0, 5)
            val y = intVar(1, 6)
            assertEquals(0L, evaluator.evalLong(x eq y))
        }

        @Test
        fun `inequality true`() {
            val x = intVar(0, 5)
            val y = intVar(1, 6)
            assertEquals(1L, evaluator.evalLong(x neq y))
        }

        @Test
        fun `less than true`() {
            val x = intVar(0, 3)
            val y = intVar(1, 5)
            assertEquals(1L, evaluator.evalLong(x lt y))
        }

        @Test
        fun `less than false`() {
            val x = intVar(0, 5)
            val y = intVar(1, 5)
            assertEquals(0L, evaluator.evalLong(x lt y))
        }

        @Test
        fun `less than or equal true for less`() {
            val x = intVar(0, 3)
            val y = intVar(1, 5)
            assertEquals(1L, evaluator.evalLong(x leq y))
        }

        @Test
        fun `less than or equal true for equal`() {
            val x = intVar(0, 5)
            val y = intVar(1, 5)
            assertEquals(1L, evaluator.evalLong(x leq y))
        }

        @Test
        fun `greater than true`() {
            val x = intVar(0, 7)
            val y = intVar(1, 5)
            assertEquals(1L, evaluator.evalLong(x gt y))
        }

        @Test
        fun `greater than or equal true for greater`() {
            val x = intVar(0, 7)
            val y = intVar(1, 5)
            assertEquals(1L, evaluator.evalLong(x geq y))
        }

        @Test
        fun `greater than or equal true for equal`() {
            val x = intVar(0, 5)
            val y = intVar(1, 5)
            assertEquals(1L, evaluator.evalLong(x geq y))
        }

        @Test
        fun `comparison with mixed types uses numeric comparison`() {
            // When comparing Long and Double, less-than comparisons work numerically
            val x = intVar(0, 3)
            val y = doubleVar(1, 5.0)
            assertEquals(1L, evaluator.evalLong(x lt y))
            assertEquals(0L, evaluator.evalLong(x gt y))
        }
    }

    @Nested
    inner class LogicalEvaluation {

        @Test
        fun `and all true`() {
            val a = boolVar(0, true)
            val b = boolVar(1, true)
            val c = boolVar(2, true)
            val result = allOf(listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `and with one false`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, true)
            val result = allOf(listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `or all false`() {
            val a = boolVar(0, false)
            val b = boolVar(1, false)
            val c = boolVar(2, false)
            val result = anyOf(listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `or with one true`() {
            val a = boolVar(0, false)
            val b = boolVar(1, true)
            val c = boolVar(2, false)
            val result = anyOf(listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `not of false`() {
            val a = boolVar(0, false)
            val result = !a
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `not of true`() {
            val a = boolVar(0, true)
            val result = !a
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `xor odd number of trues`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, true)
            val d = boolVar(3, true)
            val result = xorOf(listOf(a, b, c, d))
            assertEquals(1L, evaluator.evalLong(result)) // 3 trues = odd
        }

        @Test
        fun `xor even number of trues`() {
            val a = boolVar(0, true)
            val b = boolVar(1, true)
            val c = boolVar(2, false)
            val d = boolVar(3, false)
            val result = xorOf(listOf(a, b, c, d))
            assertEquals(0L, evaluator.evalLong(result)) // 2 trues = even
        }
    }

    @Nested
    inner class ConditionalEvaluation {

        @Test
        fun `if true branch`() {
            val cond = boolVar(0, true)
            val ifTrue = intVar(1, 100)
            val ifFalse = intVar(2, 200)
            val result = iif(cond, ifTrue, ifFalse)
            assertEquals(100L, evaluator.evalLong(result))
        }

        @Test
        fun `if false branch`() {
            val cond = boolVar(0, false)
            val ifTrue = intVar(1, 100)
            val ifFalse = intVar(2, 200)
            val result = iif(cond, ifTrue, ifFalse)
            assertEquals(200L, evaluator.evalLong(result))
        }

        @Test
        fun `nested if`() {
            val cond1 = boolVar(0, false)
            val cond2 = boolVar(1, true)
            val result = iif(cond1, 
                expr(10L), 
                iif(cond2, expr(20L), expr(30L))
            )
            assertEquals(20L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class ArrayEvaluation {

        @Test
        fun `array literal access`() {
            val arr = Expr(ExprNode.ArrayLiteral(longArrayOf(10, 20, 30, 40)))
            val result = arr[2]
            assertEquals(30L, evaluator.evalLong(result))
        }

        @Test
        fun `array literal with variable index`() {
            val arr = Expr(ExprNode.ArrayLiteral(longArrayOf(100, 200, 300)))
            val idx = intVar(0, 1)
            val result = arr[idx]
            assertEquals(200L, evaluator.evalLong(result))
        }

        @Test
        fun `ArrayOf with expressions`() {
            val x = intVar(0, 5)
            val y = intVar(1, 10)
            val arr = arrayOf(listOf(x, y, expr(15L)))
            val result = arr[1]
            assertEquals(10L, evaluator.evalLong(result))
        }

        @Test
        fun `double array literal`() {
            val arr = Expr(ExprNode.ArrayLiteralDouble(doubleArrayOf(1.1, 2.2, 3.3)))
            val result = arr[1]
            assertEquals(2.2, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `2D array access`() {
            val arr = Expr(ExprNode.Array2D(listOf(
                ExprNode.ArrayLiteral(longArrayOf(1, 2, 3)),
                ExprNode.ArrayLiteral(longArrayOf(4, 5, 6)),
                ExprNode.ArrayLiteral(longArrayOf(7, 8, 9))
            )))
            val result = Expr(ExprNode.At2D(arr.node, ExprNode.Const(1), ExprNode.Const(2)))
            assertEquals(6L, evaluator.evalLong(result))
        }

        @Test
        fun `count array literal`() {
            val arr = Expr(ExprNode.ArrayLiteral(longArrayOf(1, 2, 3, 4, 5)))
            val result = arr.count()
            assertEquals(5L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class ListEvaluation {

        @Test
        fun `list count`() {
            evaluator.setList(0, listOf(1L, 2L, 3L, 4L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = list.count()
            assertEquals(4L, evaluator.evalLong(result))
        }

        @Test
        fun `list contains existing element`() {
            evaluator.setList(0, listOf(10L, 20L, 30L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = list.containsElem(20L)
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `list contains non-existing element`() {
            evaluator.setList(0, listOf(10L, 20L, 30L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = list.containsElem(25L)
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `list indexOf existing element`() {
            evaluator.setList(0, listOf(10L, 20L, 30L, 40L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = list.indexOf(30L)
            assertEquals(2L, evaluator.evalLong(result))
        }

        @Test
        fun `list indexOf non-existing element returns -1`() {
            evaluator.setList(0, listOf(10L, 20L, 30L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = list.indexOf(50L)
            assertEquals(-1L, evaluator.evalLong(result))
        }

        @Test
        fun `list find`() {
            evaluator.setList(0, listOf(5L, 10L, 15L, 20L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = list.find(15L)
            assertEquals(2L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class SetEvaluation {

        @Test
        fun `set count`() {
            evaluator.setSet(0, setOf(1L, 2L, 3L))
            val set = Expr(ExprNode.Var(0, VarType.SET))
            val result = set.card()
            assertEquals(3L, evaluator.evalLong(result))
        }

        @Test
        fun `set contains existing element`() {
            evaluator.setSet(0, setOf(10L, 20L, 30L))
            val set = Expr(ExprNode.Var(0, VarType.SET))
            val result = set.containsElem(20L)
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `set intersection`() {
            evaluator.setSet(0, setOf(1L, 2L, 3L, 4L))
            evaluator.setSet(1, setOf(3L, 4L, 5L, 6L))
            val set1 = Expr(ExprNode.Var(0, VarType.SET))
            val set2 = Expr(ExprNode.Var(1, VarType.SET))
            val result = intersection(listOf(set1, set2))
            val resultSet = evaluator.eval(result) as Set<*>
            assertEquals(setOf(3L, 4L), resultSet)
        }

        @Test
        fun `set union`() {
            evaluator.setSet(0, setOf(1L, 2L))
            evaluator.setSet(1, setOf(2L, 3L))
            val set1 = Expr(ExprNode.Var(0, VarType.SET))
            val set2 = Expr(ExprNode.Var(1, VarType.SET))
            val result = union(listOf(set1, set2))
            val resultSet = evaluator.eval(result) as Set<*>
            assertEquals(setOf(1L, 2L, 3L), resultSet)
        }

        @Test
        fun `empty intersection`() {
            val result = intersection(emptyList())
            val resultSet = evaluator.eval(result) as Set<*>
            assertTrue(resultSet.isEmpty())
        }

        @Test
        fun `empty union`() {
            val result = union(emptyList())
            val resultSet = evaluator.eval(result) as Set<*>
            assertTrue(resultSet.isEmpty())
        }
    }

    @Nested
    inner class SortEvaluation {

        @Test
        fun `sort list ascending`() {
            evaluator.setList(0, listOf(30L, 10L, 50L, 20L, 40L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = sort(list)
            val sortedList = evaluator.eval(result) as List<*>
            assertEquals(listOf(10L, 20L, 30L, 40L, 50L), sortedList)
        }

        @Test
        fun `sorted extension function`() {
            evaluator.setList(0, listOf(3L, 1L, 4L, 1L, 5L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = list.sorted()
            val sortedList = evaluator.eval(result) as List<*>
            assertEquals(listOf(1L, 1L, 3L, 4L, 5L), sortedList)
        }
    }

    @Nested
    inner class MinMaxEvaluation {

        @Test
        fun `min of expressions`() {
            val a = intVar(0, 10)
            val b = intVar(1, 5)
            val c = intVar(2, 15)
            val result = min(listOf(a, b, c))
            assertEquals(5L, evaluator.evalLong(result))
        }

        @Test
        fun `max of expressions`() {
            val a = intVar(0, 10)
            val b = intVar(1, 5)
            val c = intVar(2, 15)
            val result = max(listOf(a, b, c))
            assertEquals(15L, evaluator.evalLong(result))
        }

        @Test
        fun `min with doubles`() {
            val a = doubleVar(0, 3.14)
            val b = doubleVar(1, 2.71)
            val c = doubleVar(2, 1.41)
            val result = min(listOf(a, b, c))
            assertEquals(1.41, evaluator.evalDouble(result), 0.001)
        }

        @Test
        fun `max with doubles`() {
            val a = doubleVar(0, 3.14)
            val b = doubleVar(1, 2.71)
            val c = doubleVar(2, 1.41)
            val result = max(listOf(a, b, c))
            assertEquals(3.14, evaluator.evalDouble(result), 0.001)
        }
    }

    @Nested
    inner class LambdaAggregationEvaluation {

        @Test
        fun `sumOver collection`() {
            evaluator.setList(0, listOf(1L, 2L, 3L, 4L, 5L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = sumOver(list) { x -> x * expr(2L) }
            assertEquals(30L, evaluator.evalLong(result)) // 2+4+6+8+10 = 30
        }

        @Test
        fun `prodOver collection`() {
            evaluator.setList(0, listOf(1L, 2L, 3L, 4L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = prodOver(list) { x -> x }
            assertEquals(24L, evaluator.evalLong(result)) // 1*2*3*4 = 24
        }

        @Test
        fun `minOver collection`() {
            evaluator.setList(0, listOf(10L, 5L, 20L, 3L, 15L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = minOver(list) { x -> x }
            assertEquals(3L, evaluator.evalLong(result))
        }

        @Test
        fun `maxOver collection`() {
            evaluator.setList(0, listOf(10L, 5L, 20L, 3L, 15L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = maxOver(list) { x -> x }
            assertEquals(20L, evaluator.evalLong(result))
        }

        @Test
        fun `forAllOver all satisfy`() {
            evaluator.setList(0, listOf(2L, 4L, 6L, 8L, 10L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            // Check all are even (x % 2 == 0)
            val result = forAllOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `forAllOver not all satisfy`() {
            evaluator.setList(0, listOf(2L, 4L, 5L, 8L, 10L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = forAllOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `existsOver at least one satisfies`() {
            evaluator.setList(0, listOf(1L, 3L, 5L, 6L, 9L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            // Check at least one is even
            val result = existsOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `existsOver none satisfy`() {
            evaluator.setList(0, listOf(1L, 3L, 5L, 7L, 9L))
            val list = Expr(ExprNode.Var(0, VarType.LIST))
            val result = existsOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class MathFunctionEvaluation {

        @Test
        fun `abs of positive`() {
            val x = intVar(0, 10)
            assertEquals(10L, evaluator.evalLong(abs(x)))
        }

        @Test
        fun `abs of negative`() {
            val x = intVar(0, -10)
            assertEquals(10L, evaluator.evalLong(abs(x)))
        }

        @Test
        fun `abs of double`() {
            val x = doubleVar(0, -3.5)
            assertEquals(3.5, evaluator.evalDouble(abs(x)), 0.001)
        }

        @Test
        fun `sqrt`() {
            val x = doubleVar(0, 25.0)
            assertEquals(5.0, evaluator.evalDouble(sqrt(x)), 0.001)
        }

        @Test
        fun `pow`() {
            val base = intVar(0, 2)
            val exp = intVar(1, 8)
            assertEquals(256.0, evaluator.evalDouble(pow(base, exp)), 0.001)
        }

        @Test
        fun `exp`() {
            val x = doubleVar(0, 0.0)
            assertEquals(1.0, evaluator.evalDouble(exp(x)), 0.001)
        }

        @Test
        fun `log`() {
            val x = doubleVar(0, 1.0)
            assertEquals(0.0, evaluator.evalDouble(log(x)), 0.001)
        }

        @Test
        fun `ceil`() {
            val x = doubleVar(0, 2.3)
            assertEquals(3L, evaluator.evalLong(ceil(x)))
        }

        @Test
        fun `floor`() {
            val x = doubleVar(0, 2.9)
            assertEquals(2L, evaluator.evalLong(floor(x)))
        }

        @Test
        fun `round down`() {
            val x = doubleVar(0, 2.4)
            assertEquals(2L, evaluator.evalLong(round(x)))
        }

        @Test
        fun `round up`() {
            val x = doubleVar(0, 2.6)
            assertEquals(3L, evaluator.evalLong(round(x)))
        }
    }

    @Nested
    inner class DomainEvaluation {

        @Test
        fun `inDomain true`() {
            val x = intVar(0, 5)
            val result = x inDomain longArrayOf(1, 3, 5, 7, 9)
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `inDomain false`() {
            val x = intVar(0, 4)
            val result = x inDomain longArrayOf(1, 3, 5, 7, 9)
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class ExtensionFunctions {

        @Test
        fun `evaluate with var bindings`() {
            val x = Expr(ExprNode.Var(0, VarType.INT))
            val y = Expr(ExprNode.Var(1, VarType.INT))
            val expr = x + y
            val result = expr.evaluate(mapOf(0 to 10L, 1 to 20L)).getOrElse { fail(it.message) }
            assertEquals(30L, result)
        }

        @Test
        fun `evaluateBool with var bindings`() {
            val x = Expr(ExprNode.Var(0, VarType.INT))
            val y = Expr(ExprNode.Var(1, VarType.INT))
            val expr = x eq y
            val result = expr.evaluateBool(mapOf(0 to 5L, 1 to 5L)).getOrElse { fail(it.message) }
            assertTrue(result)
        }

        @Test
        fun `evaluateBool returns false`() {
            val x = Expr(ExprNode.Var(0, VarType.INT))
            val y = Expr(ExprNode.Var(1, VarType.INT))
            val expr = x gt y
            val result = expr.evaluateBool(mapOf(0 to 5L, 1 to 10L)).getOrElse { fail(it.message) }
            assertFalse(result)
        }
    }

    @Nested
    inner class ComplexExpressions {

        @Test
        fun `nested arithmetic`() {
            val a = intVar(0, 2)
            val b = intVar(1, 3)
            val c = intVar(2, 4)
            // (a + b) * c - a
            val result = (a + b) * c - a
            assertEquals(18L, evaluator.evalLong(result)) // (2+3)*4 - 2 = 20 - 2 = 18
        }

        @Test
        fun `complex conditional`() {
            val x = intVar(0, 15)
            // if x > 10 then x * 2 otherwise x + 100
            val result = (x gt 10L) then (x * 2L) otherwise (x + 100L)
            assertEquals(30L, evaluator.evalLong(result))
        }

        @Test
        fun `conditional with comparison`() {
            val x = intVar(0, 5)
            val result = (x lt 10L) then (x * 2L) otherwise (x + 100L)
            assertEquals(10L, evaluator.evalLong(result))
        }

        @Test
        fun `combining arithmetic and logical`() {
            val a = intVar(0, 5)
            val b = intVar(1, 10)
            // (a < b) and (a + b > 12)
            val result = (a lt b) and ((a + b) gt 12L)
            assertEquals(1L, evaluator.evalLong(result)) // true and true
        }
    }
}

