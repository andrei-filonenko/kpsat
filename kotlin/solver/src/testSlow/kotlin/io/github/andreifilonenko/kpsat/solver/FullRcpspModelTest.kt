@file:Suppress("LongMethod", "MagicNumber")

package io.github.andreifilonenko.kpsat.solver

import arrow.core.getOrElse
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.andreifilonenko.kpsat.dsl.*
import org.junit.jupiter.api.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/**
 * Full RCPSP model test - replicates the exact model from cp-sat-dsl-example.ipynb.
 * 
 * This test is slow and is not run by default. Run with: ./gradlew :solver:slowTest
 */
class FullRcpspModelTest {

    // Data classes matching the notebook
    data class Department(val id: String = "", val name: String = "")
    
    data class Position(
        val id: String = "",
        val name: String = "",
        val skillLevel: String = "",
        val hourlyRate: Double = 0.0
    )
    
    data class BankHolidaySet(
        val id: String = "",
        val name: String = "",
        val holidays: List<LocalDate> = emptyList()
    )
    
    data class Resource(
        val id: Long = 0,
        val name: String = "",
        val departmentId: String = "",
        val positionId: String = "",
        val skills: List<String> = emptyList(),
        val bankHolidaySetId: String = "",
        val weeklyAvailability: Map<String, Int> = emptyMap()
    )
    
    data class Project(
        val id: Long = 0,
        val name: String = "",
        val projectCode: String = "",
        val departmentId: String = "",
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val priority: Int = 0
    )
    
    data class Activity(
        val id: Long = 0,
        val code: String = "",
        val name: String = "",
        val order: Int = 0,
        val projectId: Long = 0,
        val dependencies: List<Long> = emptyList(),
        val estimatedHours: Int = 0,
        val rate: Double = 0.0,
        val isCommercialRate: Boolean = false,
        val budgetNumbers: Int? = null,
        val requestDepartmentId: String? = null,
        val requestPositionId: String? = null,
        val requestSkillLevel1: String? = null,
        val requestSkillLevel2: String? = null,
        val requestSkillLevel3: String? = null
    )
    
    data class WorkUnit(
        val id: Long,
        val activityId: Long,
        val projectId: Long
    )
    
    data class SchedulingData(
        val departments: List<Department> = emptyList(),
        val positions: List<Position> = emptyList(),
        val bankHolidaySets: List<BankHolidaySet> = emptyList(),
        val resources: List<Resource> = emptyList(),
        val projects: List<Project> = emptyList(),
        val activities: List<Activity> = emptyList()
    )

    @Test
    fun `full RCPSP model from notebook should solve or report meaningful error`() {
        // Load data from YAML - path relative to project root
        val yamlFile = File("../../examples/data.yaml")
        assertTrue(yamlFile.exists(), "YAML data file should exist at ${yamlFile.absolutePath}")
        
        val mapper = ObjectMapper(YAMLFactory()).apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
        }
        
        val data = mapper.readValue(yamlFile, SchedulingData::class.java)
        
        println("Loaded scheduling data:")
        println("  Departments: ${data.departments.size}")
        println("  Positions: ${data.positions.size}")
        println("  Resources: ${data.resources.size}")
        println("  Projects: ${data.projects.size}")
        println("  Activities: ${data.activities.size}")
        
        // Setup problem: generate work units and compute date range
        val minDate = data.projects.mapNotNull { it.startDate }.minOrNull() ?: LocalDate.now()
        val maxDate = data.projects.mapNotNull { it.endDate }.maxOrNull() ?: minDate.plusDays(30)
        
        // Create weekday date list (excluding weekends)
        val allDates = generateSequence(minDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(maxDate) }
            .filter { it.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }
            .toList()
        
        val dateToIndex = allDates.mapIndexed { idx, date -> date to idx.toLong() }.toMap()
        
        // Generate work units (1 hour each)
        var unitId = 1L
        val workUnits = data.activities.flatMap { activity ->
            (1..activity.estimatedHours).map { 
                WorkUnit(unitId++, activity.id, activity.projectId)
            }
        }
        
        // Build lookup maps
        val qualifiedResourcesByActivity = data.activities.associate { activity ->
            val requiredSkills = listOfNotNull(
                activity.requestSkillLevel1,
                activity.requestSkillLevel2,
                activity.requestSkillLevel3
            )
            val qualified = if (requiredSkills.isEmpty()) {
                data.resources.map { it.id }
            } else {
                data.resources.filter { res -> 
                    requiredSkills.any { skill -> skill in res.skills }
                }.map { it.id }
            }
            activity.id to qualified
        }
        
        println("\nProblem setup complete:")
        println("  Date range: $minDate to $maxDate (${allDates.size} working days)")
        println("  Work units: ${workUnits.size} hours to schedule")
        println("  Dependencies: ${data.activities.sumOf { it.dependencies.size }} precedence arcs")
        
