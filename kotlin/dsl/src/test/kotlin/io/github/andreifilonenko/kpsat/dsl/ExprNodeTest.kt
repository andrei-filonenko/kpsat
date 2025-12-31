package io.github.andreifilonenko.kpsat.dsl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for ExprNode AST construction and equality.
 */
class ExprNodeTest {

    @Nested
    inner class ConstantNodes {

        @Test
        fun `Const nodes with same value are equal`() {
            val a = ExprNode.Const(42L)
            val b = ExprNode.Const(42L)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `Const nodes with different values are not equal`() {
            val a = ExprNode.Const(42L)
            val b = ExprNode.Const(43L)
            assertNotEquals(a, b)
        }

        @Test
        fun `ConstDouble nodes with same value are equal`() {
            val a = ExprNode.ConstDouble(3.14)
            val b = ExprNode.ConstDouble(3.14)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `ConstDouble nodes with different values are not equal`() {
            val a = ExprNode.ConstDouble(3.14)
            val b = ExprNode.ConstDouble(2.71)
            assertNotEquals(a, b)
        }

        @Test
        fun `const helper function creates Const node`() {
            val node = const(100L)
            assertTrue(node is ExprNode.Const)
            assertEquals(100L, (node as ExprNode.Const).value)
        }

        @Test
        fun `const helper function creates ConstDouble node`() {
            val node = const(1.5)
            assertTrue(node is ExprNode.ConstDouble)
            assertEquals(1.5, (node as ExprNode.ConstDouble).value)
        }
    }

    @Nested
    inner class VariableNodes {

        @Test
        fun `Var nodes with same id and type are equal`() {
            val a = ExprNode.Var(1, VarType.INT)
            val b = ExprNode.Var(1, VarType.INT)
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `Var nodes with different ids are not equal`() {
            val a = ExprNode.Var(1, VarType.INT)
            val b = ExprNode.Var(2, VarType.INT)
            assertNotEquals(a, b)
        }

        @Test
        fun `Var nodes with different types are not equal`() {
            val a = ExprNode.Var(1, VarType.INT)
            val b = ExprNode.Var(1, VarType.BOOL)
            assertNotEquals(a, b)
        }

        @Test
        fun `all VarType values are distinct`() {
            val types = VarType.entries
            assertEquals(5, types.size)
            assertTrue(types.contains(VarType.BOOL))
            assertTrue(types.contains(VarType.INT))
            assertTrue(types.contains(VarType.FLOAT))
            assertTrue(types.contains(VarType.SET))
            assertTrue(types.contains(VarType.LIST))
        }
    }

    @Nested
    inner class ArrayNodes {

        @Test
        fun `ArrayLiteral nodes with same values are equal`() {
            val a = ExprNode.ArrayLiteral(longArrayOf(1, 2, 3))
            val b = ExprNode.ArrayLiteral(longArrayOf(1, 2, 3))
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `ArrayLiteral nodes with different values are not equal`() {
            val a = ExprNode.ArrayLiteral(longArrayOf(1, 2, 3))
            val b = ExprNode.ArrayLiteral(longArrayOf(1, 2, 4))
            assertNotEquals(a, b)
        }

        @Test
        fun `ArrayLiteral nodes with different lengths are not equal`() {
            val a = ExprNode.ArrayLiteral(longArrayOf(1, 2, 3))
            val b = ExprNode.ArrayLiteral(longArrayOf(1, 2))
            assertNotEquals(a, b)
        }

        @Test
        fun `ArrayLiteralDouble nodes with same values are equal`() {
            val a = ExprNode.ArrayLiteralDouble(doubleArrayOf(1.0, 2.0, 3.0))
            val b = ExprNode.ArrayLiteralDouble(doubleArrayOf(1.0, 2.0, 3.0))
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `ArrayOf nodes with same elements are equal`() {
            val a = ExprNode.ArrayOf(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val b = ExprNode.ArrayOf(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            assertEquals(a, b)
        }

        @Test
        fun `ArrayOf nodes with different elements are not equal`() {
            val a = ExprNode.ArrayOf(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val b = ExprNode.ArrayOf(listOf(ExprNode.Const(1), ExprNode.Const(3)))
            assertNotEquals(a, b)
        }

        @Test
        fun `Array2D nodes with same rows are equal`() {
            val a = ExprNode.Array2D(listOf(
                ExprNode.ArrayLiteral(longArrayOf(1, 2)),
                ExprNode.ArrayLiteral(longArrayOf(3, 4))
            ))
            val b = ExprNode.Array2D(listOf(
                ExprNode.ArrayLiteral(longArrayOf(1, 2)),
                ExprNode.ArrayLiteral(longArrayOf(3, 4))
            ))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class ArithmeticNodes {

        @Test
        fun `Sum nodes with same operands are equal`() {
            val a = ExprNode.Sum(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val b = ExprNode.Sum(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            assertEquals(a, b)
        }

        @Test
        fun `Sum nodes with different operand order are not equal`() {
            val a = ExprNode.Sum(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val b = ExprNode.Sum(listOf(ExprNode.Const(2), ExprNode.Const(1)))
            assertNotEquals(a, b) // Order matters in AST
        }

        @Test
        fun `Sub nodes with same operands are equal`() {
            val a = ExprNode.Sub(ExprNode.Const(5), ExprNode.Const(3))
            val b = ExprNode.Sub(ExprNode.Const(5), ExprNode.Const(3))
            assertEquals(a, b)
        }

        @Test
        fun `Prod nodes with same operands are equal`() {
            val a = ExprNode.Prod(listOf(ExprNode.Const(2), ExprNode.Const(3)))
            val b = ExprNode.Prod(listOf(ExprNode.Const(2), ExprNode.Const(3)))
            assertEquals(a, b)
        }

        @Test
        fun `Div nodes with same operands are equal`() {
            val a = ExprNode.Div(ExprNode.Const(10), ExprNode.Const(2))
            val b = ExprNode.Div(ExprNode.Const(10), ExprNode.Const(2))
            assertEquals(a, b)
        }

        @Test
        fun `Mod nodes with same operands are equal`() {
            val a = ExprNode.Mod(ExprNode.Const(10), ExprNode.Const(3))
            val b = ExprNode.Mod(ExprNode.Const(10), ExprNode.Const(3))
            assertEquals(a, b)
        }

        @Test
        fun `Neg nodes with same operand are equal`() {
            val a = ExprNode.Neg(ExprNode.Const(5))
            val b = ExprNode.Neg(ExprNode.Const(5))
            assertEquals(a, b)
        }

        @Test
        fun `sumNode helper creates Sum with two operands`() {
            val result = sumNode(ExprNode.Const(1), ExprNode.Const(2))
            assertTrue(result is ExprNode.Sum)
            assertEquals(2, (result as ExprNode.Sum).operands.size)
        }

        @Test
        fun `prodNode helper creates Prod with two operands`() {
            val result = prodNode(ExprNode.Const(2), ExprNode.Const(3))
            assertTrue(result is ExprNode.Prod)
            assertEquals(2, (result as ExprNode.Prod).operands.size)
        }
    }

    @Nested
    inner class ComparisonNodes {

        @Test
        fun `Eq nodes with same operands are equal`() {
            val a = ExprNode.Eq(ExprNode.Const(1), ExprNode.Const(1))
            val b = ExprNode.Eq(ExprNode.Const(1), ExprNode.Const(1))
            assertEquals(a, b)
        }

        @Test
        fun `Neq nodes with same operands are equal`() {
            val a = ExprNode.Neq(ExprNode.Const(1), ExprNode.Const(2))
            val b = ExprNode.Neq(ExprNode.Const(1), ExprNode.Const(2))
            assertEquals(a, b)
        }

        @Test
        fun `Lt nodes with same operands are equal`() {
            val a = ExprNode.Lt(ExprNode.Const(1), ExprNode.Const(2))
            val b = ExprNode.Lt(ExprNode.Const(1), ExprNode.Const(2))
            assertEquals(a, b)
        }

        @Test
        fun `Leq nodes with same operands are equal`() {
            val a = ExprNode.Leq(ExprNode.Const(1), ExprNode.Const(2))
            val b = ExprNode.Leq(ExprNode.Const(1), ExprNode.Const(2))
            assertEquals(a, b)
        }

        @Test
        fun `Gt nodes with same operands are equal`() {
            val a = ExprNode.Gt(ExprNode.Const(2), ExprNode.Const(1))
            val b = ExprNode.Gt(ExprNode.Const(2), ExprNode.Const(1))
            assertEquals(a, b)
        }

        @Test
        fun `Geq nodes with same operands are equal`() {
            val a = ExprNode.Geq(ExprNode.Const(2), ExprNode.Const(1))
            val b = ExprNode.Geq(ExprNode.Const(2), ExprNode.Const(1))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class LogicalNodes {

        @Test
        fun `And nodes with same operands are equal`() {
            val a = ExprNode.And(listOf(ExprNode.Const(1), ExprNode.Const(1)))
            val b = ExprNode.And(listOf(ExprNode.Const(1), ExprNode.Const(1)))
            assertEquals(a, b)
        }

        @Test
        fun `Or nodes with same operands are equal`() {
            val a = ExprNode.Or(listOf(ExprNode.Const(0), ExprNode.Const(1)))
            val b = ExprNode.Or(listOf(ExprNode.Const(0), ExprNode.Const(1)))
            assertEquals(a, b)
        }

        @Test
        fun `Not nodes with same operand are equal`() {
            val a = ExprNode.Not(ExprNode.Const(0))
            val b = ExprNode.Not(ExprNode.Const(0))
            assertEquals(a, b)
        }

        @Test
        fun `Xor nodes with same operands are equal`() {
            val a = ExprNode.Xor(listOf(ExprNode.Const(1), ExprNode.Const(0)))
            val b = ExprNode.Xor(listOf(ExprNode.Const(1), ExprNode.Const(0)))
            assertEquals(a, b)
        }

        @Test
        fun `andNode helper creates And with two operands`() {
            val result = andNode(ExprNode.Const(1), ExprNode.Const(1))
            assertTrue(result is ExprNode.And)
            assertEquals(2, (result as ExprNode.And).operands.size)
        }

        @Test
        fun `orNode helper creates Or with two operands`() {
            val result = orNode(ExprNode.Const(0), ExprNode.Const(1))
            assertTrue(result is ExprNode.Or)
            assertEquals(2, (result as ExprNode.Or).operands.size)
        }
    }

    @Nested
    inner class ConditionalNodes {

        @Test
        fun `If nodes with same parts are equal`() {
            val a = ExprNode.If(ExprNode.Const(1), ExprNode.Const(10), ExprNode.Const(20))
            val b = ExprNode.If(ExprNode.Const(1), ExprNode.Const(10), ExprNode.Const(20))
            assertEquals(a, b)
        }

        @Test
        fun `If nodes with different condition are not equal`() {
            val a = ExprNode.If(ExprNode.Const(1), ExprNode.Const(10), ExprNode.Const(20))
            val b = ExprNode.If(ExprNode.Const(0), ExprNode.Const(10), ExprNode.Const(20))
            assertNotEquals(a, b)
        }

        @Test
        fun `If nodes with different ifTrue are not equal`() {
            val a = ExprNode.If(ExprNode.Const(1), ExprNode.Const(10), ExprNode.Const(20))
            val b = ExprNode.If(ExprNode.Const(1), ExprNode.Const(15), ExprNode.Const(20))
            assertNotEquals(a, b)
        }

        @Test
        fun `If nodes with different ifFalse are not equal`() {
            val a = ExprNode.If(ExprNode.Const(1), ExprNode.Const(10), ExprNode.Const(20))
            val b = ExprNode.If(ExprNode.Const(1), ExprNode.Const(10), ExprNode.Const(25))
            assertNotEquals(a, b)
        }
    }

    @Nested
    inner class CollectionNodes {

        @Test
        fun `At nodes with same array and index are equal`() {
            val arr = ExprNode.ArrayLiteral(longArrayOf(1, 2, 3))
            val a = ExprNode.At(arr, ExprNode.Const(0))
            val b = ExprNode.At(arr, ExprNode.Const(0))
            assertEquals(a, b)
        }

        @Test
        fun `At2D nodes with same array and indices are equal`() {
            val arr = ExprNode.Array2D(listOf(
                ExprNode.ArrayLiteral(longArrayOf(1, 2)),
                ExprNode.ArrayLiteral(longArrayOf(3, 4))
            ))
            val a = ExprNode.At2D(arr, ExprNode.Const(0), ExprNode.Const(1))
            val b = ExprNode.At2D(arr, ExprNode.Const(0), ExprNode.Const(1))
            assertEquals(a, b)
        }

        @Test
        fun `Count nodes with same collection are equal`() {
            val arr = ExprNode.ArrayOf(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val a = ExprNode.Count(arr)
            val b = ExprNode.Count(arr)
            assertEquals(a, b)
        }

        @Test
        fun `Contains nodes with same parts are equal`() {
            val arr = ExprNode.ArrayOf(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val a = ExprNode.Contains(arr, ExprNode.Const(1))
            val b = ExprNode.Contains(arr, ExprNode.Const(1))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class MathNodes {

        @Test
        fun `Abs nodes with same operand are equal`() {
            val a = ExprNode.Abs(ExprNode.Const(-5))
            val b = ExprNode.Abs(ExprNode.Const(-5))
            assertEquals(a, b)
        }

        @Test
        fun `Sqrt nodes with same operand are equal`() {
            val a = ExprNode.Sqrt(ExprNode.Const(4))
            val b = ExprNode.Sqrt(ExprNode.Const(4))
            assertEquals(a, b)
        }

        @Test
        fun `Pow nodes with same base and exponent are equal`() {
            val a = ExprNode.Pow(ExprNode.Const(2), ExprNode.Const(3))
            val b = ExprNode.Pow(ExprNode.Const(2), ExprNode.Const(3))
            assertEquals(a, b)
        }

        @Test
        fun `Exp nodes with same operand are equal`() {
            val a = ExprNode.Exp(ExprNode.Const(1))
            val b = ExprNode.Exp(ExprNode.Const(1))
            assertEquals(a, b)
        }

        @Test
        fun `Log nodes with same operand are equal`() {
            val a = ExprNode.Log(ExprNode.Const(10))
            val b = ExprNode.Log(ExprNode.Const(10))
            assertEquals(a, b)
        }

        @Test
        fun `Ceil nodes with same operand are equal`() {
            val a = ExprNode.Ceil(ExprNode.ConstDouble(1.5))
            val b = ExprNode.Ceil(ExprNode.ConstDouble(1.5))
            assertEquals(a, b)
        }

        @Test
        fun `Floor nodes with same operand are equal`() {
            val a = ExprNode.Floor(ExprNode.ConstDouble(1.5))
            val b = ExprNode.Floor(ExprNode.ConstDouble(1.5))
            assertEquals(a, b)
        }

        @Test
        fun `Round nodes with same operand are equal`() {
            val a = ExprNode.Round(ExprNode.ConstDouble(1.5))
            val b = ExprNode.Round(ExprNode.ConstDouble(1.5))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class DomainNodes {

        @Test
        fun `InDomain nodes with same variable and domain are equal`() {
            val varNode = ExprNode.Var(1, VarType.INT)
            val domain = longArrayOf(1, 2, 3, 5, 7)
            val a = ExprNode.InDomain(varNode, domain)
            val b = ExprNode.InDomain(varNode, domain.clone())
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
        }

        @Test
        fun `InDomain nodes with different domains are not equal`() {
            val varNode = ExprNode.Var(1, VarType.INT)
            val a = ExprNode.InDomain(varNode, longArrayOf(1, 2, 3))
            val b = ExprNode.InDomain(varNode, longArrayOf(1, 2, 4))
            assertNotEquals(a, b)
        }

        @Test
        fun `InDomain nodes with different variables are not equal`() {
            val domain = longArrayOf(1, 2, 3)
            val a = ExprNode.InDomain(ExprNode.Var(1, VarType.INT), domain)
            val b = ExprNode.InDomain(ExprNode.Var(2, VarType.INT), domain)
            assertNotEquals(a, b)
        }
    }

    @Nested
    inner class AggregationNodes {

        @Test
        fun `Min nodes with same operands are equal`() {
            val a = ExprNode.Min(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val b = ExprNode.Min(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            assertEquals(a, b)
        }

        @Test
        fun `Max nodes with same operands are equal`() {
            val a = ExprNode.Max(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            val b = ExprNode.Max(listOf(ExprNode.Const(1), ExprNode.Const(2)))
            assertEquals(a, b)
        }
    }

    @Nested
    inner class SetNodes {

        @Test
        fun `Intersection nodes with same sets are equal`() {
            val set1 = ExprNode.Var(1, VarType.SET)
            val set2 = ExprNode.Var(2, VarType.SET)
            val a = ExprNode.Intersection(listOf(set1, set2))
            val b = ExprNode.Intersection(listOf(set1, set2))
            assertEquals(a, b)
        }

        @Test
        fun `Union nodes with same sets are equal`() {
            val set1 = ExprNode.Var(1, VarType.SET)
            val set2 = ExprNode.Var(2, VarType.SET)
            val a = ExprNode.Union(listOf(set1, set2))
            val b = ExprNode.Union(listOf(set1, set2))
            assertEquals(a, b)
        }
    }
}




