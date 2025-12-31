@file:Suppress("TooManyFunctions")

package io.github.andreifilonenko.kpsat.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr
import io.github.andreifilonenko.kpsat.dsl.ConstraintScope
import io.github.andreifilonenko.kpsat.dsl.Expr

/**
 * Lexicographic multi-objective solver.
 * 
 * CP-SAT only supports a single objective. This solver implements lexicographic
 * optimization by:
 * 1. Grouping objectives by priority (higher priority = more important)
 * 2. Solving for the highest priority objective first
 * 3. Fixing that objective's optimal value as a constraint
 * 4. Solving for the next priority level, and so on
 * 
 * Soft constraints are treated as objectives to minimize (penalties).
 * They are normalized within each priority level using PenaltyNormalizer.
 */
class LexicographicSolver(
    private val model: CpModel,
    private val scope: ConstraintSolverScope,
    private val vars: Map<String, Expr>,
    private val softConstraintDefs: List<SoftConstraintDef>,
    private val objectiveDefs: List<ObjectiveDef>,
    private val timeLimitSeconds: Long,
    private val numSearchWorkers: Int,
    private val logSearchProgress: Boolean,
    private val onProgressCallback: ((SolveProgress) -> Unit)?,
) {
    
    private val penaltyNormalizer = PenaltyNormalizer(model, scope)
    private val objectiveResults = mutableMapOf<String, Long>()
    
    /**
     * Solve the model using lexicographic optimization.
     */
    fun solve(onSolutionCallback: ((ConstraintSolverScope, Map<String, Expr>) -> Unit)?): SolveResult {
        
        val softObjectives = penaltyNormalizer.processConstraints(vars, softConstraintDefs)
        
        val explicitObjectives = processExplicitObjectives()
        
        val allObjectives = buildAllObjectives(softObjectives, explicitObjectives)
        
        if (allObjectives.isEmpty()) {
            return solveFeasibility(onSolutionCallback)
        }
        
        return solveLexicographic(allObjectives, onSolutionCallback)
    }
    
    private fun processExplicitObjectives(): Map<Int, List<LexObjective>> {
        val result = mutableMapOf<Int, MutableList<LexObjective>>()
        
        for (objDef in objectiveDefs) {
            with(scope) {
                val expr = objDef.expr(scope, vars)
                val compiled = scope.compiler.compile(expr)
                val objVar = when (compiled) {
                    is CompiledExpr.Variable -> compiled.intVar
                    is CompiledExpr.Constant -> model.newConstant(compiled.value)
                }
                
                val lexObj = LexObjective(
                    name = objDef.name,
                    variable = objVar,
                    direction = objDef.direction,
                    priority = objDef.priority,
                    isSoftPenalty = false,
                )
                
                result.getOrPut(objDef.priority) { mutableListOf() }.add(lexObj)
            }
        }
        
        return result
    }
    
    private fun buildAllObjectives(
        softObjectives: Map<Int, NormalizedPriorityObjective>,
        explicitObjectives: Map<Int, List<LexObjective>>,
    ): List<PriorityLevel> {
        
        val allPriorities = (softObjectives.keys + explicitObjectives.keys).distinct().sortedDescending()
        
        return allPriorities.map { priority ->
            val objectives = mutableListOf<LexObjective>()
            
            softObjectives[priority]?.let { soft ->
                objectives.add(LexObjective(
                    name = "soft_penalty_p$priority",
                    variable = soft.objectiveVar,
                    direction = ObjectiveDirection.MINIMIZE,
                    priority = priority,
                    isSoftPenalty = true,
                ))
            }
            
            explicitObjectives[priority]?.let { explicit ->
                objectives.addAll(explicit)
            }
            
            PriorityLevel(priority, objectives)
        }
    }
    
    private fun solveFeasibility(
        onSolutionCallback: ((ConstraintSolverScope, Map<String, Expr>) -> Unit)?,
    ): SolveResult {
        val solver = createSolver()
        
        val status = solver.solve(model)
        
        return if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            scope.setSolver(solver)
            onSolutionCallback?.invoke(scope, vars)
            
            SolveResult(
                status = SolveStatus.from(status),
                objectiveValue = null,
                variables = vars,
                scope = scope,
                objectiveValues = emptyMap(),
            )
        } else {
            SolveResult(
                status = SolveStatus.from(status),
                objectiveValue = null,
                variables = vars,
                scope = scope,
                message = "No feasible solution found",
            )
        }
    }
    
    private fun solveLexicographic(
        priorityLevels: List<PriorityLevel>,
        onSolutionCallback: ((ConstraintSolverScope, Map<String, Expr>) -> Unit)?,
    ): SolveResult {
        
        var lastSolver: CpSolver? = null
        var lastStatus: CpSolverStatus = CpSolverStatus.UNKNOWN
        
        for ((levelIndex, level) in priorityLevels.withIndex()) {
            
            if (level.objectives.isEmpty()) continue
            
            val (objective, direction) = combineObjectivesAtLevel(level)
            
            when (direction) {
                ObjectiveDirection.MAXIMIZE -> model.maximize(objective)
                ObjectiveDirection.MINIMIZE -> model.minimize(objective)
            }
            
            val solver = createSolver()
            
            lastStatus = if (onProgressCallback != null) {
                solver.solve(model, ProgressCallback(onProgressCallback))
            } else {
                solver.solve(model)
            }
            lastSolver = solver
            
            if (lastStatus != CpSolverStatus.OPTIMAL && lastStatus != CpSolverStatus.FEASIBLE) {
                return SolveResult(
                    status = SolveStatus.from(lastStatus),
                    objectiveValue = null,
                    variables = vars,
                    scope = scope,
                    message = "Failed at priority level ${level.priority}",
                )
            }
            
            val optimalValue = solver.objectiveValue().toLong()
            for (obj in level.objectives) {
                objectiveResults[obj.name] = solver.value(obj.variable)
            }
            
            if (levelIndex < priorityLevels.size - 1) {
                when (direction) {
                    ObjectiveDirection.MAXIMIZE -> {
                        model.addGreaterOrEqual(objective, optimalValue)
                    }
                    ObjectiveDirection.MINIMIZE -> {
                        model.addLessOrEqual(objective, optimalValue)
                    }
                }
            }
        }
        
        if (lastSolver != null && 
            (lastStatus == CpSolverStatus.OPTIMAL || lastStatus == CpSolverStatus.FEASIBLE)) {
            
            scope.setSolver(lastSolver)
            onSolutionCallback?.invoke(scope, vars)
            
            return SolveResult(
                status = SolveStatus.from(lastStatus),
                objectiveValue = lastSolver.objectiveValue(),
                variables = vars,
                scope = scope,
                objectiveValues = objectiveResults.toMap(),
            )
        }
        
        return SolveResult(
            status = SolveStatus.UNKNOWN,
            objectiveValue = null,
            variables = vars,
            scope = scope,
            message = "Unexpected solver state",
        )
    }
    
    private fun combineObjectivesAtLevel(level: PriorityLevel): Pair<LinearExpr, ObjectiveDirection> {
        val objectives = level.objectives
        
        if (objectives.size == 1) {
            return LinearExpr.affine(objectives[0].variable, 1, 0) to objectives[0].direction
        }
        
        val hasMinimize = objectives.any { it.direction == ObjectiveDirection.MINIMIZE }
        val overallDirection = if (hasMinimize) ObjectiveDirection.MINIMIZE else ObjectiveDirection.MAXIMIZE
        
        val builder = LinearExpr.newBuilder()
        for (obj in objectives) {
            val sign = if (obj.direction == overallDirection) 1L else -1L
            builder.addTerm(obj.variable, sign)
        }
        
        return builder.build() to overallDirection
    }
    
    private fun createSolver(): CpSolver {
        val solver = CpSolver()
        solver.parameters.maxTimeInSeconds = timeLimitSeconds.toDouble()
        solver.parameters.numSearchWorkers = numSearchWorkers
        solver.parameters.logSearchProgress = logSearchProgress
        return solver
    }
    
    /**
     * Callback for reporting solve progress.
     */
    private class ProgressCallback(
        private val callback: (SolveProgress) -> Unit,
    ) : CpSolverSolutionCallback() {
        
        private var solutionCount = 0
        
        override fun onSolutionCallback() {
            solutionCount++
            callback(SolveProgress(
                objectiveValue = objectiveValue(),
                bestBound = bestObjectiveBound(),
                elapsedSeconds = wallTime(),
                solutionCount = solutionCount,
            ))
        }
    }
}

/**
 * Represents an objective at a priority level.
 */
data class LexObjective(
    val name: String,
    val variable: IntVar,
    val direction: ObjectiveDirection,
    val priority: Int,
    val isSoftPenalty: Boolean,
)

/**
 * A priority level containing one or more objectives.
 */
data class PriorityLevel(
    val priority: Int,
    val objectives: List<LexObjective>,
)