        // Constants
        val numResources = data.resources.size
        val numDates = allDates.size
        val maxHoursPerDay = 8L
        
        // Compute project deadline indices
        val projectDeadlineIndex = data.projects.associate { proj ->
            proj.id to (dateToIndex[proj.endDate] ?: (numDates - 1).toLong())
        }
        
        // Build list of dependency pairs for precedence constraints
        val dependencyPairs = data.activities.flatMap { act ->
            act.dependencies.map { predId -> predId to act.id }
        }
        
        // Precompute resource availability per (resource, day of week)
        val resourceDailyCapacity = data.resources.mapIndexed { resIdx, resource ->
            val capacityByDay = DayOfWeek.entries.associate { dow ->
                dow to (resource.weeklyAvailability[dow.name] ?: maxHoursPerDay.toInt()).toLong()
            }
            resIdx to capacityByDay
        }.toMap()
        
        println("\nBuilding RCPSP model...")
        
        // Pre-compute minimum theoretical days per activity (for fragmentation penalty)
        val minDaysPerActivity = data.activities.associate { act ->
            act.id to ((act.estimatedHours + 7) / 8)  // ceiling division
        }
        
        // Pre-compute consecutive unit pairs within each activity (for resource consistency)
        data class UnitPair(val u1: WorkUnit, val u2: WorkUnit)
        val consecutiveUnitPairs = data.activities.flatMap { act ->
            workUnits.filter { it.activityId == act.id }.zipWithNext().map { (u1, u2) -> UnitPair(u1, u2) }
        }
        
        // Build the exact model from the notebook
        val rcpspModel = ConstraintSolverBuilder()
            .timeLimit(120)
            .numWorkers(8)
            .logProgress(true)
            
            // === DECISION VARIABLES ===
            .variables { scope ->
                val vars = mutableMapOf<String, Expr>()
                
                // For each work unit: assign resource and date
                workUnits.forEach { unit ->
                    vars["res_${unit.id}"] = scope.int("res_${unit.id}", 0, (numResources - 1).toLong())
                    vars["date_${unit.id}"] = scope.int("date_${unit.id}", 0, (numDates - 1).toLong())
                }
                
                // For each activity: track min start date and max end date
                data.activities.forEach { act ->
                    vars["act_start_${act.id}"] = scope.int("act_start_${act.id}", 0, (numDates - 1).toLong())
                    vars["act_end_${act.id}"] = scope.int("act_end_${act.id}", 0, (numDates - 1).toLong())
                }
                
                vars
            }
            
            // === HARD CONSTRAINT: Resource daily capacity ===
            .hard("resource_capacity") { _, vars ->
                forAll(0 until numResources) { resIdx ->
                    forAll(0 until numDates) { dateIdx ->
                        val date = allDates[dateIdx]
                        val capacity = resourceDailyCapacity[resIdx]?.get(date.dayOfWeek) ?: maxHoursPerDay
                        
                        // Count work units assigned to this (resource, date)
                        val countExpr = sum(workUnits) { unit ->
                            val isThisRes = vars["res_${unit.id}"]!! eq resIdx.toLong()
                            val isThisDate = vars["date_${unit.id}"]!! eq dateIdx.toLong()
                            iif(isThisRes and isThisDate, 1L, 0L)
                        }
                        
                        countExpr leq capacity
                    }
                }
            }
            
            // === HARD CONSTRAINT: Activity start/end linked to work units ===
            .hard("activity_bounds") { _, vars ->
                forAll(data.activities) { act ->
                    val actUnits = workUnits.filter { it.activityId == act.id }
                    val actStart = vars["act_start_${act.id}"]!!
                    val actEnd = vars["act_end_${act.id}"]!!
                    
                    // Start is min of all unit dates, end is max
                    forAll(actUnits) { unit ->
                        val unitDate = vars["date_${unit.id}"]!!
                        (actStart leq unitDate) and (unitDate leq actEnd)
                    }
                }
            }
            
            // === HARD CONSTRAINT: Precedence ===
            .hard("precedence") { _, vars ->
                forAll(dependencyPairs) { (predId, succId) ->
                    val predEnd = vars["act_end_$predId"]!!
                    val succStart = vars["act_start_$succId"]!!
                    predEnd lt succStart  // Predecessor must end before successor starts
                }
            }
            
            // === HARD CONSTRAINT: Project deadlines ===
            .hard("project_deadline") { _, vars ->
                forAll(data.activities) { act ->
                    val deadline = projectDeadlineIndex[act.projectId] ?: (numDates - 1).toLong()
                    vars["act_end_${act.id}"]!! leq deadline
                }
            }
            
