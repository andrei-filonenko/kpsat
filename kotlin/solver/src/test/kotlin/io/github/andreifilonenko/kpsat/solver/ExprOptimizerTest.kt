package io.github.andreifilonenko.kpsat.solver

import io.github.andreifilonenko.kpsat.dsl.ExprNode
import io.github.andreifilonenko.kpsat.dsl.VarType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for ExprOptimizer pattern detection and tree rewriting.
 */
class ExprOptimizerTest {

    private val optimizer = ExprOptimizer()

    @Nested
    inner class CountingPatternDetection {

        @Test
        fun `sum of iif(cond, 1, 0) should become CountTrue`() {
            // sum(iif(a, 1, 0), iif(b, 1, 0))
            val a = ExprNode.Var(1, VarType.BOOL)
            val b = ExprNode.Var(2, VarType.BOOL)
            val sum = ExprNode.Sum(listOf(
                ExprNode.If(a, ExprNode.Const(1), ExprNode.Const(0)),
                ExprNode.If(b, ExprNode.Const(1), ExprNode.Const(0))
            ))

            val optimized = optimizer.optimize(sum)

            assertIs<ExprNode.CountTrue>(optimized)
            assertEquals(2, optimized.conditions.size)
        }

        @Test
        fun `nested sum with counting patterns should flatten into single CountTrue`() {
            val a = ExprNode.Var(1, VarType.BOOL)
            val b = ExprNode.Var(2, VarType.BOOL)
            val c = ExprNode.Var(3, VarType.BOOL)

            val innerSum = ExprNode.Sum(listOf(
                ExprNode.If(a, ExprNode.Const(1), ExprNode.Const(0)),
                ExprNode.If(b, ExprNode.Const(1), ExprNode.Const(0))
            ))
            val outerSum = ExprNode.Sum(listOf(
                innerSum,
                ExprNode.If(c, ExprNode.Const(1), ExprNode.Const(0))
            ))

            val optimized = optimizer.optimize(outerSum)

            assertIs<ExprNode.CountTrue>(optimized)
            assertEquals(3, optimized.conditions.size)
        }
    }

    @Nested
    inner class IndicatorSumPatternDetection {

        @Test
        fun `sum of iif(cond, value, 0) should become IndicatorSum`() {
            val cond1 = ExprNode.Var(1, VarType.BOOL)
            val cond2 = ExprNode.Var(2, VarType.BOOL)
            val val1 = ExprNode.Var(3, VarType.INT)
            val val2 = ExprNode.Var(4, VarType.INT)

            val sum = ExprNode.Sum(listOf(
                ExprNode.If(cond1, val1, ExprNode.Const(0)),
                ExprNode.If(cond2, val2, ExprNode.Const(0))
            ))

            val optimized = optimizer.optimize(sum)

            assertIs<ExprNode.IndicatorSum>(optimized)
            assertEquals(2, optimized.terms.size)
        }

        @Test
        fun `mixed counting and indicator patterns should be separated`() {
            val cond1 = ExprNode.Var(1, VarType.BOOL)
            val cond2 = ExprNode.Var(2, VarType.BOOL)
            val val2 = ExprNode.Var(3, VarType.INT)

            val sum = ExprNode.Sum(listOf(
                ExprNode.If(cond1, ExprNode.Const(1), ExprNode.Const(0)), // counting
                ExprNode.If(cond2, val2, ExprNode.Const(0)) // indicator
            ))

            val optimized = optimizer.optimize(sum)

            // Should result in Sum(CountTrue, IndicatorSum)
            assertIs<ExprNode.Sum>(optimized)
            assertEquals(2, optimized.operands.size)

            val hasCountTrue = optimized.operands.any { it is ExprNode.CountTrue }
            val hasIndicatorSum = optimized.operands.any { it is ExprNode.IndicatorSum }
            assertTrue(hasCountTrue)
            assertTrue(hasIndicatorSum)
        }
    }

