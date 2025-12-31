package io.github.andreifilonenko.kpsat.solver

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Tests for SolveResult, SolveStatus, SolutionExtractor, and SolveException.
 * These tests avoid instantiating OR-Tools components.
 */
class SolveResultTest {

    @Nested
    inner class SolveStatusTests {

        @Test
        fun `all status values exist`() {
            val statuses = SolveStatus.entries
            assertEquals(5, statuses.size)
            assertTrue(statuses.contains(SolveStatus.OPTIMAL))
            assertTrue(statuses.contains(SolveStatus.FEASIBLE))
            assertTrue(statuses.contains(SolveStatus.INFEASIBLE))
            assertTrue(statuses.contains(SolveStatus.UNKNOWN))
            assertTrue(statuses.contains(SolveStatus.MODEL_INVALID))
        }

        @Test
        fun `status names are correct`() {
            assertEquals("OPTIMAL", SolveStatus.OPTIMAL.name)
            assertEquals("FEASIBLE", SolveStatus.FEASIBLE.name)
            assertEquals("INFEASIBLE", SolveStatus.INFEASIBLE.name)
            assertEquals("UNKNOWN", SolveStatus.UNKNOWN.name)
            assertEquals("MODEL_INVALID", SolveStatus.MODEL_INVALID.name)
        }

        @Test
        fun `status ordinals are consistent`() {
            assertEquals(0, SolveStatus.OPTIMAL.ordinal)
            assertEquals(1, SolveStatus.FEASIBLE.ordinal)
            assertEquals(2, SolveStatus.INFEASIBLE.ordinal)
            assertEquals(3, SolveStatus.UNKNOWN.ordinal)
            assertEquals(4, SolveStatus.MODEL_INVALID.ordinal)
        }
    }

    @Nested
    inner class SolveExceptionTests {

        @Test
        fun `exception contains status`() {
            val exception = SolveException(SolveStatus.INFEASIBLE, "No solution found")
            assertEquals(SolveStatus.INFEASIBLE, exception.status)
        }

        @Test
        fun `exception contains message`() {
            val exception = SolveException(SolveStatus.UNKNOWN, "Timeout reached")
            assertEquals("Timeout reached", exception.message)
        }

        @Test
        fun `exception contains cause`() {
            val cause = RuntimeException("Original error")
            val exception = SolveException(SolveStatus.MODEL_INVALID, "Model error", cause)
            assertEquals(cause, exception.cause)
        }

        @Test
        fun `exception can be thrown and caught`() {
            val thrown = assertThrows<SolveException> {
                throw SolveException(SolveStatus.INFEASIBLE, "Test exception")
            }
            assertEquals(SolveStatus.INFEASIBLE, thrown.status)
            assertEquals("Test exception", thrown.message)
        }

        @Test
        fun `exception is a RuntimeException`() {
            val exception = SolveException(SolveStatus.UNKNOWN, "Test")
            assertTrue(exception is RuntimeException)
        }
    }

    @Nested
    inner class ObjectiveDirectionTests {

        @Test
        fun `all directions exist`() {
            val directions = ObjectiveDirection.entries
            assertEquals(2, directions.size)
            assertTrue(directions.contains(ObjectiveDirection.MAXIMIZE))
            assertTrue(directions.contains(ObjectiveDirection.MINIMIZE))
        }

        @Test
        fun `direction names are correct`() {
            assertEquals("MAXIMIZE", ObjectiveDirection.MAXIMIZE.name)
            assertEquals("MINIMIZE", ObjectiveDirection.MINIMIZE.name)
        }
    }

    @Nested
    inner class SolveProgressTests {

        @Test
        fun `SolveProgress contains all fields`() {
            val progress = SolveProgress(
                objectiveValue = 100.5,
                bestBound = 95.0,
                elapsedSeconds = 5.5,
                solutionCount = 3
            )
            
            assertEquals(100.5, progress.objectiveValue, 0.001)
            assertEquals(95.0, progress.bestBound, 0.001)
            assertEquals(5.5, progress.elapsedSeconds, 0.001)
            assertEquals(3, progress.solutionCount)
        }

        @Test
        fun `SolveProgress equality works`() {
            val progress1 = SolveProgress(100.0, 90.0, 1.0, 1)
            val progress2 = SolveProgress(100.0, 90.0, 1.0, 1)
            assertEquals(progress1, progress2)
        }

        @Test
        fun `SolveProgress copy works`() {
            val original = SolveProgress(100.0, 90.0, 1.0, 1)
            val copy = original.copy(solutionCount = 5)
            assertEquals(100.0, copy.objectiveValue, 0.001)
            assertEquals(5, copy.solutionCount)
        }
    }
}




