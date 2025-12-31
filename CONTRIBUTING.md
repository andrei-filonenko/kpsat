# Contributing to kpsat

Thank you for your interest in contributing to kpsat! This document provides guidelines and instructions for contributing.

## Development Setup

### Prerequisites

- **JDK 21** or later
- **Gradle** (wrapper included, no separate installation needed)

### Building

```bash
cd kotlin/
./gradlew build
```

### Creating the Fat JAR

For notebook usage or standalone deployment:

```bash
cd kotlin/
./gradlew :solver:shadowJar
```

The JAR will be at `kotlin/solver/build/libs/solver-all-0.1.0-SNAPSHOT.jar`.

## Running Tests

The project has three test source sets:

```bash
# Unit tests (fast, no OR-Tools required for most)
./gradlew test

# Integration tests (require OR-Tools native library)
./gradlew testIntegration

# Slow tests (full model replications, not run by default)
./gradlew slowTest
```

Run all tests:

```bash
./gradlew test testIntegration
```

## Code Style

### General Guidelines

- Follow standard [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful names for variables, functions, and classes
- Write KDoc comments for public APIs
- Keep functions focused and small

### Context Receivers

This project uses Kotlin context receivers for the DSL. When adding new DSL methods:

```kotlin
// IDE/compile-time usage with context receivers
fun hard(name: String, constraint: context(ConstraintScope) (Map<String, Expr>) -> Expr)

// Jupyter notebook usage with explicit parameter
fun hardOf(name: String, constraint: (ConstraintScope, Map<String, Expr>) -> Expr)
```

Always provide both variants for new constraint/variable definition methods.

### Expression DSL

When extending the expression DSL:

1. Add the AST node to `ExprNode` sealed class
2. Add operator/function in `Expr` class or as extension
3. Add evaluation logic in `DirectEvaluator`
4. Add compilation logic in `ExprCompiler`
5. Write tests using `DirectEvaluator` (no solver needed)

## Pull Request Process

1. **Fork** the repository and create a feature branch
2. **Write tests** for new functionality
3. **Ensure all tests pass**: `./gradlew test testIntegration`
4. **Update documentation** if adding new features
5. **Submit a PR** with a clear description of changes

### PR Guidelines

- Keep PRs focused on a single change
- Reference any related issues
- Add tests for bug fixes and new features
- Update the README if adding user-facing features

## Project Structure

```
kotlin/
├── dsl/                    # Expression DSL (solver-agnostic)
│   └── src/main/kotlin/    # Expr, ExprNode, aggregations, DirectEvaluator
├── solver/                 # CP-SAT integration
│   └── src/main/kotlin/    # ConstraintSolverBuilder, ExprCompiler, etc.
└── build.gradle.kts        # Root build configuration

examples/
└── cp-sat-dsl-example.ipynb  # Interactive notebook examples

docs/                       # Generated API documentation
```

## Questions?

Open an issue for questions or discussion about potential changes before starting work on large features.



