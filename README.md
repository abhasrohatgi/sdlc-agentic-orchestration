# 🚀 AgenticSDLC — Autonomous Multi-Agent Software Engineering Framework
> **Assignment Submission & Narrative Document**  
> *Interview Assignment: Build an Agentic Software Engineering System — URL Shortener*

---

## 🎯 1. Executive Summary & Narrative

**AgenticSDLC** is an autonomous, multi-agent AI software engineering framework designed to transform high-level natural language software requirements into production-ready, fully verified Java codebases.

Unlike standard code generators that produce disconnected snippets or swallow build errors, **AgenticSDLC** enforces a strict, human-governed engineering protocol:
1. **🛡️ Controlled Autonomy & Governance**: Every feature or project is first architected in `.ai-plan/plan.md` and submitted for explicit human approval before any code mutation occurs.
2. **🧩 2-Phase Decoupled Manifest & Markdown Code Synthesis**: Eliminates LLM JSON syntax escaping failures by separating file manifest mapping (Phase 1) from raw Markdown code synthesis (Phase 2).
3. **🏗️ Topological Contract-First Synthesis**: Code is synthesized topologically in 3 ordered waves (Contracts/Schemas → Service Implementations → Integration Tests) to eliminate missing symbol errors.
4. **⚡ Empirical Verification & Anti-Looping Self-Healing**: Code is verified against real local builds (`mvn test`). When compilation or test failures occur, an autonomous self-healing loop diagnoses root causes, retains history of past failed hypotheses to prevent repetitive loops, and applies targeted patches.

---

## 📋 2. System Assumptions & Operational Boundaries

To set clear operational scope for evaluation, **AgenticSDLC** operates under the following explicit assumptions:

1. **☕ Target Language**: Supports **Java** exclusively.
2. **📁 Workspace Storage**: All generated and managed projects are isolated under the local **`workspaces/`** directory (`workspaces/<project_id>/`).
3. **🔑 LLM Credentials & Model Configuration**: Assumes **`LLM_API_KEY`** and **`LLM_MODEL`** are exported in system environment variables to configure Google Gemini access.
4. **⚙️ Build Environment Prerequisites**: Assumes Java 21 JDK and Apache Maven (`mvn`) binaries are installed and accessible on the host machine's system `PATH`.
5. **👤 Interactive Operator Governance**: Assumes an interactive CLI terminal session where a human operator reviews generated `.ai-plan/plan.md` architectural plans and enters explicit `Y/n` approval.

---

## 🏗️ 3. Multi-Agent Pipeline & DAG Architecture Diagram

The **AgenticSDLC** orchestration engine operates as a **Directed Acyclic Graph (DAG)** of specialized agents. Execution flows strictly from requirement analysis to human governance, topological wave synthesis, and empirical build validation.

