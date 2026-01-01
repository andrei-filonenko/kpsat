package io.github.andreifilonenko.kpsat.solver

import io.github.andreifilonenko.kpsat.dsl.ConstraintScope
import io.github.andreifilonenko.kpsat.dsl.Expr
import io.github.andreifilonenko.kpsat.dsl.ExprNode
import io.github.andreifilonenko.kpsat.dsl.VarType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.fail

/**
 * Tests for constraint definition data classes.
 * These tests avoid instantiating OR-Tools components.
 */
class ConstraintDefinitionTest {

    // Helper to create a dummy constraint function
    private val dummyConstraint: (ConstraintScope, Map<String, Expr>) -> Expr = { _, _ ->
        Expr(ExprNode.Const(1L))
    }

    @Nested
    inner class HardConstraintDefTests {

        @Test
        fun `HardConstraintDef is enabled by default`() {
            val def = HardConstraintDef(
                name = "test_constraint",
                constraint = dummyConstraint
            )
            assertTrue(def.enabled)
        }

        @Test
        fun `HardConstraintDef can be disabled`() {
            val def = HardConstraintDef(
                name = "test_constraint",
                constraint = dummyConstraint,
                enabled = false
            )
            assertFalse(def.enabled)
        }

        @Test
        fun `HardConstraintDef enabled can be toggled`() {
            val def = HardConstraintDef(
                name = "test_constraint",
                constraint = dummyConstraint
            )
            assertTrue(def.enabled)
            
            def.enabled = false
            assertFalse(def.enabled)
            
            def.enabled = true
            assertTrue(def.enabled)
        }

        @Test
        fun `HardConstraintDef stores name correctly`() {
            val def = HardConstraintDef(
                name = "capacity_limit",
                constraint = dummyConstraint
            )
            assertEquals("capacity_limit", def.name)
        }

        @Test
        fun `HardConstraintDef stores constraint function`() {
            val constraint: (ConstraintScope, Map<String, Expr>) -> Expr = { _, _ ->
                Expr(ExprNode.Const(1L))
            }
            
            val def = HardConstraintDef(
                name = "test",
                constraint = constraint
            )
            
            // Verify the constraint function is stored
            assertEquals(constraint, def.constraint)
        }
    }

