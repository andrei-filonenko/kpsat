package io.github.andreifilonenko.kpsat.dsl

// ============ BASE ERROR ============

/**
 * Root of all DSL errors. Enables unified error handling across the library.
 */
sealed interface DslError {
    val message: String
    val context: ErrorContext
}

/**
 * Contextual information attached to every error for debugging.
 */
data class ErrorContext(
    val operation: String,
    val location: String? = null,
    val additionalInfo: Map<String, Any> = emptyMap(),
)

// ============ EVALUATION ERRORS (DirectEvaluator) ============

sealed interface EvalError : DslError {

    /** Variable was referenced but never assigned a value */
    data class VariableNotSet(
        val varId: Int,
        val varType: VarType?,
        override val context: ErrorContext,
    ) : EvalError {
        override val message: String = "Variable $varId (type: $varType) not set"
    }

    /** Expected one type but got another */
    data class TypeMismatch(
        val expected: String,
        val actual: String,
        val value: Any,
        override val context: ErrorContext,
    ) : EvalError {
        override val message: String = "Type mismatch: expected $expected, got $actual (value: $value)"
    }

    /** Index out of bounds for array/list access */
    data class IndexOutOfBounds(
        val index: Long,
        val size: Int,
        override val context: ErrorContext,
    ) : EvalError {
        override val message: String = "Index $index out of bounds for collection of size $size"
    }

    /** Null encountered where value was expected */
    data class NullValue(
        val path: String,
        override val context: ErrorContext,
    ) : EvalError {
        override val message: String = "Null value at $path"
    }

    /** Cannot perform operation on given type */
    data class InvalidOperand(
        val operation: String,
        val operandType: String,
        override val context: ErrorContext,
    ) : EvalError {
        override val message: String = "Cannot perform $operation on $operandType"
    }

    /** Division by zero */
    data class DivisionByZero(
        override val context: ErrorContext,
    ) : EvalError {
        override val message: String = "Division by zero"
    }
}

// ============ COMPILATION ERRORS (ExprCompiler) ============

sealed interface CompileError : DslError {

    /** Variable ID not found in the variable registry */
    data class VariableNotFound(
        val varId: Int,
        override val context: ErrorContext,
    ) : CompileError {
        override val message: String = "Variable $varId not found in registry"
    }

    /** Operation not supported by CP-SAT solver */
    data class UnsupportedOperation(
        val operation: String,
        val reason: String,
        override val context: ErrorContext,
    ) : CompileError {
        override val message: String = "Unsupported operation '$operation': $reason"
    }

    /** Array must be accessed via At node, not directly */
    data class DirectArrayAccess(
        val arrayType: String,
        override val context: ErrorContext,
    ) : CompileError {
        override val message: String = "$arrayType must be accessed via At/At2D, not directly"
    }

    /** Hard constraint evaluates to constant false */
    data class ConstraintAlwaysFalse(
        val constraintName: String,
        override val context: ErrorContext,
    ) : CompileError {
        override val message: String = "Hard constraint '$constraintName' evaluates to false"
    }

    /** Variable index access requires constant index */
    data class NonConstantIndex(
        val indexType: String,
        override val context: ErrorContext,
    ) : CompileError {
        override val message: String = "2D array access requires constant indices, got $indexType"
    }

    /** Min/Max requires at least one operand */
    data class EmptyAggregation(
        val aggregationType: String,
        override val context: ErrorContext,
    ) : CompileError {
        override val message: String = "$aggregationType requires at least one operand"
    }
}

// ============ CONFIGURATION ERRORS (ConstraintSolverBuilder) ============

sealed interface ConfigError : DslError {

    /** Soft constraint with given name not found */
    data class SoftConstraintNotFound(
        val name: String,
        val availableNames: List<String>,
        override val context: ErrorContext,
    ) : ConfigError {
        override val message: String = "Soft constraint '$name' not found. Available: ${availableNames.joinToString()}"
    }

    /** Hard constraint with given name not found */
    data class HardConstraintNotFound(
        val name: String,
        val availableNames: List<String>,
        override val context: ErrorContext,
    ) : ConfigError {
        override val message: String = "Hard constraint '$name' not found. Available: ${availableNames.joinToString()}"
    }

