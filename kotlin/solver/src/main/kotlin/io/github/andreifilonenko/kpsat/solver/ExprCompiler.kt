@file:Suppress("TooManyFunctions", "LongMethod", "CyclomaticComplexMethod")

package io.github.andreifilonenko.kpsat.solver

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.IntVar
import com.google.ortools.sat.LinearExpr
import com.google.ortools.sat.Literal
import io.github.andreifilonenko.kpsat.dsl.ExprNode
import io.github.andreifilonenko.kpsat.dsl.Expr

/**
 * Result of compiling an expression.
 * Can be either an IntVar (for decision variables and complex expressions)
 * or a constant Long value.
 */
sealed interface CompiledExpr {
    data class Variable(val intVar: IntVar) : CompiledExpr
    data class Constant(val value: Long) : CompiledExpr
    
    fun toLinearExpr(): LinearExpr = when (this) {
        is Variable -> LinearExpr.affine(intVar, 1, 0)
        is Constant -> LinearExpr.constant(value)
    }
    
    fun toLong(): Long = when (this) {
        is Constant -> value
        is Variable -> throw IllegalStateException("Cannot get constant value from variable")
    }
    
    fun isConstant(): Boolean = this is Constant
    
    companion object {
        fun of(value: Long) = Constant(value)
        fun of(intVar: IntVar) = Variable(intVar)
    }
}

/**
 * Compiles DSL expressions (ExprNode) to CP-SAT model constructs.
 */
