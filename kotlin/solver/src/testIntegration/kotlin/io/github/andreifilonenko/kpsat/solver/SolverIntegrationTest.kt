package io.github.andreifilonenko.kpsat.solver

import arrow.core.getOrElse
import io.github.andreifilonenko.kpsat.dsl.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Integration tests for the constraint solver.
 * These tests exercise the full OR-Tools integration.
 */
class SolverIntegrationTest {

    @Nested
    inner class BasicVariableTests {

        @Test
        fun `solve with single boolean variable`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.bool())
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            assertTrue(x == 0L || x == 1L)
        }

        @Test
        fun `solve with single integer variable`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            assertTrue(x in 0L..100L)
        }

        @Test
        fun `solve with multiple variables`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 10),
                        "y" to scope.int(0, 10),
                        "z" to scope.bool()
                    )
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertTrue(result.getValue("x").isRight())
            assertTrue(result.getValue("y").isRight())
            assertTrue(result.getValue("z").isRight())
        }
    }

    @Nested
    inner class HardConstraintTests {

        @Test
        fun `hard constraint enforces equality`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .hard("fix_x") { scope, vars ->
                    vars["x"]!! eq 42L
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertEquals(42L, result.getValue("x").getOrElse { fail(it.message) })
        }

        @Test
        fun `hard constraint enforces inequality`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 100),
                        "y" to scope.int(0, 100)
                    )
                }
                .hard("x_less_than_y") { scope, vars ->
                    vars["x"]!! lt vars["y"]!!
                }
                .hard("y_is_50") { scope, vars ->
                    vars["y"]!! eq 50L
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            val y = result.getValue("y").getOrElse { fail(it.message) }
            assertTrue(x < y)
            assertEquals(50L, y)
        }

        @Test
        fun `multiple hard constraints - sum constraint`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 50),
                        "y" to scope.int(0, 50)
                    )
                }
                .hard("sum_is_100") { scope, vars ->
                    (vars["x"]!! + vars["y"]!!) eq 100L
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            val y = result.getValue("y").getOrElse { fail(it.message) }
            assertEquals(100L, x + y)
        }

        @Test
        fun `infeasible hard constraints returns INFEASIBLE`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 10))
                }
                .hard("x_gt_10") { scope, vars ->
                    vars["x"]!! gt 10L
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertEquals(SolveStatus.INFEASIBLE, result.status)
            assertFalse(result.hasSolution)
        }

        @Test
        fun `disabled hard constraint is not enforced`() {
            val builder = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .hard("x_is_50") { scope, vars ->
                    vars["x"]!! eq 50L
                }
                .hard("x_is_impossible") { scope, vars ->
                    vars["x"]!! gt 1000L // Would make it infeasible
                }
            
            // Disable the impossible constraint
            builder.disable("x_is_impossible").getOrElse { fail(it.message) }
            
            val result = builder.build().solve().getOrElse { fail(it.message) }
            
            assertTrue(result.hasSolution)
            assertEquals(50L, result.getValue("x").getOrElse { fail(it.message) })
        }
    }

    @Nested
    inner class ObjectiveTests {

        @Test
        fun `maximize objective`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .maximize("max_x", priority = 0) { scope, vars ->
                    vars["x"]!!
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertEquals(100L, result.getValue("x").getOrElse { fail(it.message) })
        }

        @Test
        fun `minimize objective`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .minimize("min_x", priority = 0) { scope, vars ->
                    vars["x"]!!
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertEquals(0L, result.getValue("x").getOrElse { fail(it.message) })
        }

        @Test
        fun `maximize with constraint`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 100),
                        "y" to scope.int(0, 100)
                    )
                }
                .hard("sum_limit") { scope, vars ->
                    (vars["x"]!! + vars["y"]!!) leq 150L
                }
                .maximize("max_product", priority = 0) { scope, vars ->
                    vars["x"]!! + vars["y"]!!
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            val y = result.getValue("y").getOrElse { fail(it.message) }
            assertEquals(150L, x + y)
        }

        @Test
        fun `disabled objective is not used`() {
            val builder = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .maximize("max_x", priority = 1) { scope, vars ->
                    vars["x"]!!
                }
                .minimize("min_x", priority = 0) { scope, vars ->
                    vars["x"]!!
                }
            
            // Disable max_x, leaving only min_x
            builder.disable("max_x").getOrElse { fail(it.message) }
            
            val result = builder.build().solve().getOrElse { fail(it.message) }
            
            assertTrue(result.hasSolution)
            assertEquals(0L, result.getValue("x").getOrElse { fail(it.message) }) // Minimized
        }
    }

    @Nested
    inner class SoftConstraintTests {

        @Test
        fun `soft constraint penalty is minimized`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .penalize("prefer_50", weight = 1, priority = 0) { scope, vars ->
                    abs(vars["x"]!! - 50L)
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertEquals(50L, result.getValue("x").getOrElse { fail(it.message) })
        }

        @Test
        fun `soft constraint with weight`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 100),
                        "y" to scope.int(0, 100)
                    )
                }
                .hard("sum_is_100") { scope, vars ->
                    (vars["x"]!! + vars["y"]!!) eq 100L
                }
                .penalize("prefer_x_high", weight = 10, priority = 0) { scope, vars ->
                    100L - vars["x"]!!
                }
                .penalize("prefer_y_high", weight = 1, priority = 0) { scope, vars ->
                    100L - vars["y"]!!
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            val y = result.getValue("y").getOrElse { fail(it.message) }
            // x should be higher due to higher weight
            assertTrue(x >= y)
        }

        @Test
        fun `modifying soft constraint weight`() {
            val builder = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .penalize("prefer_low", weight = 1, priority = 0) { scope, vars ->
                    vars["x"]!!
                }
            
            builder.setWeight("prefer_low", 100).getOrElse { fail(it.message) }
            
            val constraints = builder.getSoftConstraints()
            assertEquals(100, constraints.find { it.name == "prefer_low" }?.weight)
        }

        @Test
        fun `negative weight acts as reward - maximizes value`() {
            // Negative weight means we want to MAXIMIZE the expression
            // (minimize the negative of it)
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 100))
                }
                .penalize("reward_high_x", weight = -1, priority = 0) { scope, vars ->
                    vars["x"]!!  // Higher x = more reward
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            // With negative weight, we're rewarding high values of x
            // so x should be maximized to 100
            assertEquals(100L, result.getValue("x").getOrElse { fail(it.message) })
        }

        @Test
        fun `mixed positive and negative weights`() {
            // Mix of penalties (positive weight) and rewards (negative weight)
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 100),
                        "y" to scope.int(0, 100)
                    )
                }
                .hard("sum_limit") { scope, vars ->
                    (vars["x"]!! + vars["y"]!!) leq 100L
                }
                // Penalty: prefer x to be low (weight 1)
                .penalize("prefer_low_x", weight = 1, priority = 0) { scope, vars ->
                    vars["x"]!!
                }
                // Reward: prefer y to be high (weight -10, stronger)
                .penalize("reward_high_y", weight = -10, priority = 0) { scope, vars ->
                    vars["y"]!!
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            val y = result.getValue("y").getOrElse { fail(it.message) }
            
            // The reward for y is 10x stronger than penalty for x
            // So y should be high (close to 100) and x should be low (close to 0)
            assertTrue(y > x, "y ($y) should be greater than x ($x) due to stronger reward")
            assertTrue(y >= 90, "y ($y) should be high (>= 90) due to reward")
        }

        @Test
        fun `negative weight with explicit bounds`() {
            // Test that negative weights work with explicit bounds
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 50))
                }
                .penalize(
                    "reward_x",
                    weight = -5,
                    priority = 0,
                    minBound = 0L,
                    maxBound = 50L
                ) { scope, vars ->
                    vars["x"]!!
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            // Reward should maximize x
            assertEquals(50L, result.getValue("x").getOrElse { fail(it.message) })
        }
    }

    @Nested
    inner class MultiObjectiveTests {

        @Test
        fun `lexicographic optimization with priorities`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 10),
                        "y" to scope.int(0, 10)
                    )
                }
                .hard("sum_limit") { scope, vars ->
                    (vars["x"]!! + vars["y"]!!) leq 15L
                }
                // First maximize x (higher priority)
                .maximize("max_x", priority = 2) { scope, vars ->
                    vars["x"]!!
                }
                // Then maximize y (lower priority)
                .maximize("max_y", priority = 1) { scope, vars ->
                    vars["y"]!!
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val x = result.getValue("x").getOrElse { fail(it.message) }
            val y = result.getValue("y").getOrElse { fail(it.message) }
            // x should be maxed first (10), then y should be 5
            assertEquals(10L, x)
            assertEquals(5L, y)
        }
    }

    @Nested
    inner class ArrayConstraintTests {

        @Test
        fun `sum of array variables`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    val arr = scope.intArray(5, 0, 10)
                    mapOf("arr" to arr)
                }
                .hard("sum_is_25") { scope, vars ->
                    val arr = vars["arr"]!!
                    sum(0 until 5) { i -> arr[i] } eq 25L
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
        }

        @Test
        fun `element constraint with constant array`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    val costs = scope.constArray(longArrayOf(10, 20, 30, 40, 50))
                    val idx = scope.int(0, 4)
                    mapOf("costs" to costs, "idx" to idx)
                }
                .hard("selected_cost_is_30") { scope, vars ->
                    vars["costs"]!![vars["idx"]!!] eq 30L
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertEquals(2L, result.getValue("idx").getOrElse { fail(it.message) }) // Index of 30 in the array
        }
    }

    @Nested
    inner class ConstraintPatternTests {

        @Test
        fun `all different constraint via pairwise neq`() {
            val n = 4
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    (0 until n).associate { i ->
                        "x$i" to scope.int(1, n.toLong())
                    }
                }
                .hard("all_different") { scope, vars ->
                    val xs = (0 until n).map { vars["x$it"]!! }
                    allDifferent(xs)
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val values = (0 until n).map { result.getValue("x$it").getOrElse { fail(it.message) } }.toSet()
            assertEquals(n, values.size) // All different
        }

        @Test
        fun `forAll constraint`() {
            val n = 5
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    (0 until n).associate { i ->
                        "x$i" to scope.int(0, 100)
                    }
                }
                .hard("all_positive") { scope, vars ->
                    forAll(0 until n) { i -> vars["x$i"]!! geq 10L }
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            for (i in 0 until n) {
                assertTrue(result.getValue("x$i").getOrElse { fail(it.message) } >= 10L)
            }
        }

        @Test
        fun `exists constraint`() {
            val n = 5
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    (0 until n).associate { i ->
                        "x$i" to scope.int(0, 10)
                    }
                }
                .hard("one_is_max") { scope, vars ->
                    exists(0 until n) { i -> vars["x$i"]!! eq 10L }
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val hasMax = (0 until n).any { result.getValue("x$it").getOrElse { fail(it.message) } == 10L }
            assertTrue(hasMax)
        }

        @Test
        fun `exactly one constraint`() {
            val n = 5
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    (0 until n).associate { i ->
                        "b$i" to scope.bool()
                    }
                }
                .hard("exactly_one_true") { scope, vars ->
                    val bs = (0 until n).map { vars["b$it"]!! }
                    exactly(1, bs)
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val trueCount = (0 until n).count { result.getValue("b$it").getOrElse { fail(it.message) } == 1L }
            assertEquals(1, trueCount)
        }
    }

    @Nested
    inner class SolverConfigurationTests {

        @Test
        fun `time limit configuration does not throw`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(1)
                .numWorkers(2)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 10))
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
        }

        @Test
        fun `log progress configuration does not throw`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(5)
                .logProgress(false)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 10))
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
        }
    }

    @Nested
    inner class ResultExtractionTests {

        @Test
        fun `getAllValues returns all variable values`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "a" to scope.int(0, 10),
                        "b" to scope.int(0, 10),
                        "c" to scope.int(0, 10)
                    )
                }
                .hard("a_is_1") { _, vars -> vars["a"]!! eq 1L }
                .hard("b_is_2") { _, vars -> vars["b"]!! eq 2L }
                .hard("c_is_3") { _, vars -> vars["c"]!! eq 3L }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            val allValues = result.getAllValues()
            assertEquals(1L, allValues["a"])
            assertEquals(2L, allValues["b"])
            assertEquals(3L, allValues["c"])
        }

        @Test
        fun `getBoolValue returns boolean`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("b" to scope.bool())
                }
                .hard("b_is_true") { _, vars -> vars["b"]!! eq 1L }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertEquals(true, result.getBoolValue("b").getOrElse { fail(it.message) })
        }

        @Test
        fun `extract solution with custom extractor`() {
            data class Solution(val x: Long, val y: Long, val sum: Long)

            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf(
                        "x" to scope.int(0, 100),
                        "y" to scope.int(0, 100)
                    )
                }
                .hard("x_is_30") { _, vars -> vars["x"]!! eq 30L }
                .hard("y_is_70") { _, vars -> vars["y"]!! eq 70L }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)

            val solution = result.extract { r ->
                Solution(
                    x = r.getValue("x").getOrElse { fail(it.message) },
                    y = r.getValue("y").getOrElse { fail(it.message) },
                    sum = r.getValue("x").getOrElse { fail(it.message) } + r.getValue("y").getOrElse { fail(it.message) }
                )
            }.getOrThrow()

            assertEquals(30L, solution.x)
            assertEquals(70L, solution.y)
            assertEquals(100L, solution.sum)
        }

        @Test
        fun `summary returns formatted string`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 10))
                }
                .maximize("max_x") { _, vars -> vars["x"]!! }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            val summary = result.summary()
            assertTrue(summary.contains("Solve Result"))
            assertTrue(summary.contains("Status"))
        }
    }

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `setWeight returns Left on unknown constraint`() {
            val builder = ConstraintSolverBuilder()
                .variables { scope -> mapOf("x" to scope.int(0, 10)) }

            val result = builder.setWeight("nonexistent", 10)
            assertTrue(result.isLeft())
        }

        @Test
        fun `setPriority returns Left on unknown constraint`() {
            val builder = ConstraintSolverBuilder()
                .variables { scope -> mapOf("x" to scope.int(0, 10)) }

            val result = builder.setPriority("nonexistent", 10)
            assertTrue(result.isLeft())
        }

        @Test
        fun `enable returns Left on unknown constraint`() {
            val builder = ConstraintSolverBuilder()
                .variables { scope -> mapOf("x" to scope.int(0, 10)) }

            val result = builder.enable("nonexistent")
            assertTrue(result.isLeft())
        }

        @Test
        fun `disable returns Left on unknown constraint`() {
            val builder = ConstraintSolverBuilder()
                .variables { scope -> mapOf("x" to scope.int(0, 10)) }

            val result = builder.disable("nonexistent")
            assertTrue(result.isLeft())
        }

        @Test
        fun `solve returns Right with INFEASIBLE status on infeasible model`() {
            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 10))
                }
                .hard("impossible") { _, vars -> vars["x"]!! gt 100L }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertEquals(SolveStatus.INFEASIBLE, result.status)
            assertFalse(result.hasSolution)
        }
    }

    @Nested
    inner class CallbackTests {

        @Test
        fun `onSolutionReady callback is called`() {
            var callbackCalled = false
            var extractedX: Long? = null

            val result = ConstraintSolverBuilder()
                .timeLimit(10)
                .variables { scope ->
                    mapOf("x" to scope.int(0, 10))
                }
                .hard("x_is_5") { _, vars -> vars["x"]!! eq 5L }
                .onSolutionReady { scope, vars ->
                    callbackCalled = true
                    extractedX = scope.value(vars["x"]!!).getOrElse { null }
                }
                .build()
                .solve()
                .getOrElse { fail(it.message) }

            assertTrue(result.hasSolution)
            assertTrue(callbackCalled)
            assertEquals(5L, extractedX)
        }
    }
}