    /** Objective with given name not found */
    data class ObjectiveNotFound(
        val name: String,
        val availableNames: List<String>,
        override val context: ErrorContext,
    ) : ConfigError {
        override val message: String = "Objective '$name' not found. Available: ${availableNames.joinToString()}"
    }

    /** Invalid weight value */
    data class InvalidWeight(
        val name: String,
        val weight: Int,
        val reason: String,
        override val context: ErrorContext,
    ) : ConfigError {
        override val message: String = "Invalid weight $weight for '$name': $reason"
    }
}

// ============ SOLVE ERRORS (replaces SolveException) ============

sealed interface SolveError : DslError {

    /** Model is infeasible - no solution exists */
    data class Infeasible(
        val failedConstraint: String?,
        val details: String?,
        override val context: ErrorContext,
    ) : SolveError {
        override val message: String = buildString {
            append("Model is infeasible")
            failedConstraint?.let { append(" (failed at: $it)") }
            details?.let { append(": $it") }
        }
    }

    /** Solver timed out before finding optimal solution */
    data class Timeout(
        val elapsedSeconds: Double,
        val limitSeconds: Long,
        val hasFeasibleSolution: Boolean,
        override val context: ErrorContext,
    ) : SolveError {
        override val message: String = "Solver timed out after ${elapsedSeconds}s (limit: ${limitSeconds}s). " +
            if (hasFeasibleSolution) "Feasible solution available." else "No solution found."
    }

    /** Model is invalid (structural problem) */
    data class InvalidModel(
        val reason: String,
        override val context: ErrorContext,
    ) : SolveError {
        override val message: String = "Invalid model: $reason"
    }

    /** Failed at specific priority level during lexicographic solve */
    data class LexicographicFailure(
        val priorityLevel: Int,
        val priorityName: String,
        val underlyingStatus: String,
        override val context: ErrorContext,
    ) : SolveError {
        override val message: String =
            "Lexicographic solve failed at priority $priorityLevel ($priorityName): $underlyingStatus"
    }

    /** Unknown solver state */
    data class Unknown(
        val solverStatus: String,
        override val context: ErrorContext,
    ) : SolveError {
        override val message: String = "Unknown solver state: $solverStatus"
    }
}

// ============ SCOPE ERRORS (ConstraintSolverScope) ============

sealed interface ScopeError : DslError {

    /** Operation not supported by the solver backend */
    data class UnsupportedVariable(
        val variableType: String,
        val solver: String,
        override val context: ErrorContext,
    ) : ScopeError {
        override val message: String = "$variableType variables not supported in $solver"
    }

    /** Solver not yet executed, cannot extract values */
    data class SolverNotExecuted(
        override val context: ErrorContext,
    ) : ScopeError {
        override val message: String = "Cannot extract values: solver has not been executed"
    }

    /** Variable not found in the solver scope */
    data class VariableNotFound(
        val varId: Int,
        override val context: ErrorContext,
    ) : ScopeError {
        override val message: String = "Variable $varId not found in solver scope"
    }

    /** Expression compilation failed */
    data class CompilationFailed(
        val details: String,
        override val context: ErrorContext,
    ) : ScopeError {
        override val message: String = "Expression compilation failed: $details"
    }
}

// ============ HELPER EXTENSIONS ============

/** Create error context from current operation */
fun errorContext(
    operation: String,
    location: String? = null,
    vararg info: Pair<String, Any>,
): ErrorContext = ErrorContext(
    operation = operation,
    location = location,
    additionalInfo = info.toMap(),
)

/** Convert any DslError to a human-readable report */
fun DslError.toReport(): String = buildString {
    appendLine("Error: $message")
    appendLine("  Operation: ${context.operation}")
    context.location?.let { appendLine("  Location: $it") }
    if (context.additionalInfo.isNotEmpty()) {
        appendLine("  Details:")
        append(
            context.additionalInfo.entries.joinToString("\n") { (k, v) ->
                "    $k: $v"
            }
        )
    }
}

