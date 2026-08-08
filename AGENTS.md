# AgenticSDLC - Agent-Driven Development Guidelines & Behavioral Protocol (AGENTS.md)

This document specifies the core operational directives, coding guidelines, diagnostic reasoning protocols, and quality standards for AI agents (including Antigravity, pair-programming assistants, subagents, and automated software agents) operating within any repository building **AgenticSDLC**.

---

## 1. Mandatory Compliance with `.ai-plan/` & Plan-First Human Approval Protocol

- **Plan-First Requirement**: ANY modification request, feature addition, refactoring, or structural change MUST FIRST be updated and reflected in the `.ai-plan/` directory (specifically `.ai-plan/sdlc_orchestrator_plan.md` or active plan file).
- **Human Approval Checkpoint**: The updated plan in `.ai-plan/` MUST be presented to and explicitly approved by the human operator BEFORE any code modification occurs.
- **Code Changes Post-Approval Only**: Code edits and file mutations are STRICTLY PROHIBITED until the plan changes are reflected in `.ai-plan/` AND explicit human approval is granted.
- **Strict Adherence**: Never make architectural, structural, algorithmic, or code logic changes that contradict or precede active plans in `.ai-plan/`.

---

## 2. Core Engineering Directives for AI Coding Agents

### Directive 1: Never Guess Code Logic, Schemas, or File Paths
- **ALWAYS** inspect authoritative source files, schemas, and directory structures before writing or refactoring code.
- Never infer symbol definitions, method signatures, variable names, or data structures from partial line views.

### Directive 2: Inspect Logs & Stack Traces Before Diagnosing Errors
- **NEVER** form a diagnostic hypothesis for a build failure, runtime exception, or test breakage without reading the full, un-truncated error log and stack trace.
- Base diagnoses strictly on empirical log evidence.

### Directive 3: No Superficial Symptom Patching
- **NEVER** resolve errors by masking symptoms, swallowing exceptions, returning dummy fallbacks, commenting out broken assertions, or deleting failing unit tests.
- Identify and fix the true underlying contract violation.

### Directive 4: Empirical Runtime Verification Required
- **NEVER** declare a task completed, a bug fixed, or a feature implemented until you have gathered concrete, empirical runtime verification demonstrating clean success.
- Editing a file does NOT equal completing a task. Build and test execution commands (`mvn clean test`, `npm test`, `pytest`, etc.) MUST pass cleanly.

### Directive 5: Preserve Existing API Contracts & Scope Control Flow
- Maintain backward compatibility of API signatures unless explicit breaking changes are requested.
- Scope conditional branches and loop mutations strictly to prevent unintended side effects across execution paths.

---

## 3. Agentic Workflow & Quality Gate Protocol

AI agents executing multi-step development tasks must enforce strict execution state transitions and entry/exit quality gates:

0. **Plan Specification & Human Approval Gate**: Every modification request or feature task MUST be documented in `.ai-plan/` and explicitly approved by the human operator BEFORE code generation or editing begins.
1. **Requirement Stabilization Gate**: Zero unresolved ambiguities; problem statement and acceptance criteria established.
2. **Architectural Approval Gate**: API contracts, data flow, and component interactions defined and validated.
3. **Human Governance Checkpoint**: Pause execution and request explicit human review before performing high-impact, breaking, or destructive operations.
4. **Security & Compliance Gate**: Zero hardcoded secrets, path traversal vulnerabilities, or SAST violations.
5. **Empirical Build & Test Gate**: 100% pass rate on real runtime compilation and test suite execution.

---

## 4. Self-Healing & Diagnostic Reasoning Protocol

When build compilation, static analysis, or test execution fails:
1. **Extract Empirical Evidence**: Retrieve full stdout, stderr, and compiler stack traces.
2. **Diagnostic Reasoning**: Trace exact line numbers, missing symbols, or contract assertion mismatches.
3. **Formulate Remediation Directive**: Formulate a targeted fix directive specifying exact code modifications.
4. **Bounded Retries**: Re-synthesize code and re-validate up to a defined maximum retry limit.
5. **Safe Stop & Rollback**: If retries are exhausted or critical security flaws persist, revert workspace mutations (`git reset --hard`) and log audit telemetry.

---

## 5. Audit Lineage & Observability Standards

- Maintain complete persistence of decision lineage across execution stages (requirements, architecture, code diffs, audit reports).
- Export structured execution telemetry including:
  - `sessionId` & timestamp
  - Decision lineage & audit trail
  - Reliability metrics: **Execution Latency**, **MTTR**, **Success Rate**, **Retry Count**, **Rollback Count**.
