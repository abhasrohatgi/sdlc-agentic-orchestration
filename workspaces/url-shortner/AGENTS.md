# Project Behavioral Protocol & Guidelines (AGENTS.md)

> **Project**: url shortner
> **Tech Stack**: JAVA
> **Category**: CUSTOM

---

## 1. Mandatory Plan-First & Human Approval Protocol
- **Plan-First Requirement**: Any modification request, feature addition, or refactoring MUST first be documented in `.ai-plan/plan.md`.
- **Human Approval Checkpoint**: The plan in `.ai-plan/plan.md` MUST be presented to and explicitly approved by the human operator BEFORE code implementation begins.
- **Code Changes Post-Approval Only**: Code edits are strictly prohibited until the plan is approved by the human operator.

---

## 2. Core Engineering Directives
- **Never Guess Schemas or API Signatures**: Inspect authoritative source files and schemas before writing code.
- **Inspect Stack Traces Before Diagnosing**: Base failure diagnoses strictly on full, un-truncated error logs.
- **No Superficial Symptom Patching**: Fix underlying contract violations instead of swallowing exceptions or deleting tests.
- **Empirical Verification Required**: Run build/test commands (`mvn clean test`, `pytest`, `npm test`) and verify 100% clean success.

---

## 3. Quality Gates
1. `GATE_REQ_STABILIZED`: Requirements normalized and scenario auto-detected.
2. `GATE_ARCH_APPROVED`: Blueprint generated in `.ai-plan/plan.md`.
3. `GATE_HUMAN_APPROVED`: Explicit human operator approval recorded.
4. `GATE_TESTS_PASSED`: 100% build and unit test pass rate.
