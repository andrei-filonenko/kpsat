package io.github.andreifilonenko.kpsat.dsl

import arrow.core.getOrElse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests for aggregation functions defined in Aggregations.kt.
 */
class AggregationsTest {

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

    // Helper extension functions for tests
    private fun DirectEvaluator.evalLong(expr: Expr): Long = evaluateLong(expr).getOrElse { fail(it.message) }

    @Nested
    inner class BasicAggregations {

        @Test
        fun `sum of list of expressions`() {
            val a = intVar(0, 10)
            val b = intVar(1, 20)
            val c = intVar(2, 30)
            val result = sum(listOf(a, b, c))
            assertEquals(60L, evaluator.evalLong(result))
        }

        @Test
        fun `sum of empty list`() {
            val result = sum(emptyList())
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `sum of single element`() {
            val a = intVar(0, 42)
            val result = sum(listOf(a))
            assertEquals(42L, evaluator.evalLong(result))
        }

        @Test
        fun `prod of list of expressions`() {
            val a = intVar(0, 2)
            val b = intVar(1, 3)
            val c = intVar(2, 4)
            val result = prod(listOf(a, b, c))
            assertEquals(24L, evaluator.evalLong(result))
        }

        @Test
        fun `prod of empty list`() {
            val result = prod(emptyList())
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `prod of single element`() {
            val a = intVar(0, 7)
            val result = prod(listOf(a))
            assertEquals(7L, evaluator.evalLong(result))
        }

        @Test
        fun `min of list of expressions`() {
            val a = intVar(0, 15)
            val b = intVar(1, 5)
            val c = intVar(2, 25)
            val result = min(listOf(a, b, c))
            assertEquals(5L, evaluator.evalLong(result))
        }

        @Test
        fun `min of single element`() {
            val a = intVar(0, 42)
            val result = min(listOf(a))
            assertEquals(42L, evaluator.evalLong(result))
        }

        @Test
        fun `max of list of expressions`() {
            val a = intVar(0, 15)
            val b = intVar(1, 5)
            val c = intVar(2, 25)
            val result = max(listOf(a, b, c))
            assertEquals(25L, evaluator.evalLong(result))
        }

        @Test
        fun `max of single element`() {
            val a = intVar(0, 42)
            val result = max(listOf(a))
            assertEquals(42L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class RangeAggregations {

        @Test
        fun `sum over IntRange`() {
            // sum(0 until 5) { i -> i * 2 } = 0 + 2 + 4 + 6 + 8 = 20
            val result = sum(0 until 5) { i -> expr(i.toLong() * 2) }
            assertEquals(20L, evaluator.evalLong(result))
        }

        @Test
        fun `sum over LongRange`() {
            // sum(1L..4L) { i -> i } = 1 + 2 + 3 + 4 = 10
            val result = sum(1L..4L) { i -> expr(i) }
            assertEquals(10L, evaluator.evalLong(result))
        }

        @Test
        fun `sum over iterable`() {
            val items = listOf("a", "bb", "ccc")
            val result = sum(items) { s -> expr(s.length.toLong()) }
            assertEquals(6L, evaluator.evalLong(result)) // 1 + 2 + 3
        }

        @Test
        fun `prod over IntRange`() {
            // prod(1 until 5) { i -> i } = 1 * 2 * 3 * 4 = 24
            val result = prod(1 until 5) { i -> expr(i.toLong()) }
            assertEquals(24L, evaluator.evalLong(result))
        }

        @Test
        fun `prod over LongRange`() {
            // prod(1L..3L) { i -> i + 1 } = 2 * 3 * 4 = 24
            val result = prod(1L..3L) { i -> expr(i + 1) }
            assertEquals(24L, evaluator.evalLong(result))
        }

        @Test
        fun `prod over iterable`() {
            val items = listOf(2, 3, 4)
            val result = prod(items) { i -> expr(i.toLong()) }
            assertEquals(24L, evaluator.evalLong(result))
        }

        @Test
        fun `min over IntRange`() {
            // min(0 until 5) { i -> (i - 2)^2 } => values: 4, 1, 0, 1, 4 => min = 0
            val result = min(0 until 5) { i -> 
                val diff = i - 2
                expr((diff * diff).toLong())
            }
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `min over LongRange`() {
            val result = min(10L..15L) { i -> expr(i) }
            assertEquals(10L, evaluator.evalLong(result))
        }

        @Test
        fun `min over iterable`() {
            val items = listOf(5, 3, 8, 1, 9)
            val result = min(items) { i -> expr(i.toLong()) }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `max over IntRange`() {
            val result = max(0 until 10) { i -> expr(i.toLong()) }
            assertEquals(9L, evaluator.evalLong(result))
        }

        @Test
        fun `max over LongRange`() {
            val result = max(5L..10L) { i -> expr(i * 2) }
            assertEquals(20L, evaluator.evalLong(result))
        }

        @Test
        fun `max over iterable`() {
            val items = listOf(5, 3, 8, 1, 9)
            val result = max(items) { i -> expr(i.toLong()) }
            assertEquals(9L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class LogicalAggregations {

        @Test
        fun `allOf with all true`() {
            val a = boolVar(0, true)
            val b = boolVar(1, true)
            val c = boolVar(2, true)
            val result = allOf(listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `allOf with one false`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, true)
            val result = allOf(listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `allOf with empty list`() {
            val result = allOf(emptyList())
            assertEquals(1L, evaluator.evalLong(result)) // Vacuous truth
        }

        @Test
        fun `anyOf with all false`() {
            val a = boolVar(0, false)
            val b = boolVar(1, false)
            val c = boolVar(2, false)
            val result = anyOf(listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `anyOf with one true`() {
            val a = boolVar(0, false)
            val b = boolVar(1, true)
            val c = boolVar(2, false)
            val result = anyOf(listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `anyOf with empty list`() {
            val result = anyOf(emptyList())
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `xorOf with odd number of trues`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, true)
            val d = boolVar(3, true)
            val result = xorOf(listOf(a, b, c, d))
            assertEquals(1L, evaluator.evalLong(result)) // 3 trues = odd
        }

        @Test
        fun `xorOf with even number of trues`() {
            val a = boolVar(0, true)
            val b = boolVar(1, true)
            val c = boolVar(2, false)
            val d = boolVar(3, false)
            val result = xorOf(listOf(a, b, c, d))
            assertEquals(0L, evaluator.evalLong(result)) // 2 trues = even
        }
    }

    @Nested
    inner class Quantifiers {

        @Test
        fun `forAll over IntRange - all satisfy`() {
            // Check all values in 0..4 are >= 0
            val result = forAll(0 until 5) { i -> expr(i.toLong()) geq 0L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `forAll over IntRange - not all satisfy`() {
            // Check all values in -2..2 are positive
            val result = forAll(-2..2) { i -> expr(i.toLong()) gt 0L }
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `forAll over LongRange`() {
            // Check all values in 1..5 are less than 10
            val result = forAll(1L..5L) { i -> expr(i) lt 10L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `forAll over iterable`() {
            val items = listOf(2, 4, 6, 8)
            // Check all are even
            val result = forAll(items) { i -> expr(i.toLong() % 2) eq 0L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `exists over IntRange - one satisfies`() {
            // Check if any value in 0..10 equals 5
            val result = exists(0..10) { i -> expr(i.toLong()) eq 5L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `exists over IntRange - none satisfy`() {
            // Check if any value in 0..4 equals 10
            val result = exists(0 until 5) { i -> expr(i.toLong()) eq 10L }
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `exists over LongRange`() {
            // Check if any value in 1..10 is divisible by 3
            val result = exists(1L..10L) { i -> expr(i % 3) eq 0L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `exists over iterable`() {
            val items = listOf(1, 3, 5, 7)
            // Check if any is even
            val result = exists(items) { i -> expr(i.toLong() % 2) eq 0L }
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class CountingFunctions {

        @Test
        fun `count of boolean expressions`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, true)
            val d = boolVar(3, true)
            val result = count(listOf(a, b, c, d))
            assertEquals(3L, evaluator.evalLong(result))
        }

        @Test
        fun `count over IntRange`() {
            // Count even numbers in 0..9
            val result = count(0 until 10) { i -> 
                (expr(i.toLong()) % 2L) eq 0L
            }
            assertEquals(5L, evaluator.evalLong(result))
        }

        @Test
        fun `count over LongRange`() {
            // Count multiples of 3 in 1..15
            val result = count(1L..15L) { i ->
                (expr(i) % 3L) eq 0L
            }
            assertEquals(5L, evaluator.evalLong(result)) // 3, 6, 9, 12, 15
        }

        @Test
        fun `count over iterable`() {
            val items = listOf("a", "bb", "ccc", "dddd", "eeeee")
            // Count strings with length >= 3
            val result = count(items) { s -> expr(s.length.toLong()) geq 3L }
            assertEquals(3L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class ExactlyAtLeastAtMost {

        @Test
        fun `exactly n - true case`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, true)
            val result = exactly(2, listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `exactly n - false case`() {
            val a = boolVar(0, true)
            val b = boolVar(1, true)
            val c = boolVar(2, true)
            val result = exactly(2, listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `atLeast n - true case`() {
            val a = boolVar(0, true)
            val b = boolVar(1, true)
            val c = boolVar(2, false)
            val result = atLeast(2, listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `atLeast n - false case`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, false)
            val result = atLeast(2, listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `atMost n - true case`() {
            val a = boolVar(0, true)
            val b = boolVar(1, false)
            val c = boolVar(2, false)
            val result = atMost(1, listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `atMost n - false case`() {
            val a = boolVar(0, true)
            val b = boolVar(1, true)
            val c = boolVar(2, false)
            val result = atMost(1, listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class AllDifferent {

        @Test
        fun `allDifferent with all different values`() {
            val a = intVar(0, 1)
            val b = intVar(1, 2)
            val c = intVar(2, 3)
            val result = allDifferent(listOf(a, b, c))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `allDifferent with duplicate values`() {
            val a = intVar(0, 1)
            val b = intVar(1, 2)
            val c = intVar(2, 1) // Duplicate
            val result = allDifferent(listOf(a, b, c))
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `allDifferent with empty list`() {
            val result = allDifferent(emptyList())
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `allDifferent with single element`() {
            val a = intVar(0, 42)
            val result = allDifferent(listOf(a))
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `allDifferent with many elements`() {
            val vars = (0..9).map { i -> intVar(i, i.toLong()) }
            val result = allDifferent(vars)
            assertEquals(1L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class CollectionBasedLazyAggregations {

        @Test
        fun `sumOver with identity`() {
            evaluator.setList(10, listOf(1L, 2L, 3L, 4L, 5L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = sumOver(list) { x -> x }
            assertEquals(15L, evaluator.evalLong(result))
        }

        @Test
        fun `sumOver with transformation`() {
            evaluator.setList(10, listOf(1L, 2L, 3L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = sumOver(list) { x -> x * x }
            assertEquals(14L, evaluator.evalLong(result)) // 1 + 4 + 9
        }

        @Test
        fun `prodOver`() {
            evaluator.setList(10, listOf(1L, 2L, 3L, 4L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = prodOver(list) { x -> x }
            assertEquals(24L, evaluator.evalLong(result))
        }

        @Test
        fun `minOver`() {
            evaluator.setList(10, listOf(5L, 3L, 8L, 1L, 9L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = minOver(list) { x -> x }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `maxOver`() {
            evaluator.setList(10, listOf(5L, 3L, 8L, 1L, 9L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = maxOver(list) { x -> x }
            assertEquals(9L, evaluator.evalLong(result))
        }

        @Test
        fun `forAllOver - all satisfy`() {
            evaluator.setList(10, listOf(2L, 4L, 6L, 8L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = forAllOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `forAllOver - not all satisfy`() {
            evaluator.setList(10, listOf(2L, 4L, 5L, 8L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = forAllOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(0L, evaluator.evalLong(result))
        }

        @Test
        fun `existsOver - at least one satisfies`() {
            evaluator.setList(10, listOf(1L, 3L, 5L, 6L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = existsOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `existsOver - none satisfy`() {
            evaluator.setList(10, listOf(1L, 3L, 5L, 7L))
            val list = Expr(ExprNode.Var(10, VarType.LIST))
            val result = existsOver(list) { x -> x % expr(2L) eq 0L }
            assertEquals(0L, evaluator.evalLong(result))
        }
    }

    @Nested
    inner class ComplexAggregations {

        @Test
        fun `nested sum and product`() {
            // sum(0..2) { i -> prod(0..2) { j -> i + j } }
            // i=0: 0*1*2 = 0
            // i=1: 1*2*3 = 6
            // i=2: 2*3*4 = 24
            // Total: 30
            val result = sum(0..2) { i ->
                prod(0..2) { j ->
                    expr((i + j).toLong())
                }
            }
            assertEquals(30L, evaluator.evalLong(result))
        }

        @Test
        fun `count with complex predicate`() {
            // Count numbers in 1..20 that are divisible by both 2 and 3
            val result = count(1..20) { i ->
                val e = expr(i.toLong())
                (e % 2L eq 0L) and (e % 3L eq 0L)
            }
            assertEquals(3L, evaluator.evalLong(result)) // 6, 12, 18
        }

        @Test
        fun `forAll with min comparison`() {
            val a = intVar(0, 5)
            val b = intVar(1, 10)
            val c = intVar(2, 15)
            val values = listOf(a, b, c)
            
            // Check all values are >= min
            val minVal = min(values)
            val result = forAll(values) { v -> v geq minVal }
            assertEquals(1L, evaluator.evalLong(result))
        }

        @Test
        fun `sum of conditional expressions`() {
            // Sum of i if i is even, else 0
            val result = sum(0 until 10) { i ->
                val e = expr(i.toLong())
                e onlyIf ((e % 2L) eq 0L)
            }
            assertEquals(20L, evaluator.evalLong(result)) // 0 + 2 + 4 + 6 + 8 = 20
        }
    }
}