    @Nested
    inner class AndOrFlattening {

        @Test
        fun `nested And should be flattened`() {
            val a = ExprNode.Var(1, VarType.BOOL)
            val b = ExprNode.Var(2, VarType.BOOL)
            val c = ExprNode.Var(3, VarType.BOOL)
            val d = ExprNode.Var(4, VarType.BOOL)

            val nested = ExprNode.And(listOf(
                ExprNode.And(listOf(a, b)),
                ExprNode.And(listOf(c, d))
            ))

            val optimized = optimizer.optimize(nested)

            assertIs<ExprNode.And>(optimized)
            assertEquals(4, optimized.operands.size)
        }

        @Test
        fun `nested Or should be flattened`() {
            val a = ExprNode.Var(1, VarType.BOOL)
            val b = ExprNode.Var(2, VarType.BOOL)
            val c = ExprNode.Var(3, VarType.BOOL)

            val nested = ExprNode.Or(listOf(
                ExprNode.Or(listOf(a, b)),
                c
            ))

            val optimized = optimizer.optimize(nested)

            assertIs<ExprNode.Or>(optimized)
            assertEquals(3, optimized.operands.size)
        }

        @Test
        fun `And with constant true should be simplified`() {
            val a = ExprNode.Var(1, VarType.BOOL)

            val andWithTrue = ExprNode.And(listOf(
                a,
                ExprNode.Const(1) // true
            ))

            val optimized = optimizer.optimize(andWithTrue)

            // Should simplify to just 'a'
            assertEquals(a, optimized)
        }

        @Test
        fun `And with constant false should short-circuit to false`() {
            val a = ExprNode.Var(1, VarType.BOOL)

            val andWithFalse = ExprNode.And(listOf(
                a,
                ExprNode.Const(0) // false
            ))

            val optimized = optimizer.optimize(andWithFalse)

            assertEquals(ExprNode.Const(0), optimized)
        }

        @Test
        fun `Or with constant true should short-circuit to true`() {
            val a = ExprNode.Var(1, VarType.BOOL)

            val orWithTrue = ExprNode.Or(listOf(
                a,
                ExprNode.Const(1) // true
            ))

            val optimized = optimizer.optimize(orWithTrue)

            assertEquals(ExprNode.Const(1), optimized)
        }
    }

    @Nested
    inner class ConstantFolding {

        @Test
        fun `sum of constants should be folded`() {
            val sum = ExprNode.Sum(listOf(
                ExprNode.Const(1),
                ExprNode.Const(2),
                ExprNode.Const(3)
            ))

            val optimized = optimizer.optimize(sum)

            assertEquals(ExprNode.Const(6), optimized)
        }

        @Test
        fun `if with constant true condition should be folded`() {
            val ifTrue = ExprNode.If(
                ExprNode.Const(1), // true
                ExprNode.Const(10),
                ExprNode.Const(20)
            )

            val optimized = optimizer.optimize(ifTrue)

            assertEquals(ExprNode.Const(10), optimized)
        }

        @Test
        fun `if with constant false condition should be folded`() {
            val ifFalse = ExprNode.If(
                ExprNode.Const(0), // false
                ExprNode.Const(10),
                ExprNode.Const(20)
            )

            val optimized = optimizer.optimize(ifFalse)

            assertEquals(ExprNode.Const(20), optimized)
        }
    }

    @Nested
    inner class CountTrueNode {

        @Test
        fun `CountTrue node should pass through optimizer`() {
            val countTrue = ExprNode.CountTrue(listOf(
                ExprNode.Var(1, VarType.BOOL),
                ExprNode.Var(2, VarType.BOOL)
            ))

            val optimized = optimizer.optimize(countTrue)

            assertIs<ExprNode.CountTrue>(optimized)
            assertEquals(2, optimized.conditions.size)
        }
    }
}
