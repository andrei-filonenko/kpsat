package io.github.andreifilonenko.kpsat.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr
import io.github.andreifilonenko.kpsat.dsl.ConstraintScope
import io.github.andreifilonenko.kpsat.dsl.Expr

/**
 * Normalizes soft constraint penalties to ensure fair comparison within the same priority level.
 * 
 * Following OptaPlanner's approach:
 * 1. Group constraints by priority
 * 2. Estimate bounds for each constraint
 * 3. Normalize each penalty to range [0, SCALE]
 * 4. Apply weight after normalization
 * 5. Sum normalized weighted penalties per priority
 * 
 * This ensures that a constraint with natural range [0, 1000] doesn't dominate
 * one with range [0, 10] just because of the raw values.
 */
class PenaltyNormalizer(
    private val model: CpModel,
    private val scope: ConstraintSolverScope,
    private val scale: Long = CompiledSoftConstraint.NORMALIZATION_SCALE,
) {
    
    /**
     * Process soft constraint definitions and create normalized penalty groups.
     * 
     * @param vars The variable map
     * @param softConstraintDefs The soft constraint definitions from the builder
     * @return Map from priority to the normalized objective variable for that priority
     */
    fun processConstraints(
        vars: Map<String, Expr>,
        softConstraintDefs: List<SoftConstraintDef>,
    ): Map<Int, NormalizedPriorityObjective> {
        
        if (softConstraintDefs.isEmpty()) {
            return emptyMap()
        }
        
        val byPriority = softConstraintDefs.groupBy { it.priority }
        
        return byPriority.mapValues { (priority, defs) ->
            processGroup(priority, defs, vars)
        }
    }
    
    private fun processGroup(
        priority: Int,
        defs: List<SoftConstraintDef>,
        vars: Map<String, Expr>,
    ): NormalizedPriorityObjective {
        
        val compiledConstraints = mutableListOf<CompiledSoftConstraint>()
        
        for (def in defs) {
            with(scope) {
                val penaltyExpr = def.penalty(scope, vars)
                
                val compiled = scope.compiler.compile(penaltyExpr)
                val penaltyVar = when (compiled) {
                    is CompiledExpr.Variable -> compiled.intVar
                    is CompiledExpr.Constant -> model.newConstant(compiled.value)
                }
                
                val bounds = BoundsEstimator.estimate(
                    penaltyVar = penaltyVar,
                    explicitMin = def.minBound,
                    explicitMax = def.maxBound,
                )
                
                compiledConstraints.add(CompiledSoftConstraint(
                    name = def.name,
                    penaltyVar = penaltyVar,
                    weight = def.weight,
                    priority = priority,
                    minBound = bounds.minBound,
                    maxBound = bounds.maxBound,
                ))
            }
        }
        
        val group = SoftConstraintGroup(priority, compiledConstraints)
        val objectiveVar = group.createGroupObjective(model)
        
        return NormalizedPriorityObjective(
            priority = priority,
            objectiveVar = objectiveVar,
            constraints = compiledConstraints,
            direction = ObjectiveDirection.MINIMIZE, 
        )
    }
    
    /**
     * Create a combined objective from multiple priority levels.
     * Uses big-M scaling to ensure strict priority ordering in single-objective mode.
     * 
     * This is an alternative to lexicographic solving - it combines all priorities
     * into a single weighted objective where higher priorities have exponentially larger weights.
     */
    fun createCombinedObjective(
        priorityObjectives: Map<Int, NormalizedPriorityObjective>,
    ): IntVar? {
        if (priorityObjectives.isEmpty()) {
            return null
        }
        
        val sorted = priorityObjectives.entries.sortedByDescending { it.key }
        
        if (sorted.size == 1) {
            return sorted[0].value.objectiveVar
        }
        
        val priorityWeights = mutableMapOf<Int, Long>()
        var currentWeight = 1L
        
        for (i in sorted.indices.reversed()) {
            val priority = sorted[i].key
            val obj = sorted[i].value
            
            priorityWeights[priority] = currentWeight
            
            val maxPenalty = obj.objectiveVar.domain.max()
            currentWeight *= (maxPenalty + 1)
        }
        
        val builder = LinearExpr.newBuilder()
        var maxSum = 0L
        
        for ((priority, obj) in priorityObjectives) {
            val weight = priorityWeights[priority] ?: 1L
            builder.addTerm(obj.objectiveVar, weight)
            maxSum += weight * obj.objectiveVar.domain.max()
        }
        
        val combinedVar = model.newIntVar(0, maxSum, "combined_penalty")
        model.addEquality(combinedVar, builder)
        
        return combinedVar
    }
}

/**
 * Normalized objective for a single priority level.
 */
data class NormalizedPriorityObjective(
    val priority: Int,
    val objectiveVar: IntVar,
    val constraints: List<CompiledSoftConstraint>,
    val direction: ObjectiveDirection,
) {
    /**
     * Get the optimal value after solving.
     */
    fun getOptimalValue(solver: com.google.ortools.sat.CpSolver): Long {
        return solver.value(objectiveVar)
    }
}

/**
 * Report of normalization statistics for debugging.
 */
data class NormalizationReport(
    val constraintName: String,
    val priority: Int,
    val weight: Int,
    val estimatedMin: Long,
    val estimatedMax: Long,
    val boundsStrategy: BoundsEstimationStrategy,
    val confidence: Double,
) {
    override fun toString(): String {
        return "[$priority] $constraintName: weight=$weight, bounds=[$estimatedMin, $estimatedMax], " +
               "strategy=$boundsStrategy, confidence=${String.format("%.0f", confidence * 100)}%"
    }
}

/**
 * Generates a report of all soft constraints and their normalization parameters.
 */
fun generateNormalizationReport(
    constraints: List<CompiledSoftConstraint>,
): List<NormalizationReport> {
    return constraints.map { c ->
        NormalizationReport(
            constraintName = c.name,
            priority = c.priority,
            weight = c.weight,
            estimatedMin = c.minBound,
            estimatedMax = c.maxBound,
            boundsStrategy = BoundsEstimationStrategy.HYBRID, 
            confidence = 1.0,
        )
    }
}