    @Nested
    inner class SoftConstraintDefTests {

        @Test
        fun `SoftConstraintDef is enabled by default`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 10,
                priority = 1,
                minBound = null,
                maxBound = null,
                penalty = dummyConstraint
            )
            assertTrue(def.enabled)
        }

        @Test
        fun `SoftConstraintDef stores weight correctly`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 100,
                priority = 1,
                minBound = null,
                maxBound = null,
                penalty = dummyConstraint
            )
            assertEquals(100, def.weight)
        }

        @Test
        fun `SoftConstraintDef weight can be modified`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 10,
                priority = 1,
                minBound = null,
                maxBound = null,
                penalty = dummyConstraint
            )
            
            def.weight = 50
            assertEquals(50, def.weight)
        }

        @Test
        fun `SoftConstraintDef stores priority correctly`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 10,
                priority = 5,
                minBound = null,
                maxBound = null,
                penalty = dummyConstraint
            )
            assertEquals(5, def.priority)
        }

        @Test
        fun `SoftConstraintDef priority can be modified`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 10,
                priority = 1,
                minBound = null,
                maxBound = null,
                penalty = dummyConstraint
            )
            
            def.priority = 10
            assertEquals(10, def.priority)
        }

        @Test
        fun `SoftConstraintDef stores bounds correctly`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 10,
                priority = 1,
                minBound = 0L,
                maxBound = 100L,
                penalty = dummyConstraint
            )
            assertEquals(0L, def.minBound)
            assertEquals(100L, def.maxBound)
        }

        @Test
        fun `SoftConstraintDef with null bounds`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 10,
                priority = 1,
                minBound = null,
                maxBound = null,
                penalty = dummyConstraint
            )
            assertEquals(null, def.minBound)
            assertEquals(null, def.maxBound)
        }

        @Test
        fun `SoftConstraintDef enabled can be toggled`() {
            val def = SoftConstraintDef(
                name = "soft_test",
                weight = 10,
                priority = 1,
                minBound = null,
                maxBound = null,
                penalty = dummyConstraint
            )
            
            def.enabled = false
            assertFalse(def.enabled)
            
            def.enabled = true
            assertTrue(def.enabled)
        }
    }

    @Nested
    inner class ObjectiveDefTests {

        @Test
        fun `ObjectiveDef is enabled by default`() {
            val def = ObjectiveDef(
                name = "maximize_profit",
                direction = ObjectiveDirection.MAXIMIZE,
                priority = 1,
                expr = dummyConstraint
            )
            assertTrue(def.enabled)
        }

        @Test
        fun `ObjectiveDef stores direction correctly - MAXIMIZE`() {
            val def = ObjectiveDef(
                name = "maximize_profit",
                direction = ObjectiveDirection.MAXIMIZE,
                priority = 1,
                expr = dummyConstraint
            )
            assertEquals(ObjectiveDirection.MAXIMIZE, def.direction)
        }

        @Test
        fun `ObjectiveDef stores direction correctly - MINIMIZE`() {
            val def = ObjectiveDef(
                name = "minimize_cost",
                direction = ObjectiveDirection.MINIMIZE,
                priority = 1,
                expr = dummyConstraint
            )
            assertEquals(ObjectiveDirection.MINIMIZE, def.direction)
        }

        @Test
        fun `ObjectiveDef stores priority correctly`() {
            val def = ObjectiveDef(
                name = "test_obj",
                direction = ObjectiveDirection.MAXIMIZE,
                priority = 5,
                expr = dummyConstraint
            )
            assertEquals(5, def.priority)
        }

        @Test
        fun `ObjectiveDef priority can be modified`() {
            val def = ObjectiveDef(
                name = "test_obj",
                direction = ObjectiveDirection.MAXIMIZE,
                priority = 1,
                expr = dummyConstraint
            )
            
            def.priority = 10
            assertEquals(10, def.priority)
        }

        @Test
        fun `ObjectiveDef enabled can be toggled`() {
            val def = ObjectiveDef(
                name = "test_obj",
                direction = ObjectiveDirection.MAXIMIZE,
                priority = 1,
                expr = dummyConstraint
            )
            
            def.enabled = false
            assertFalse(def.enabled)
            
            def.enabled = true
            assertTrue(def.enabled)
        }
    }

    @Nested
    inner class PriorityLevelTests {

        @Test
        fun `PriorityLevel stores priority correctly`() {
            val level = PriorityLevel(
                priority = 5,
                objectives = emptyList()
            )
            assertEquals(5, level.priority)
        }

        @Test
        fun `PriorityLevel with empty objectives`() {
            val level = PriorityLevel(
                priority = 1,
                objectives = emptyList()
            )
            assertTrue(level.objectives.isEmpty())
        }
    }

    @Nested
    inner class HardConstraintSpecTests {

        @Test
        fun `HardConstraintSpec stores name correctly`() {
            val expr = Expr(ExprNode.Const(1L))
            val spec = HardConstraintSpec(
                name = "constraint_1",
                constraint = expr
            )
            assertEquals("constraint_1", spec.name)
        }

        @Test
        fun `HardConstraintSpec stores constraint correctly`() {
            val expr = Expr(ExprNode.Const(42L))
            val spec = HardConstraintSpec(
                name = "test",
                constraint = expr
            )
            assertEquals(expr, spec.constraint)
        }

        @Test
        fun `HardConstraintSpec equality works`() {
            val expr = Expr(ExprNode.Const(1L))
            val spec1 = HardConstraintSpec("test", expr)
            val spec2 = HardConstraintSpec("test", expr)
            assertEquals(spec1, spec2)
        }
    }

    @Nested
    inner class ObjectiveSpecTests {

        @Test
        fun `ObjectiveSpec stores all fields correctly`() {
            val expr = Expr(ExprNode.Const(100L))
            val spec = ObjectiveSpec(
                name = "profit",
                expr = expr,
                direction = ObjectiveDirection.MAXIMIZE,
                priority = 2
            )
            
            assertEquals("profit", spec.name)
            assertEquals(expr, spec.expr)
            assertEquals(ObjectiveDirection.MAXIMIZE, spec.direction)
            assertEquals(2, spec.priority)
        }

        @Test
        fun `ObjectiveSpec equality works`() {
            val expr = Expr(ExprNode.Const(100L))
            val spec1 = ObjectiveSpec("test", expr, ObjectiveDirection.MINIMIZE, 1)
            val spec2 = ObjectiveSpec("test", expr, ObjectiveDirection.MINIMIZE, 1)
            assertEquals(spec1, spec2)
        }

        @Test
        fun `ObjectiveSpec with MINIMIZE direction`() {
            val expr = Expr(ExprNode.Const(0L))
            val spec = ObjectiveSpec(
                name = "cost",
                expr = expr,
                direction = ObjectiveDirection.MINIMIZE,
                priority = 1
            )
            assertEquals(ObjectiveDirection.MINIMIZE, spec.direction)
        }
    }

    @Nested
    inner class CompiledExprTests {

        @Test
        fun `CompiledExpr Constant stores value`() {
            val compiled = CompiledExpr.Constant(42L)
            assertEquals(42L, compiled.value)
        }

        @Test
        fun `CompiledExpr Constant toLong works`() {
            val compiled = CompiledExpr.Constant(100L)
            assertEquals(100L, compiled.toLong())
        }

        @Test
        fun `CompiledExpr Constant isConstant returns true`() {
            val compiled = CompiledExpr.Constant(50L)
            assertTrue(compiled.isConstant())
        }

        @Test
        fun `CompiledExpr of Long creates Constant`() {
            val compiled = CompiledExpr.of(123L)
            assertTrue(compiled is CompiledExpr.Constant)
            assertEquals(123L, (compiled as CompiledExpr.Constant).value)
        }
    }
}