```text
                  ┌────────────────────────────────────────────────────────┐
                  │               User Software Requirement                │
                  └───────────────────────────┬────────────────────────────┘
                                              │
                                              ▼
                  ┌────────────────────────────────────────────────────────┐
                  │               STAGE 1: PlanGeneratorAgent              │
                  │  - Disambiguates & Normalizes Requirements             │
                  │  - Writes .ai-plan/plan.md Architectural Plan          │
                  └───────────────────────────┬────────────────────────────┘
                                              │
                                              ▼
                  ┌────────────────────────────────────────────────────────┐
                  │            STAGE 2: HumanGatekeeperAgent               │
                  │  - Human Governance Gatekeeper Checkpoint              │
                  │  - Requires Explicit Operator Approval                 │
                  └─────────────┬────────────────────────────┬─────────────┘
                                │ Approved                   │ Rejected
                                ▼                            ▼
                  ┌───────────────────────────┐  ┌─────────────────────────┐
                  │ STAGE 3: CodeEngineerAgent│  │    Pipeline Aborted     │
                  └─────────────┬─────────────┘  └─────────────────────────┘
                                │
                                ▼
       ┌─────────────────────────────────────────────────────────────────┐
       │             TOPOLOGICAL 3-WAVE CODE SYNTHESIS (DAG)             │
       ├─────────────────────────────────────────────────────────────────┤
       │ Phase 1: Lightweight JSON Manifest Map Generation               │
       │    │                                                            │
       │    ▼                                                            │
       │ Wave 1: Contracts & Schemas (POM, DTOs, Entities, Exceptions)   │
       │    │                                                            │
       │    ▼                                                            │
       │ Wave 2: Implementations & Controllers (Services, REST APIs)    │
       │    │                                                            │
       │    ▼                                                            │
       │ Wave 3: Integration Tests & Verification Suites                 │
       └────────────────────────────────┬────────────────────────────────┘
                                        │
                                        ▼
                  ┌────────────────────────────────────────────────────────┐◄─────────┐
                  │                STAGE 4: QAValidatorAgent               │          │
                  │  - Runs Real Local Process Execution (mvn test)        │          │
                  └─────────────┬────────────────────────────┬─────────────┘          │
                                │                            │                        │
                  Build Passed  │                            │ Build Failed           │
                                ▼                            ▼                        │
                  ┌───────────────────────────┐  ┌─────────────────────────┐          │
                  │ ExecutionTrajectoryLogger │  │  LlmClientManager Fix   │          │
                  │  - Writes Step-by-Step    │  │  - Injects History &    │          │
                  │    Trajectory JSON        │  │    Top-of-Context POM   │          │
                  └─────────────┬─────────────┘  └───────────┬─────────────┘          │
                                │                            │                        │
                                ▼                            ▼                        │
                  ┌───────────────────────────┐  ┌─────────────────────────┐          │
                  │   Production Workspace    │  │  CodeEngineerAgent      │          │
                  │      Verified & Ready     │  │  - Applies Targeted     │          │
                  └───────────────────────────┘  │    Patch & Retries      │          │
                                                 └───────────┬─────────────┘          │
                                                             │                        │
                                                             └─ Re-run Build & Test ──┘
```

### Key Architectural Characteristics of the DAG:
- **Strict Topological Order**: Wave 2 implementation files (`ServiceImpl.java`) are compiled against the exact symbols generated in Wave 1 contract files (`Service.java`, `CreateRequestDto.java`), guaranteeing zero missing symbol or import errors.
- **Bounded Feedback Cycles**: When an empirical build failure occurs in `QAValidatorAgent`, execution loops backwards strictly to the self-healing diagnostic node. The loop retains state history across attempts to guarantee convergence and prevent infinite looping.

---

## 🌟 4. Demonstrating the Three Required SDLC Scenarios

Per assignment requirements, **AgenticSDLC** handles greenfield, brownfield, and ambiguous software engineering workflows:

### 🟢 Scenario 1: Greenfield (New System from Scratch — URL Shortener)
- **User Requirement**: *"Build a REST URL Shortener with Base62 encoding, custom short aliases, expiration timestamps, and click analytics."*
- **Decomposition**: `PlanGeneratorAgent` decomposes the requirement into 27 discrete files across controller, service, repository, DTO, entity, exception, and test layers.
- **Orchestration**: Executes 3-wave topological synthesis, creating `pom.xml`, `UrlEntity.java`, `Base62Encoder.java`, `UrlServiceImpl.java`, `UrlController.java`, and JUnit 5 test suites.
- **Validation**: `QAValidatorAgent` executes `mvn test`, confirming 100% test pass rate.

### 🟡 Scenario 2: Brownfield (Incremental Evolution & Refactoring)
- **User Requirement**: *"Add Redis caching layer to shorten URL redirect lookup latency in the existing url-shortener service."*
- **Codebase Reasoning**: `ScenarioDetector` identifies an existing project at `workspaces/url-shortener`, switches to brownfield mode, inspects existing `pom.xml` and `UrlServiceImpl.java`, and isolates impacted files.
- **Orchestration**: `CodeEngineerAgent` synthesizes `RedisConfig.java` and patches `UrlServiceImpl.java` to check Redis before querying Spring Data JPA.
- **Validation**: Executes `mvn test` to verify backward compatibility and clean compilation.