            // === HARD CONSTRAINT: Skill requirements ===
            .hard("skill_requirements") { _, vars ->
                forAll(workUnits) { unit ->
                    val qualified = qualifiedResourcesByActivity[unit.activityId] ?: emptyList()
                    if (qualified.isNotEmpty() && qualified.size < numResources) {
                        val qualifiedIndices = qualified.mapNotNull { resId -> 
                            data.resources.indexOfFirst { it.id == resId }.takeIf { it >= 0 }?.toLong()
                        }.toLongArray()
                        vars["res_${unit.id}"]!! inDomain qualifiedIndices
                    } else {
                        expr(1L) eq 1L  // Always true - no restriction
                    }
                }
            }
            
            // === OBJECTIVE: Maximize contiguous blocks (priority 1) ===
            .maximize("contiguous_blocks", priority = 1) { _, vars ->
                sum(data.activities) { act ->
                    val actUnits = workUnits.filter { it.activityId == act.id }
                    if (actUnits.size <= 1) {
                        expr(0L)
                    } else {
                        sum(0 until actUnits.size - 1) { i ->
                            val u1 = actUnits[i]
                            val u2 = actUnits[i + 1]
                            val res1 = vars["res_${u1.id}"]!!
                            val res2 = vars["res_${u2.id}"]!!
                            val date1 = vars["date_${u1.id}"]!!
                            val date2 = vars["date_${u2.id}"]!!
                            
                            val sameResource = iif(res1 eq res2, 10L, 0L)
                            val dateDiff = iif(date1 leq date2, date2 - date1, date1 - date2)
                            val dateBonus = iif(dateDiff eq 0L, 5L, iif(dateDiff eq 1L, 3L, 0L))
                            
                            sameResource + dateBonus
                        }
                    }
                }
            }
            
            // === OBJECTIVE: Minimize makespan (priority 2 - higher priority) ===
            .minimize("makespan", priority = 2) { _, vars ->
                max(data.activities.map { vars["act_end_${it.id}"]!! })
            }
            
            // === SOFT CONSTRAINT: Penalize activity fragmentation ===
            .soft(
                name = "activity_fragmentation",
                weight = 5,
                priority = 1
            ) { _, vars ->
                sum(data.activities) { act ->
                    val span = vars["act_end_${act.id}"]!! - vars["act_start_${act.id}"]!! + 1L
                    val minDays = minDaysPerActivity[act.id]!!.toLong()
                    // Penalty = days beyond minimum
                    span - minDays
                }
            }
            
            // === SOFT CONSTRAINT: Penalize resource switches within activity ===
            .soft(
                name = "resource_consistency",
                weight = 10,
                priority = 1
            ) { _, vars ->
                // Count pairs where resource differs between consecutive work units
                sum(consecutiveUnitPairs) { pair ->
                    val different = vars["res_${pair.u1.id}"]!! neq vars["res_${pair.u2.id}"]!!
                    different  // 1 if different, 0 if same
                }
            }
            
            .build()
        
        println("RCPSP model built with ${workUnits.size} work units")
        println("  Hard constraints:")
        println("    - Resource capacity: max $maxHoursPerDay hours/day per resource")
        println("    - Precedence: ${dependencyPairs.size} activity dependencies")
        println("    - Skill requirements: activities matched to qualified resources")
        println("  Soft constraints:")
        println("    - Activity fragmentation: penalize span beyond minimum days (weight=5)")
        println("    - Resource consistency: penalize resource switches within activity (weight=10)")
        println("  Objectives:")
        println("    - Priority 2: Minimize makespan")
        println("    - Priority 1: Maximize contiguous blocks")
        
        // Solve
        println("\nSolving RCPSP model...")
        val result = rcpspModel.solve()
        
        println("Solve completed with status: ${result.status}")
        println("Message: ${result.message}")
        
        when (result.status) {
            SolveStatus.OPTIMAL -> {
                println("✓ Optimal solution found!")
                println("  Objective value: ${result.objectiveValue}")
            }
            SolveStatus.FEASIBLE -> {
                println("~ Feasible solution found (may not be optimal)")
                println("  Objective value: ${result.objectiveValue}")
            }
            SolveStatus.INFEASIBLE -> {
                println("✗ No feasible solution - constraints cannot be satisfied")
                // This is valid - model might be over-constrained
            }
            SolveStatus.MODEL_INVALID -> {
                println("✗ Model invalid: ${result.message}")
                throw AssertionError("Model invalid: ${result.message}")
            }
            SolveStatus.UNKNOWN -> {
                println("? Status unknown - likely timeout or solver issue")
                println("  Message: ${result.message}")
                // Check if it's a timeout or something else
            }
        }
        
        // Assert we got some result (not an internal error)
        assertNotEquals(SolveStatus.MODEL_INVALID, result.status, 
            "Model should be valid: ${result.message}")
    }
}