class ExprCompiler(
    private val model: CpModel,
    private val variables: Map<Int, IntVar>,
) {
    
    private var auxVarCounter = 0
    private val cache = mutableMapOf<ExprNode, CompiledExpr>()
    
    /**
     * Compile an expression to a CompiledExpr.
     */
    fun compile(expr: Expr): CompiledExpr = compile(expr.node)
    
    /**
     * Compile an ExprNode to a CompiledExpr.
     */
    fun compile(node: ExprNode): CompiledExpr = cache.getOrPut(node) {
        compileUncached(node)
    }
    
    /**
     * Compile an expression to a LinearExpr (for objective functions).
     */
    fun compileToLinearExpr(expr: Expr): LinearExpr = compile(expr).toLinearExpr()
    
    /**
     * Compile an expression and ensure it's an IntVar (creating aux var if needed).
     */
    fun compileToIntVar(expr: Expr): IntVar = when (val compiled = compile(expr)) {
        is CompiledExpr.Variable -> compiled.intVar
        is CompiledExpr.Constant -> model.newConstant(compiled.value)
    }
    
    @Suppress("LargeClass")
    private fun compileUncached(node: ExprNode): CompiledExpr = when (node) {
        is ExprNode.Const -> CompiledExpr.Constant(node.value)
        is ExprNode.ConstDouble -> CompiledExpr.Constant(node.value.toLong())
        is ExprNode.Var -> CompiledExpr.Variable(
            variables[node.id] ?: throw IllegalStateException("Variable ${node.id} not found")
        )
        is ExprNode.ArrayLiteral -> throw UnsupportedOperationException("ArrayLiteral must be accessed via At")
        is ExprNode.ArrayLiteralDouble -> throw UnsupportedOperationException("ArrayLiteralDouble must be accessed via At")
        is ExprNode.ArrayOf -> throw UnsupportedOperationException("ArrayOf must be accessed via At")
        is ExprNode.Array2D -> throw UnsupportedOperationException("Array2D must be accessed via At2D")
        
        is ExprNode.Sum -> compileSum(node.operands)
        is ExprNode.Sub -> compileSub(node.left, node.right)
        is ExprNode.Prod -> compileProd(node.operands)
        is ExprNode.Div -> compileDiv(node.left, node.right)
        is ExprNode.Mod -> compileMod(node.left, node.right)
        is ExprNode.Neg -> compileNeg(node.operand)
        
        is ExprNode.Eq -> compileEq(node.left, node.right)
        is ExprNode.Neq -> compileNeq(node.left, node.right)
        is ExprNode.Lt -> compileLt(node.left, node.right)
        is ExprNode.Leq -> compileLeq(node.left, node.right)
        is ExprNode.Gt -> compileGt(node.left, node.right)
        is ExprNode.Geq -> compileGeq(node.left, node.right)
        
        is ExprNode.And -> compileAnd(node.operands)
        is ExprNode.Or -> compileOr(node.operands)
        is ExprNode.Not -> compileNot(node.operand)
        is ExprNode.Xor -> throw UnsupportedOperationException("XOR not fully supported")
        
        is ExprNode.If -> compileIf(node)
        
        is ExprNode.At -> compileAt(node)
        is ExprNode.At2D -> compileAt2D(node)
        is ExprNode.Count -> throw UnsupportedOperationException("Count not supported at compile time")
        is ExprNode.Contains -> throw UnsupportedOperationException("Contains not supported at compile time")
        is ExprNode.Find -> throw UnsupportedOperationException("Find not supported at compile time")
        is ExprNode.IndexOf -> throw UnsupportedOperationException("IndexOf not supported at compile time")
        is ExprNode.CountTrue -> compileCountTrue(node.conditions)
        is ExprNode.IndicatorSum -> compileIndicatorSum(node.terms)
        
        is ExprNode.Intersection -> throw UnsupportedOperationException("Set operations not supported")
        is ExprNode.Union -> throw UnsupportedOperationException("Set operations not supported")
        is ExprNode.Sort -> throw UnsupportedOperationException("Sort not supported at compile time")
        
        is ExprNode.Min -> compileMin(node.operands)
        is ExprNode.Max -> compileMax(node.operands)
        
        is ExprNode.SumOver -> throw UnsupportedOperationException("SumOver should be expanded before compilation")
        is ExprNode.ProdOver -> throw UnsupportedOperationException("ProdOver should be expanded before compilation")
        is ExprNode.MinOver -> throw UnsupportedOperationException("MinOver should be expanded before compilation")
        is ExprNode.MaxOver -> throw UnsupportedOperationException("MaxOver should be expanded before compilation")
        is ExprNode.AndOver -> throw UnsupportedOperationException("AndOver should be expanded before compilation")
        is ExprNode.OrOver -> throw UnsupportedOperationException("OrOver should be expanded before compilation")
        
        is ExprNode.Abs -> compileAbs(node.operand)
        is ExprNode.Sqrt -> throw UnsupportedOperationException("Sqrt not supported in CP-SAT")
        is ExprNode.Pow -> throw UnsupportedOperationException("Pow not supported in CP-SAT (use linearization)")
        is ExprNode.Exp -> throw UnsupportedOperationException("Exp not supported in CP-SAT")
        is ExprNode.Log -> throw UnsupportedOperationException("Log not supported in CP-SAT")
        is ExprNode.Ceil -> throw UnsupportedOperationException("Ceil not supported in CP-SAT")
        is ExprNode.Floor -> throw UnsupportedOperationException("Floor not supported in CP-SAT")
        is ExprNode.Round -> throw UnsupportedOperationException("Round not supported in CP-SAT")
        
        is ExprNode.InDomain -> compileInDomain(node)
    }
    
    private fun compileSum(operands: List<ExprNode>): CompiledExpr {
        val compiled = operands.map { compile(it) }
        
        // Check if all are constants
        if (compiled.all { it.isConstant() }) {
            return CompiledExpr.Constant(compiled.sumOf { it.toLong() })
        }
        
        // Build linear expression
        val builder = LinearExpr.newBuilder()
        for (c in compiled) {
            when (c) {
                is CompiledExpr.Constant -> builder.add(c.value)
                is CompiledExpr.Variable -> builder.add(c.intVar)
            }
        }
        
        // Create auxiliary variable for the sum
        val domain = estimateSumDomain(compiled)
        val auxVar = newAuxVar("sum", domain.first, domain.second)
        model.addEquality(auxVar, builder.build())
        
        return CompiledExpr.Variable(auxVar)
    }
    
    private fun compileSub(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(l.toLong() - r.toLong())
        }
        
        val builder = LinearExpr.newBuilder()
        when (l) {
            is CompiledExpr.Constant -> builder.add(l.value)
            is CompiledExpr.Variable -> builder.add(l.intVar)
        }
        when (r) {
            is CompiledExpr.Constant -> builder.add(-r.value)
            is CompiledExpr.Variable -> builder.addTerm(r.intVar, -1)
        }
        
        val domain = estimateSubDomain(l, r)
        val auxVar = newAuxVar("sub", domain.first, domain.second)
        model.addEquality(auxVar, builder.build())
        
        return CompiledExpr.Variable(auxVar)
    }
    
    private fun compileProd(operands: List<ExprNode>): CompiledExpr {
        if (operands.isEmpty()) return CompiledExpr.Constant(1L)
        if (operands.size == 1) return compile(operands[0])
        
        val compiled = operands.map { compile(it) }
        
        if (compiled.all { it.isConstant() }) {
            return CompiledExpr.Constant(compiled.fold(1L) { acc, c -> acc * c.toLong() })
        }
        
        val vars = compiled.filterIsInstance<CompiledExpr.Variable>()
        val consts = compiled.filterIsInstance<CompiledExpr.Constant>()
        
        if (vars.size == 1 && consts.isNotEmpty()) {
            val coef = consts.fold(1L) { acc, c -> acc * c.value }
            val builder = LinearExpr.newBuilder()
            builder.addTerm(vars[0].intVar, coef)
            
            val domain = estimateProdDomain(listOf(vars[0]), coef)
            val auxVar = newAuxVar("prod", domain.first, domain.second)
            model.addEquality(auxVar, builder.build())
            
            return CompiledExpr.Variable(auxVar)
        }
        
        var result: IntVar = when (val first = compiled[0]) {
            is CompiledExpr.Variable -> first.intVar
            is CompiledExpr.Constant -> model.newConstant(first.value)
        }
        
        for (i in 1 until compiled.size) {
            val next = when (val c = compiled[i]) {
                is CompiledExpr.Variable -> c.intVar
                is CompiledExpr.Constant -> model.newConstant(c.value)
            }
            
            val domain = estimateMultDomain(result, next)
            val auxVar = newAuxVar("mult", domain.first, domain.second)
            model.addMultiplicationEquality(auxVar, result, next)
            result = auxVar
        }
        
        return CompiledExpr.Variable(result)
    }
    
    private fun compileDiv(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(l.toLong() / r.toLong())
        }
        
        val lVar = when (l) {
            is CompiledExpr.Variable -> l.intVar
            is CompiledExpr.Constant -> model.newConstant(l.value)
        }
        val rVar = when (r) {
            is CompiledExpr.Variable -> r.intVar
            is CompiledExpr.Constant -> model.newConstant(r.value)
        }
        
        val domain = estimateDivDomain(lVar, rVar)
        val auxVar = newAuxVar("div", domain.first, domain.second)
        model.addDivisionEquality(auxVar, lVar, rVar)
        
        return CompiledExpr.Variable(auxVar)
    }
    
    private fun compileMod(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(l.toLong() % r.toLong())
        }
        
        val lVar = when (l) {
            is CompiledExpr.Variable -> l.intVar
            is CompiledExpr.Constant -> model.newConstant(l.value)
        }
        val rVar = when (r) {
            is CompiledExpr.Variable -> r.intVar
            is CompiledExpr.Constant -> model.newConstant(r.value)
        }
        
        val domain = estimateModDomain(rVar)
        val auxVar = newAuxVar("mod", domain.first, domain.second)
        model.addModuloEquality(auxVar, lVar, rVar)
        
        return CompiledExpr.Variable(auxVar)
    }
    
    private fun compileNeg(operand: ExprNode): CompiledExpr {
        val op = compile(operand)
        
        if (op.isConstant()) {
            return CompiledExpr.Constant(-op.toLong())
        }
        
        val opVar = (op as CompiledExpr.Variable).intVar
        val builder = LinearExpr.newBuilder()
        builder.addTerm(opVar, -1)
        
        val auxVar = newAuxVar("neg", -opVar.domain.max(), -opVar.domain.min())
        model.addEquality(auxVar, builder.build())
        
        return CompiledExpr.Variable(auxVar)
    }
    
    // ============ COMPARISON OPERATORS ============
    
    private fun compileEq(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(if (l.toLong() == r.toLong()) 1L else 0L)
        }
        
        val boolVar = model.newBoolVar(nextAuxName("eq"))
        
        val lExpr = l.toLinearExpr()
        val rExpr = r.toLinearExpr()
        
        model.addEquality(lExpr, rExpr).onlyEnforceIf(boolVar)
        model.addDifferent(lExpr, rExpr).onlyEnforceIf(boolVar.not())
        
        return CompiledExpr.Variable(boolVar)
    }
    
    private fun compileNeq(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(if (l.toLong() != r.toLong()) 1L else 0L)
        }
        
        val boolVar = model.newBoolVar(nextAuxName("neq"))
        
        val lExpr = l.toLinearExpr()
        val rExpr = r.toLinearExpr()
        
        model.addDifferent(lExpr, rExpr).onlyEnforceIf(boolVar)
        model.addEquality(lExpr, rExpr).onlyEnforceIf(boolVar.not())
        
        return CompiledExpr.Variable(boolVar)
    }
    
    private fun compileLt(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(if (l.toLong() < r.toLong()) 1L else 0L)
        }
        
        val boolVar = model.newBoolVar(nextAuxName("lt"))
        
        val lExpr = l.toLinearExpr()
        val rExpr = r.toLinearExpr()
        
        model.addLessThan(lExpr, rExpr).onlyEnforceIf(boolVar)
        model.addGreaterOrEqual(lExpr, rExpr).onlyEnforceIf(boolVar.not())
        
        return CompiledExpr.Variable(boolVar)
    }
    
    private fun compileLeq(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(if (l.toLong() <= r.toLong()) 1L else 0L)
        }
        
        val boolVar = model.newBoolVar(nextAuxName("leq"))
        
        val lExpr = l.toLinearExpr()
        val rExpr = r.toLinearExpr()
        
        model.addLessOrEqual(lExpr, rExpr).onlyEnforceIf(boolVar)
        model.addGreaterThan(lExpr, rExpr).onlyEnforceIf(boolVar.not())
        
        return CompiledExpr.Variable(boolVar)
    }
    
    private fun compileGt(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(if (l.toLong() > r.toLong()) 1L else 0L)
        }
        
        val boolVar = model.newBoolVar(nextAuxName("gt"))
        
        val lExpr = l.toLinearExpr()
        val rExpr = r.toLinearExpr()
        
        model.addGreaterThan(lExpr, rExpr).onlyEnforceIf(boolVar)
        model.addLessOrEqual(lExpr, rExpr).onlyEnforceIf(boolVar.not())
        
        return CompiledExpr.Variable(boolVar)
    }
    
    private fun compileGeq(left: ExprNode, right: ExprNode): CompiledExpr {
        val l = compile(left)
        val r = compile(right)
        
        if (l.isConstant() && r.isConstant()) {
            return CompiledExpr.Constant(if (l.toLong() >= r.toLong()) 1L else 0L)
        }
        
        val boolVar = model.newBoolVar(nextAuxName("geq"))
        
        val lExpr = l.toLinearExpr()
        val rExpr = r.toLinearExpr()
        
        model.addGreaterOrEqual(lExpr, rExpr).onlyEnforceIf(boolVar)
        model.addLessThan(lExpr, rExpr).onlyEnforceIf(boolVar.not())
        
        return CompiledExpr.Variable(boolVar)
    }
    
    // ============ LOGICAL OPERATORS ============
    
    private fun compileAnd(operands: List<ExprNode>): CompiledExpr {
        if (operands.isEmpty()) return CompiledExpr.Constant(1L)
        
        val compiled = operands.map { compile(it) }
        
        if (compiled.all { it.isConstant() }) {
            return CompiledExpr.Constant(if (compiled.all { it.toLong() != 0L }) 1L else 0L)
        }
        
        val boolVars = compiled.map { toIntVar(it) }.toTypedArray()
        
        val result = model.newBoolVar(nextAuxName("and"))
        
        for (v in boolVars) {
            model.addLessOrEqual(result, v)
        }
        val sumBuilder = LinearExpr.newBuilder()
        for (v in boolVars) {
            sumBuilder.add(v)
        }
        sumBuilder.add(-(boolVars.size - 1).toLong())
        model.addLessOrEqual(sumBuilder.build(), result)
        
        return CompiledExpr.Variable(result)
    }
    
    private fun compileOr(operands: List<ExprNode>): CompiledExpr {
        if (operands.isEmpty()) return CompiledExpr.Constant(0L)
        
        val compiled = operands.map { compile(it) }
        
        if (compiled.all { it.isConstant() }) {
            return CompiledExpr.Constant(if (compiled.any { it.toLong() != 0L }) 1L else 0L)
        }
        
        val boolVars = compiled.map { toIntVar(it) }.toTypedArray()
        
        val result = model.newBoolVar(nextAuxName("or"))
        
        for (v in boolVars) {
            model.addGreaterOrEqual(result, v)
        }
        model.addLessOrEqual(result, LinearExpr.sum(boolVars))
        
        return CompiledExpr.Variable(result)
    }
    
    private fun compileNot(operand: ExprNode): CompiledExpr {
        val op = compile(operand)
        
        if (op.isConstant()) {
            return CompiledExpr.Constant(if (op.toLong() == 0L) 1L else 0L)
        }
        
        val opVar = (op as CompiledExpr.Variable).intVar
        val result = model.newBoolVar(nextAuxName("not"))
        
        model.addEquality(LinearExpr.sum(arrayOf(result, opVar)), 1)
        
        return CompiledExpr.Variable(result)
    }
    
    private fun toIntVar(compiled: CompiledExpr): IntVar = when (compiled) {
        is CompiledExpr.Variable -> compiled.intVar
        is CompiledExpr.Constant -> model.newConstant(compiled.value)
    }
    
    // ============ CONDITIONAL ============
    
    private fun compileIf(node: ExprNode.If): CompiledExpr {
        val cond = compile(node.condition)
        val ifTrue = compile(node.ifTrue)
        val ifFalse = compile(node.ifFalse)
        
        if (cond.isConstant()) {
            return if (cond.toLong() != 0L) ifTrue else ifFalse
        }
        
        val condVar = (cond as CompiledExpr.Variable).intVar
        
        // If both branches are constants, use element constraint
        if (ifTrue.isConstant() && ifFalse.isConstant()) {
            val trueVal = ifTrue.toLong()
            val falseVal = ifFalse.toLong()
            
            // result = cond ? trueVal : falseVal
            // result = falseVal + cond * (trueVal - falseVal)
            val diff = trueVal - falseVal
            val minVal = minOf(trueVal, falseVal)
            val maxVal = maxOf(trueVal, falseVal)
            
            val result = newAuxVar("if", minVal, maxVal)
            
            val builder = LinearExpr.newBuilder()
            builder.add(falseVal)
            builder.addTerm(condVar, diff)
            model.addEquality(result, builder.build())
            
            return CompiledExpr.Variable(result)
        }
        
        val trueVar = toIntVar(ifTrue)
        val falseVar = toIntVar(ifFalse)
        
        val minVal = minOf(trueVar.domain.min(), falseVar.domain.min())
        val maxVal = maxOf(trueVar.domain.max(), falseVar.domain.max())
        
        val result = newAuxVar("if", minVal, maxVal)
        
        model.addElement(condVar, arrayOf(falseVar, trueVar), result)
        
        return CompiledExpr.Variable(result)
    }
    
    // ============ ARRAY ACCESS ============
    
    private fun compileAt(node: ExprNode.At): CompiledExpr {
        val index = compile(node.index)
        
        return when (val arr = node.array) {
            is ExprNode.ArrayLiteral -> {
                if (index.isConstant()) {
                    CompiledExpr.Constant(arr.values[index.toLong().toInt()])
                } else {
                    val indexVar = (index as CompiledExpr.Variable).intVar
                    val result = newAuxVar("elem", arr.values.min(), arr.values.max())
                    model.addElement(indexVar, arr.values, result)
                    CompiledExpr.Variable(result)
                }
            }
            is ExprNode.ArrayOf -> {
                if (index.isConstant()) {
                    compile(arr.elements[index.toLong().toInt()])
                } else {
                    val indexVar = (index as CompiledExpr.Variable).intVar
                    val compiledElements = arr.elements.map { compile(it) }
                    val elementVars = compiledElements.map { toIntVar(it) }.toTypedArray()
                    
                    val minVal = elementVars.minOf { it.domain.min() }
                    val maxVal = elementVars.maxOf { it.domain.max() }
                    val result = newAuxVar("elem", minVal, maxVal)
                    model.addElement(indexVar, elementVars, result)
                    CompiledExpr.Variable(result)
                }
            }
            else -> throw UnsupportedOperationException("Cannot index into ${arr::class.simpleName}")
        }
    }
    
    private fun compileAt2D(node: ExprNode.At2D): CompiledExpr {
        val i = compile(node.i)
        val j = compile(node.j)
        
        if (!i.isConstant() || !j.isConstant()) {
            throw UnsupportedOperationException("2D array access with variable indices not fully supported")
        }
        
        val iVal = i.toLong().toInt()
        val jVal = j.toLong().toInt()
        
        return when (val arr = node.array) {
            is ExprNode.Array2D -> {
                val row = arr.rows[iVal]
                val atNode = ExprNode.At(row, ExprNode.Const(jVal.toLong()))
                compile(atNode)
            }
            else -> throw UnsupportedOperationException("Cannot 2D index into ${arr::class.simpleName}")
        }
    }
    
    // ============ MIN/MAX ============
    
    private fun compileMin(operands: List<ExprNode>): CompiledExpr {
        if (operands.isEmpty()) throw IllegalArgumentException("Min requires at least one operand")
        if (operands.size == 1) return compile(operands[0])
        
        val compiled = operands.map { compile(it) }
        
        if (compiled.all { it.isConstant() }) {
            return CompiledExpr.Constant(compiled.minOf { it.toLong() })
        }
        
        val vars = compiled.map { toIntVar(it) }.toTypedArray()
        
        val minVal = vars.minOf { it.domain.min() }
        val maxVal = vars.minOf { it.domain.max() }
        val result = newAuxVar("min", minVal, maxVal)
        
        model.addMinEquality(result, vars)
        
        return CompiledExpr.Variable(result)
    }
    
    private fun compileMax(operands: List<ExprNode>): CompiledExpr {
        if (operands.isEmpty()) throw IllegalArgumentException("Max requires at least one operand")
        if (operands.size == 1) return compile(operands[0])
        
        val compiled = operands.map { compile(it) }
        
        if (compiled.all { it.isConstant() }) {
            return CompiledExpr.Constant(compiled.maxOf { it.toLong() })
        }
        
        val vars = compiled.map { toIntVar(it) }.toTypedArray()
        
        val minVal = vars.maxOf { it.domain.min() }
        val maxVal = vars.maxOf { it.domain.max() }
        val result = newAuxVar("max", minVal, maxVal)
        
        model.addMaxEquality(result, vars)
        
        return CompiledExpr.Variable(result)
    }
    
    // ============ ABS ============
    
    private fun compileAbs(operand: ExprNode): CompiledExpr {
        val op = compile(operand)
        
        if (op.isConstant()) {
            return CompiledExpr.Constant(kotlin.math.abs(op.toLong()))
        }
        
        val opVar = (op as CompiledExpr.Variable).intVar
        val maxAbs = maxOf(kotlin.math.abs(opVar.domain.min()), kotlin.math.abs(opVar.domain.max()))
        val result = newAuxVar("abs", 0, maxAbs)
        
        model.addAbsEquality(result, opVar)
        
        return CompiledExpr.Variable(result)
    }
    
    // ============ DOMAIN CONSTRAINT ============
    
    private fun compileInDomain(node: ExprNode.InDomain): CompiledExpr {
        val variable = compile(node.variable)
        
        val varToConstrain = when (variable) {
            is CompiledExpr.Variable -> variable.intVar
            is CompiledExpr.Constant -> {
                // Check if constant is in domain
                return CompiledExpr.Constant(if (variable.value in node.domain.toList()) 1L else 0L)
            }
        }
        
        // Create a reified domain constraint
        // result == 1 iff varToConstrain is in domain
        // We'll use a sum of equality checks
        val result = model.newBoolVar(nextAuxName("inDomain"))
        
        // For each value in domain, create indicator: isValue_i = (var == value_i)
        // Then: result = OR(isValue_i)
        val indicators = node.domain.map { value ->
            val isValue = model.newBoolVar(nextAuxName("isVal"))
            model.addEquality(varToConstrain, value).onlyEnforceIf(isValue)
            model.addDifferent(varToConstrain, value).onlyEnforceIf(isValue.not())
            isValue
        }
        
        // result = 1 iff at least one indicator is 1
        // sum(indicators) >= 1 when result = 1
        // sum(indicators) == 0 when result = 0
        val indicatorSum = LinearExpr.sum(indicators.toTypedArray())
        model.addGreaterOrEqual(indicatorSum, 1).onlyEnforceIf(result)
        model.addEquality(indicatorSum, 0).onlyEnforceIf(result.not())
        
        return CompiledExpr.Variable(result)
    }
    
    // ============ DOMAIN ESTIMATION HELPERS ============
    
    private fun estimateSumDomain(operands: List<CompiledExpr>): Pair<Long, Long> {
        var minSum = 0L
        var maxSum = 0L
        for (op in operands) {
            when (op) {
                is CompiledExpr.Constant -> {
                    minSum += op.value
                    maxSum += op.value
                }
                is CompiledExpr.Variable -> {
                    minSum += op.intVar.domain.min()
                    maxSum += op.intVar.domain.max()
                }
            }
        }
        return minSum to maxSum
    }
    
    private fun estimateSubDomain(left: CompiledExpr, right: CompiledExpr): Pair<Long, Long> {
        val lMin = when (left) {
            is CompiledExpr.Constant -> left.value
            is CompiledExpr.Variable -> left.intVar.domain.min()
        }
        val lMax = when (left) {
            is CompiledExpr.Constant -> left.value
            is CompiledExpr.Variable -> left.intVar.domain.max()
        }
        val rMin = when (right) {
            is CompiledExpr.Constant -> right.value
            is CompiledExpr.Variable -> right.intVar.domain.min()
        }
        val rMax = when (right) {
            is CompiledExpr.Constant -> right.value
            is CompiledExpr.Variable -> right.intVar.domain.max()
        }
        return (lMin - rMax) to (lMax - rMin)
    }
    
    private fun estimateProdDomain(vars: List<CompiledExpr.Variable>, coef: Long): Pair<Long, Long> {
        if (vars.isEmpty()) return coef to coef
        val v = vars[0].intVar
        val products = listOf(
            v.domain.min() * coef,
            v.domain.max() * coef
        )
        return products.min() to products.max()
    }
    
    private fun estimateMultDomain(a: IntVar, b: IntVar): Pair<Long, Long> {
        val products = listOf(
            a.domain.min() * b.domain.min(),
            a.domain.min() * b.domain.max(),
            a.domain.max() * b.domain.min(),
            a.domain.max() * b.domain.max()
        )
        return products.min() to products.max()
    }
    
    private fun estimateDivDomain(num: IntVar, denom: IntVar): Pair<Long, Long> {
        // Conservative estimate
        val maxAbs = maxOf(
            kotlin.math.abs(num.domain.min()),
            kotlin.math.abs(num.domain.max())
        )
        return -maxAbs to maxAbs
    }
    
    private fun estimateModDomain(denom: IntVar): Pair<Long, Long> {
        val maxMod = maxOf(
            kotlin.math.abs(denom.domain.min()),
            kotlin.math.abs(denom.domain.max())
        ) - 1
        return 0L to maxOf(0L, maxMod)
    }
    
    private fun newAuxVar(prefix: String, min: Long, max: Long): IntVar =
        model.newIntVar(min, max, nextAuxName(prefix))
    
    private fun nextAuxName(prefix: String): String = "_${prefix}_${auxVarCounter++}"
    
    private fun compileCountTrue(conditions: List<ExprNode>): CompiledExpr {
        if (conditions.isEmpty()) {
            return CompiledExpr.Constant(0)
        }
        
        val boolVars = conditions.map { cond ->
            when (val compiled = compile(cond)) {
                is CompiledExpr.Constant -> model.newConstant(if (compiled.value != 0L) 1 else 0)
                is CompiledExpr.Variable -> compiled.intVar
            }
        }
        
        val result = newAuxVar("countTrue", 0, conditions.size.toLong())
        model.addEquality(result, LinearExpr.sum(boolVars.toTypedArray()))
        return CompiledExpr.Variable(result)
    }
    
    private fun compileIndicatorSum(terms: List<Pair<ExprNode, ExprNode>>): CompiledExpr {
        if (terms.isEmpty()) {
            return CompiledExpr.Constant(0)
        }
        
        val products = terms.map { (condition, value) ->
            val condCompiled = compile(condition)
            val valCompiled = compile(value)
            
            val condVar = when (condCompiled) {
                is CompiledExpr.Constant -> model.newConstant(if (condCompiled.value != 0L) 1 else 0)
                is CompiledExpr.Variable -> condCompiled.intVar
            }
            
            val valVar = when (valCompiled) {
                is CompiledExpr.Constant -> model.newConstant(valCompiled.value)
                is CompiledExpr.Variable -> valCompiled.intVar
            }
            
            val valMin = valVar.domain.min()
            val valMax = valVar.domain.max()
            val productMin = minOf(0, valMin)
            val productMax = maxOf(0, valMax)
            val product = newAuxVar("indProd", productMin, productMax)
            model.addMultiplicationEquality(product, condVar, valVar)
            product
        }
        
        val totalMin = products.sumOf { it.domain.min() }
        val totalMax = products.sumOf { it.domain.max() }
        val result = newAuxVar("indSum", totalMin, totalMax)
        model.addEquality(result, LinearExpr.sum(products.toTypedArray()))
        return CompiledExpr.Variable(result)
    }
}

/**
 * Extension function to add a hard constraint directly from an Expr.
 */
fun CpModel.addHardConstraint(compiler: ExprCompiler, expr: Expr) {
    val compiled = compiler.compile(expr)
    when (compiled) {
        is CompiledExpr.Variable -> addEquality(compiled.intVar, 1)
        is CompiledExpr.Constant -> {
            if (compiled.value == 0L) {
                throw IllegalStateException("Hard constraint evaluates to false")
            }
        }
    }
}