### 🔴 Scenario 3: Ambiguous Requirements Disambiguation
- **User Requirement**: *"Make the URL shortener better and faster."*
- **Requirement Normalization**: `ScenarioDetector` flags the prompt as `AMBIGUOUS`. `PlanGeneratorAgent` executes requirement normalization, expanding vague intent into explicit engineering criteria (caching strategy, database indexing, REST response codes, error handling) before writing `.ai-plan/plan.md`.
- **Governance Gate**: Pauses for human operator approval, displaying normalized requirements for sign-off.

---

## ⚡ 5. Core Architectural & Design Decisions

### Decision 1: Plan-First Human Approval Protocol
- **Problem**: Autonomous agents often mutate workspace files unpredictably, making uncoordinated code changes that break existing systems.
- **Our Solution**: Enforced a strict gatekeeper checkpoint (`HumanGatekeeperAgent`). The system generates a detailed architectural plan (`.ai-plan/plan.md`) outlining all 25+ files to be created/modified. Execution pauses until the human operator explicitly approves the plan.

### Decision 2: Decoupled Phase-1 JSON Manifest & Phase-2 Markdown Code Synthesis
- **Problem**: Forcing an LLM to generate 25+ complete Java source files inside a single giant JSON object leads to constant JSON syntax failures (unescaped quotes, newlines in code strings, and JSON token truncation).
- **Our Solution**: Decoupled code generation into two focused phases:
  - **Phase 1 (Lightweight JSON Manifest Map)**: The LLM returns ONLY a lightweight JSON map declaring file paths, file types, and purpose descriptions (zero code). Enforced with `responseMimeType("application/json")`.
  - **Phase 2 (Isolated Raw Markdown Code Blocks)**: Each file is synthesized in isolation where the LLM returns raw Java code wrapped in a clean Markdown fence (` ```java `).
  - The `MarkdownCodeExtractor` and `MarkdownMultiFileParser` strip the fences cleanly, eliminating JSON string escaping errors entirely!

### Decision 3: 3-Wave Topological Contract-First Code Synthesis
- **Problem**: Generating complete codebases in a single LLM prompt leads to hallucinated imports, missing DTOs, and interface mismatch errors.
- **Our Solution**: Partitioned code synthesis into 3 ordered topological waves:
  - **Wave 1 (Contracts & Schemas)**: `pom.xml`, DTOs, Entities, Exceptions, Enums, and Service Interfaces.
  - **Wave 2 (Implementations & Controllers)**: Service implementations and REST controllers consuming Wave 1 contracts.
  - **Wave 3 (Tests & Verification)**: Unit and integration test suites validating Wave 2 components.

### Decision 4: Empirical Build Verification & Anti-Looping Self-Healing Memory
- **Problem**: Self-healing loops often get trapped in "short-term memory blindspots", repeatedly making minor variations of the same failed fix hypothesis (e.g., tweaking version numbers).
- **Our Solution**:
  - Verification is strictly empirical: real local process execution (`mvn test`).
  - During retries, `QAValidatorAgent` tracks past failed attempts, reasoning, and error outputs, injecting this history into the LLM prompt under a **Critical Anti-Looping Directive**. Reading its own past failed hypotheses forces the LLM to pivot autonomously to a fundamentally different fix strategy.

### Decision 5: Systemic Build & Plugin Error Diagnosis
- **Problem**: Compiler plugin crashes (`maven-compiler-plugin`, JDK 21 AST errors) output zero Java source file error lines, causing LLMs to ignore `pom.xml` and fail repair passes.
- **Our Solution**: Whenever compiler/plugin execution errors occur, `LlmClientManager` automatically includes `pom.xml` in the diagnostic set and places `pom.xml` at the **VERY TOP** of the LLM context window as `// === PRIMARY BUILD DESCRIPTOR (pom.xml) ===`.

### Decision 6: Synchronous OS Disk Flush Protocol
- **Problem**: Multi-threaded Java file writing uses kernel page caching by default. When Maven is launched immediately after file synthesis, `javac` occasionally reads 0-byte or incomplete file buffers.
- **Our Solution**: Updated all file writing utilities to use `StandardOpenOption.SYNC`, forcing the OS kernel to flush both file data and filesystem metadata directly to physical storage before compilation begins.

### Decision 7: Resilient Phase-1 Manifest Auto-Repair Engine
- **Problem**: Generating large project manifests (25+ files) can occasionally truncate JSON responses near the tail end, causing JSON parser crashes.
- **Our Solution**: Built a 2-tier auto-repair engine in `ManifestParser`: Tier 1 auto-completes truncated closing brackets (`\n  ]\n}`), and Tier 2 regex parser extracts all fully declared file nodes.

---

## 📊 6. Observability & Telemetry Artifacts

**AgenticSDLC** provides end-to-end auditability and observability across every step of the orchestration pipeline.

### 📝 Step-by-Step Trajectory Log (`workspaces/<project_id>/execution_trajectory.json`)
Per assignment requirements, every agent action, decision, timing, diagnostic reasoning, and modified file is recorded in real time:

```json
{
  "projectId" : "url-shortner",
  "projectName" : "URL Shortener",
  "languageStack" : "JAVA",
  "scenarioType" : "GREENFIELD",
  "startTime" : "2026-08-08T22:42:00Z",
  "endTime" : "2026-08-08T22:42:45Z",
  "finalStatus" : "PASSED",
  "steps" : [
    {
      "stepIndex" : 1,
      "stage" : "STAGE_1_REQUIREMENT_ANALYSIS",
      "agent" : "PlanGeneratorAgent",
      "status" : "COMPLETED",
      "timestamp" : "2026-08-08T22:42:02Z",
      "details" : "Plan generated at .ai-plan/plan.md",
      "filesModified" : [ ".ai-plan/plan.md" ]
    },
    {
      "stepIndex" : 2,
      "stage" : "STAGE_2_HUMAN_GOVERNANCE",
      "agent" : "HumanGatekeeperAgent",
      "status" : "APPROVED",
      "timestamp" : "2026-08-08T22:42:05Z",
      "details" : "Plan approved by human operator"
    },
    {
      "stepIndex" : 3,
      "stage" : "STAGE_3_CODE_SYNTHESIS",
      "agent" : "CodeEngineerAgent",
      "status" : "COMPLETED",
      "timestamp" : "2026-08-08T22:42:30Z",
      "details" : "Topological code synthesis complete — 27 files written"
    },
    {
      "stepIndex" : 4,
      "stage" : "STAGE_4_QA_SELF_HEALING_ATTEMPT_1",
      "agent" : "QAValidatorAgent",
      "status" : "DIAGNOSING",
      "timestamp" : "2026-08-08T22:42:35Z",
      "details" : "Analyzing build failure for attempt 1"
    },
    {
      "stepIndex" : 5,
      "stage" : "STAGE_4_QA_PATCH_APPLIED",
      "agent" : "CodeEngineerAgent",
      "status" : "PATCHED",
      "timestamp" : "2026-08-08T22:42:40Z",
      "details" : "Applied self-healing patch for 2 files on attempt 1",
      "diagnosticReasoning" : "- Root Cause: Lombok TypeTag crash on JDK 21.\n- Fix Strategy: Update pom.xml annotationProcessorPaths.",
      "filesModified" : [ "pom.xml" ]
    },
    {
      "stepIndex" : 6,
      "stage" : "STAGE_4_QA_VALIDATION",
      "agent" : "QAValidatorAgent",
      "status" : "PASSED",
      "timestamp" : "2026-08-08T22:42:45Z",
      "details" : "Build and tests passed cleanly on attempt 2"
    }
  ]
}
```

---

## 🛠️ 7. Setup & Execution Instructions

### 1️⃣ Prerequisites
1. Java 21 JDK installed (`java -version`).
2. Apache Maven installed (`mvn -version`).

### 2️⃣ Environment Variables
Export your Google Gemini API key and model name:

```bash
export LLM_API_KEY="your-gemini-api-key"
export LLM_MODEL="gemini-2.5-flash"
```

### 3️⃣ Running the Interactive Prototype
Run the CLI orchestrator interactively:

```bash
mvn compile exec:java -Dexec.mainClass="com.schwab.agenticsdlc.AgenticSdlcApplication"
```

### 4️⃣ Running Automated Verification Suite
Execute the 30-unit-test verification suite:

```bash
mvn clean test
```

---

## 🏆 8. Key Achievements

1. **✅ 100% Test Suite Reliability**: 30/30 unit tests passing cleanly across `OrchestratorEngine`, `CodeEngineerAgent`, `QAValidatorAgent`, `LlmClientManager`, `WorkspaceManager`, `ManifestParser`, `CliRendererLogAppender`, and `ExecutionTrajectoryLogger`.
2. **🔧 Autonomous Build Self-Healing**: Successfully diagnoses and repairs JDK 21 compiler crashes, missing imports, and broken dependencies without human code intervention.
3. **💻 Enterprise Terminal UX & Observability**: Integrated Logback appender (`CliRendererLogAppender`) routing 100% of internal logs through a formatted terminal UI while persisting structured step-by-step trajectory JSON logs.

---

## 💡 9. Areas for Future Improvement & Technical Roadmap

While **AgenticSDLC** provides a solid, working prototype with multi-agent orchestration and self-healing build verification, bringing it to full production parity with state-of-the-art coding agents (such as Antigravity or Claude CLI) requires additional development time, resources, and architectural refinement:

1. **🌐 Multi-Language & Technology Ecosystem Expansion**:
   - Extend language stack classifiers and build execution templates beyond Java to natively support **Python (pytest / poetry)**, **TypeScript/Node.js (npm / vitest)**, and **Go (go test)** ecosystems.

2. **🕸️ Decentralized Multi-Agent & Sidecar Observability**:
   - Transition from a single orchestrator process into a **decentralized event-driven agent network**, where independent agent sidecars handle code generation, static analysis, and observability telemetry concurrently across distributed worker nodes.

3. **⚡ High-Throughput Parallel Synthesis**:
   - Parallelize Wave 1 leaf contract generation across 8 worker threads to reduce initial project creation latency from 50s down to ~15s.

4. **🌳 In-Memory LSP & Tree-Sitter AST Validation**:
   - Integrate in-memory Language Server Protocol (LSP) and Tree-Sitter AST validation to catch syntax and symbol reference errors instantly before invoking local process builds (`mvn test`).

5. **🔍 Local Vector Indexing for Brownfield Codebases**:
   - Incorporate a local vector index (HNSW / Lucene) for instant, semantic context retrieval across massive, legacy brownfield codebases.
  
6. **🧠 Extended Context & Long-Horizon Memory Management**:
   - Shift from localized generation contexts to long-horizon memory management and repo-wide dependency tracking.
   - Improve first-pass code generation quality to reach parity with SOTA coding agents—drastically reducing reliance on reactive self-healing retry loops, token overhead, and build-fix latency.

7. **🎯 Domain-Specific Skill & Enterprise Pattern Injection**:
   - Equip code-generation agents with deterministic skill sets and strict architectural rulesets, specifically tailored to idiomatic **Java, Maven, and Spring Boot** standards.
   - Enforce enterprise best practices (e.g., Layered/Clean Architecture, DTO-Entity separation, standardized REST conventions, and clean `pom.xml` dependency management) to prevent code quality drift and anti-patterns.

