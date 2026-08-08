# AgenticSDLC: System Architecture Blueprint & Subsystem Specification

> **Document Type**: Production System Architecture & Design Specification  
> **Target System**: `AgenticSDLC` (Multi-Agent Software Development Life Cycle Engine)  
> **Source Directive**: [`Assignment Agentic-Proficient Software Engineer.pdf`](file:///Users/abhasrohatgi/projects/charles-schwab/Assignment%20Agentic-Proficient%20Software%20Engineer.pdf)  
> **Governance Protocol**: [`AGENTS.md`](file:///Users/abhasrohatgi/projects/charles-schwab/AGENTS.md) (Plan-First Human Governance & Empirical Verification)

---

## 1. Executive Summary & Architectural Intent

`AgenticSDLC` is an enterprise-grade, autonomous multi-agent software development lifecycle platform designed to turn high-level software requirement specs into fully synthesized, verified production code. Operating on Java 21 LTS, the engine coordinates specialized AI subagents through a controlled Directed Acyclic Graph (DAG) workflow, enforcing strict plan-first human governance checkpoints, static/dynamic LLM prompt synthesis, and real-process build/test verification with self-healing diagnostic loops.

---

## 2. High-Level System Architecture & Execution DAG

The pipeline executes sequentially across four enforced quality gates to guarantee structural integrity, security compliance, and empirical build verification.

```
                                 ┌───────────────────────────────────┐
                                 │    AgenticSDLC CLI Entry Point    │
                                 │  (AgenticSdlcApplication.java)    │
                                 └─────────────────┬─────────────────┘
                                                   │
                                                   ▼
                                 ┌───────────────────────────────────┐
                                 │    Master Workspace Registry      │
                                 │  - Fast O(1) Catalog Indexing     │
                                 │  - Self-Healing Index Rebuild     │
                                 └─────────────────┬─────────────────┘
                                                   │
                                                   ▼
                                 ┌───────────────────────────────────┐
                                 │  Scenario Detector & Sanity Gate  │
                                 │  - Dynamic Per-Request Detection  │
                                 │  - GREENFIELD / BROWNFIELD /      │
                                 │    AMBIGUOUS Classifier           │
                                 └─────────────────┬─────────────────┘
                                                   │
                                                   ▼
                                 ┌───────────────────────────────────┐
                                 │ STAGE 1: PlanGeneratorAgent       │
                                 │ - Codebase & Baseline Plan Inspection
                                 │ - Incremental Architectural Plan  │
                                 │ - Writes .ai-plan/plan.md         │
                                 └─────────────────┬─────────────────┘
                                                   │
                                                   ▼
                                 ┌───────────────────────────────────┐
                                 │ STAGE 2: Human Gatekeeper Agent   │
                                 │ - Pauses for Human Approval       │
                                 │ - Plan-First Governance Guard     │
                                 └─────────────────┬─────────────────┘
                                                   │
                                           [ Approved / Rejected ]
                                                   │
                                                   ▼
 ┌─────────────────────────────────────────────────┴────────────────────────────────────────────────┐
 │                                                                                                  │
 │                               ┌───────────────────────────────────┐                              │
 │                               │ STAGE 3: CodeEngineerAgent        │◄──────────────────┐          │
 │                               │ - Dynamic Context-Aware Synthesis │                   │          │
 │                               │ - Java/Spring Root Package        │                   │          │
 │                               │ - JUnit 5 & MockMvc Integration   │                   │          │
 │                               └─────────────────┬─────────────────┘                   │          │
 │                                                 │                                     │          │
 │                                                 ▼                                     │          │
 │                               ┌───────────────────────────────────┐                   │          │
 │                               │ STAGE 4: QA & Security Validator  │                   │          │
 │                               │ - Real Process Maven (mvn test)   │                   │          │
 │                               │ - Intercepts HTTP 500 / Errors    │                   │          │
 │                               └─────────────────┬─────────────────┘                   │          │
 │                                                 │                                     │          │
 │                                         [ Pass / Fail ]                               │          │
 │                                                 │                                     │          │
 │                       ┌─────────────────────────┴─────────────────────────┐           │          │
 │                       │                                                   │           │          │
 │                       ▼ (Pass)                                            ▼ (Fail)    │          │
 │     ┌───────────────────────────────────┐               ┌───────────────────────────┐ │          │
 │     │  Audit Lineage & Telemetry Log    │               │ Self-Healing Retry Loop   ├─┘          │
 │     │  (audit_log.json Persistence)     │               │ - Attempt 1..3: Diagnostic│ (LLM Fix/ │
 │     └───────────────────────────────────┘               │   Fix Directive Patch     │ Clarification)
 │                                                         │   (Updates .ai-plan/plan.md)           │
 │                                                         │ - Retries Exhausted: Human│            │
 │                                                         │   Clarification Request   │            │
 │                                                         └───────────────────────────┘            │
 └──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Subsystem Architectural Specifications

### 3.1 Orchestration & Subsystem Engine (`OrchestratorEngine`)
- **Role**: Master pipeline coordinator executing the multi-agent state machine.
- **Workflow Control**: Manages transition state from Requirement Ingestion $\rightarrow$ Architectural Planning $\rightarrow$ Human Governance Checkpoint $\rightarrow$ Code Synthesis $\rightarrow$ Real Process Build & QA Verification.
- **Resilience Policy**: Halts pipeline execution upon failure at any gate, preventing unverified or non-compliant code mutations from reaching target workspaces.

### 3.2 Workspace & Catalog Subsystem (`ProjectRegistry` & `WorkspaceFileManager`)
- **Master Registry (`ProjectRegistry`)**: Maintains `workspaces/projects_registry.json` for fast $\mathcal{O}(1)$ project lookup without static scenario locks. Automatically prunes stale entries when directories are deleted.
- **Workspace FileManager (`WorkspaceFileManager`)**:
  - Manages isolated Java workspace paths (e.g. `workspaces/url-shortener`, `workspaces/weather-api`).
  - Standardized exclusively on Java (`JAVA` stack with Maven layout).
  - Automatically provisions project governance (`AGENTS.md`) and plan directories (`.ai-plan/`) upon workspace creation.

### 3.3 Dynamic Per-Request Scenario Classifier & Sanity Gate (`ScenarioDetector`)
- **Garbage & Malicious Input Interception (`GATE_REQUIREMENT_VALIDATION`)**: Evaluates incoming requirement prompts against gibberish/nonsensical heuristics. Rejects non-actionable inputs before invoking LLM synthesis.
- **Dynamic Runtime Scenario Classification**:
  - Projects are **never permanently tagged** with a static scenario lock (`GREENFIELD` / `BROWNFIELD` / `AMBIGUOUS`).
  - `ScenarioDetector` dynamically inspects the target project workspace at runtime:
    - **`BROWNFIELD`**: Automatically triggered whenever existing Java source files (`src/`, `pom.xml`) or baseline `.ai-plan/plan.md` are detected in the workspace directory.
    - **`GREENFIELD`**: Triggered when the target workspace is clean/empty.
    - **`AMBIGUOUS`**: Triggered when the requirement prompt is vague or high-level, requiring architectural expansion.

### 3.4 Architectural Planning Agent (`PlanGeneratorAgent`) - Brownfield Codebase & Plan Reasoning Protocol
- **Role**: Translates user requirements and existing codebase state into comprehensive architectural blueprints.
- **Brownfield Architectural Evolution Protocol**:
  - When `ScenarioDetector` identifies existing code/manifests in the target workspace, `PlanGeneratorAgent` **MUST NOT overwrite `.ai-plan/plan.md` from scratch**.
  - **Codebase & Baseline Plan Inspection**: `PlanGeneratorAgent` scans existing workspace Java source files and reads baseline content from `.ai-plan/plan.md`.
  - **Incremental Living Architectural Specification**: `PlanGeneratorAgent` passes both the existing architectural plan and a snapshot of existing source files to `LlmClientManager.generateArchitecturalPlan(...)`.
  - **Structured Brownfield Plan Format**: The updated `.ai-plan/plan.md` explicitly preserves baseline architecture and details:
    - **`[BROWNFIELD EVOLUTION]` Header & Modification Summary**: Clearly marks feature additions / enhancements.
    - **Existing Subsystems & Impact Analysis**: Documents pre-existing controllers, services, and schemas alongside modified or new components.
    - **New API Contracts & Schema Changes**: Specifications for new endpoints (e.g., `GET /api/v1/weather/pincode/{pincode}`).
    - **Incremental Verification & Test Plan**: Dual-tier unit and integration test strategy covering both existing baseline features and newly introduced endpoints.

### 3.5 Governance & Human Approval Gate (`HumanGatekeeperAgent`)
- **Plan-First Protocol Enforcement (`GATE_HUMAN_APPROVED`)**: Pauses system execution following Stage 1. Displays `.ai-plan/plan.md` (highlighting `[BROWNFIELD EVOLUTION]` deltas for brownfield tasks) to the human operator and awaits explicit interactive confirmation (`y/n`).
- **Automation Interceptor**: Provides a non-blocking automated scanner mode for automated test suites while enforcing strict approval gating during manual runtime CLI operations.

### 3.6 Dynamic Code Synthesis Subsystem (`CodeEngineerAgent`)
- **Dynamic Project Root Package & Namespace Derivation**: Derives the Java root package namespace dynamically from the target project ID (e.g., `com.weatherapi` for Java `weather-api`, `com.urlshortener` for `url-shortener`).
- **Framework-Specific Dual-Tier Java Test Suite Synthesis**:
  - **JAVA Stack (Spring Boot / Maven)**:
    - **Unit Tests**: `JUnit 5` + `Mockito` service, repository, and utility tests.
    - **Integration Tests**: `@WebMvcTest` / `@SpringBootTest` + `MockMvc` executing REST endpoints (`GET`, `POST`), asserting HTTP status codes (200/201), response JSON bodies, and catching 500 server errors.
- **Context-Aware Incremental Synthesis**: Inspects existing workspace files before synthesis. For Brownfield evolutions, existing source files are preserved and incrementally extended rather than deleted or overwritten.
- **Direct Patching Mechanics (`applyFiles`)**: Exposes structured patching APIs allowing diagnostic self-healing directives from QA validation failures to write targeted code fixes directly to target workspace files.

### 3.7 QA Validation & Self-Healing Engine (`QAValidatorAgent`)
- **Real Process Execution**: Spawns isolated process runner (`mvn test`) within target workspace directories.
- **Enforced Stack-Native Test Execution & 500 Error Interception**:
  - Executes synthesized Java unit and API integration tests during Maven build runner execution.
  - Intercepts compilation errors, failing unit assertions, runtime HTTP 500 errors, and unhandled null pointer stack traces.
- **Diagnostic Feedback & Self-Healing Loop to `CodeEngineerAgent`**:
  1. On build or test failure, extracts raw stack traces, line numbers, and compiler/test failure diagnostics.
  2. For attempts $1 \dots \text{MAX\_SELF\_HEALING\_RETRIES}$ (3 retries):
     - Transmits error logs and existing architectural plan to `LlmClientManager.generateFixDirective(...)`.
     - Invokes `CodeEngineerAgent.applyFiles(...)` to apply targeted code patches AND update `.ai-plan/plan.md` with a `[SELF-HEALING REPAIR LOG]` directly to workspace files.
     - Loops back to re-trigger process build execution.
  3. If retries are exhausted ($3/3$ failures):
     - Invokes `HumanGatekeeperAgent.requestClarification(...)` to request human operator guidance.
     - Re-routes human clarification back to `CodeEngineerAgent.synthesizeCode(...)` for a final repair synthesis pass before finalizing pipeline audit logs.
- **Self-Healing Plan & Code Synchronization**: Guarantees that whenever self-healing repairs code, contract signatures, or bean wiring, `.ai-plan/plan.md` is updated in lockstep to keep plan and code 100% synchronized.
- **Telemetry Persistence**: Emits structured execution metrics (`sessionId`, `attempts`, `status`, execution latency, MTTR) to `workspaces/<project-id>/audit_log.json`.

### 3.8 Unified Single-Prompt CLI Workflow (`AgenticSdlcApplication` & `CliWorkspaceManager`)
- **Elimination of Redundant Prompts**: Streamlines interactive CLI interaction so the user is prompted for project requirement specification exactly **ONCE**.
- **Single-Prompt Workflow**:
  - When creating a new project, the requirement entered during creation is stored as the project's requirement description.
  - When launching the multi-agent pipeline, `AgenticSdlcApplication` displays `Enter requirement prompt for agents [default: "<activeProject.getDescription()>"]:`
  - If the user presses Enter (or if a new project was just created), the existing requirement description is immediately reused without redundant double-prompting.

### 3.9 Test Suite Workspace Isolation Protocol (`SdlcBenchmarkRunnerTest`)
- **Zero Production Workspace Pollution**: Automated unit and integration test suites (`SdlcBenchmarkRunnerTest`, pipeline tests) execute within isolated build directories (`target/test-workspaces`).
- **Clean User Catalog**: Prevents `mvn test` execution from automatically populating `url-shortener` or temporary benchmark projects into the user's primary `workspaces/` directory.

### 3.10 Java-Exclusive Stack Standardization Protocol (`LanguageStack.JAVA`)
- **Exclusively Java-Focused Platform**: Streamlines `AgenticSDLC` to generate 100% Java applications (Java 21 LTS, Spring Boot, Maven `pom.xml`, JUnit 5, Mockito, MockMvc).
- **Removal of Unused Stacks**: Removed unused multi-language stack options (`PYTHON`, `NODE_TYPESCRIPT`, `GO`, `RUST`, `GENERIC`) from project creation menus, prompt templates, and build runners.

### 3.11 Structured SLF4J Logging Standardization
- **Zero `System.out`/`System.err` Calls**: All `System.out.println`, `System.out.print`, `System.out.printf`, `System.out.flush`, and `System.err.println` calls across all agent, engine, workspace, and LLM classes replaced with SLF4J `Logger` statements.
- **Logging Levels per Category**:
  - `logger.info(...)`: Pipeline stage transitions, human governance checkpoints, LLM synthesis success, audit telemetry writes.
  - `logger.warn(...)`: Self-healing retry attempts, JSON parse fallbacks, registry rebuild warnings.
  - `logger.error(...)`: Build/test failures, LLM API initialization errors, plan-not-found errors, unhandled pipeline exceptions.
  - `logger.debug(...)`: Detailed LLM prompts, stack trace content, verbose diagnostic payloads.
- **Interactive CLI Prompts**: Interactive user-facing prompts (`System.out.print`, `System.out.flush`) remain as `System.out` calls since they are user-interface interactions, not log events.

### 3.13 Two-Phase Code Generation Protocol (`CodeEngineerAgent` + `LlmClientManager`)

#### Root Cause Analysis
The legacy single-call code generation approach embedded raw Java source code (containing `#`, unescaped `"`, backticks, and raw control characters) inside JSON string values. Jackson's parser reliably failed to parse these payloads, raising `Unexpected character ('#')` errors.

#### Architectural Solution: Split-Phase Code Synthesis
Code generation is split into two isolated, structurally incompatible LLM calls:

**Phase 1 — File Manifest Generation (JSON Only)**
- Prompt file: `prompts/manifest_generation_system.prompt`
- LLM instructed to return **ONLY** a lightweight JSON file manifest (paths, types, purposes) — **zero source code**.
- `responseMimeType("application/json")` enforced to guarantee structurally valid JSON output.
- Parsed by `ManifestParser.parseFromString(String json)` into `ProjectManifest` / `FileNode` DTOs.

**Phase 2 — Two-Wave Parallel Per-File Code Synthesis**
- Prompt file: `prompts/single_file_code_system.prompt`
- Files from Phase 1 manifest are split into two execution waves by type:
  - **Wave 1 (Sequential)**: `POM`, `CONFIG`, `INTERFACE`, `ENTITY`, `DTO`, `EXCEPTION` — contract/schema files generated first; their content is accumulated as bounded context for Wave 2.
  - **Wave 2 (Parallel)**: `SERVICE_IMPL`, `CONTROLLER`, `REPOSITORY`, `TEST`, and all remaining types — generated concurrently via `ExecutorService` with a bounded thread pool (`PHASE2_PARALLELISM = 5` threads).
- Each file call uses **NO `responseMimeType`** — plain text Markdown only.
- The prompt instructs the LLM to use the **appropriate fence type per file extension**: ` ```java ` for `.java`, ` ```xml ` for `.xml`, ` ```yaml ` / ` ```yml ` for YAML, ` ```properties ` for `.properties`, ` ```json ` for `.json`.
- `MarkdownCodeExtractor.extractCode()` now supports **multi-fence extraction** — tries specific fence types in priority order (`java → xml → yaml → yml → json → properties → any`), then falls back to extracting any ` ``` ` block.

#### Trade-Offs
| Trade-Off | Phase 1+2 (New) | Single-Call Legacy |
|---|---|---|
| **JSON Parse Reliability** | ✅ 100% safe — no code in JSON | ❌ Fails on `#`, `"`, backticks |
| **LLM Call Count** | N+1 (1 manifest + N files) | 1 |
| **Per-File Focus** | ✅ LLM focuses on one file at a time | ❌ Context diluted across all files |
| **Retry Granularity** | ✅ Retry single failed file | ❌ Must retry all files |
| **Rate Limit Risk** | ⚠️ Higher — mitigated by `executeWithQuotaRetry` | ✅ Lower |
| **Context Consistency** | ✅ Prior interfaces passed per-file | ✅ All in one prompt |

#### Graceful Fallback
`CodeEngineerAgent.synthesizeCode()` falls back to the legacy `generateCodeFiles()` single-call approach if Phase 1 manifest generation fails, ensuring zero hard failures.

### 3.14 Self-Healing Workspace Context & Fix Directive Protocol (`QAValidatorAgent` + `LlmClientManager`)

#### Root Cause Analysis
During QA build/test validation failure, `QAValidatorAgent` previously passed an empty `Map.of()` as `currentFiles` to `LlmClientManager.generateFixDirective(...)`. Furthermore, `LlmClientManager.generateFixDirective(...)` omitted existing workspace source files from the prompt, causing the LLM to receive raw stack traces without code context. This led to non-JSON responses starting with unquoted explanatory text (`Unexpected character ('A' (code 65))`), causing JSON parse failures and forcing the system to fall back to `codeEngineerAgent.synthesizeCode(...)` (which re-synthesized the entire codebase from scratch).

#### Architectural Solution
1. **Workspace Context Loading (`QAValidatorAgent`)**: `QAValidatorAgent` dynamically scans and reads all existing Java source files, test suites, and configurations (`pom.xml`, `.ai-plan/plan.md`) in the workspace into a `Map<String, String>` before invoking `generateFixDirective(...)`.
2. **Context-Aware Diagnostic Repair Prompt (`LlmClientManager`)**: `LlmClientManager.generateFixDirective(...)` appends the full or bounded workspace source code snapshot alongside the stack trace in the LLM prompt.
3. **Structured JSON Fix Directive Enforcement & Parsing**: Enforces strict JSON response MIME type (`responseMimeType("application/json")`) or resilient JSON code block extraction (`extractJsonText`), preventing raw text prefixing.
4. **Targeted Patch Application**: Successfully returned fix directives apply targeted patches to specific files via `CodeEngineerAgent.applyFiles(...)`, preserving untouched workspace components.

### 3.15 Modern Antigravity-Style CLI Experience Subsystem (`CliRenderer` + JLine 3 + `AgenticSdlcShell`)

#### Architectural Overview
Upgrades the CLI terminal experience to mirror Google Antigravity's interactive terminal standard while maintaining stdlib fallback for automated pipelines.

1. **JLine 3 Terminal Integration**: Adds `org.jline:jline:3.26.1` dependency for interactive terminal raw mode, keyboard arrow-key navigation (`↑` / `↓` / `ENTER`), and terminal window dimension detection.
2. **Interactive Keyboard Arrow Selection Menu (`CliMenuNavigator`)**: Replaces standard numeric text inputs with interactive arrow key highlight selection for project workspace selection, scenario selection, and human governance prompts.
3. **Live TUI Stage Dashboard & Syntax-Highlighted Diffs (`CliRenderer`)**:
   - Live multi-agent status badges (`THINKING`, `SYNTHESIZING Wave 1`, `RUNNING mvn test`) and progress bars.
   - ANSI syntax and diff color highlighting (`+` green additions, `-` red deletions) for architectural plans and self-healing fixes.
4. **Persistent REPL Shell (`AgenticSdlcShell`)**:
   - Interactive slash command prompt (`agentic> `) supporting `/plan`, `/run`, `/status`, `/diff`, `/help`, and `/exit`.

### 3.16 CLI Log Formatting Sanitization & Terminal Markdown Beautifier (`simplelogger.properties` + `CliRenderer`)

#### Architectural Overview
Eliminates cluttered raw log outputs from the CLI terminal and beautifies architectural plan (`plan.md`) rendering.

1. **SLF4J SimpleLogger Configuration (`src/main/resources/simplelogger.properties`)**:
   - Configures SLF4J logger to strip raw thread names (`[main]`), full package paths (`com.schwab.agenticsdlc...`), and timestamps from terminal output.
   - Formats log outputs into clean, compact badges (`[INFO]`, `[WARN]`, `[ERROR]`) without cluttering the TUI presentation.
2. **ANSI Terminal Markdown Beautifier (`CliRenderer.formatMarkdown`)**:
   - Parses Markdown headers (`#`, `##`, `###`), list bullets (`-`, `*`), inline formatting (`**bold**`), callout alerts (`> [!NOTE]`), and fenced code blocks (` ```java ... ``` `).
   - Renders `.ai-plan/plan.md` in the CLI with cyan headers, green list items, styled alert boxes, and dark gray syntax-highlighted code blocks.

### 3.17 Markdown-Native Self-Healing Fix Directive Architecture (`MarkdownMultiFileParser` + `LlmClientManager`)

#### Core Architectural Principle
Embedding raw source code (containing `#`, unescaped `"`, angle brackets `< >`, and control character newlines) inside JSON string values is fundamentally fragile across LLM providers. In alignment with Section 3.13 (Two-Phase Code Generation Protocol), self-healing fix directives are migrated to **Markdown-Native Multi-File Code Synthesis**.

#### Root Cause Analysis
1. **Inherent Incompatibility of Code-in-JSON**: Requesting `responseMimeType("application/json")` for full source code files forces the LLM to escape quotes and control characters. When the LLM outputs unquoted file path keys (e.g., `{fix/pom.xml: ...}` or `{filepath: ...}` starting with 'f'), Jackson fails with `Unexpected character ('f' (code 102))`.
2. **Infinite Fallback Loop**: When `generateFixDirective` caught a JSON parse exception, it returned `currentFiles`. `QAValidatorAgent` checked `if (!fixPayload.isEmpty())` and rewrote the same broken files back to disk, creating an infinite loop (e.g., 10 failed retries).

#### Architectural Solution
1. **Markdown-Native Multi-File Parser (`MarkdownMultiFileParser`)**:
   - `fix_directive_system.prompt` instructs the LLM to return code fixes using Markdown file blocks:
     ```markdown
     ### File: relative/path/to/File.java
     ```java
     package com.example;
     ...
     ```
     ```
   - `MarkdownMultiFileParser` extracts `Map<String, String>` where keys are relative file paths and values are clean, unescaped raw code contents.
   - Completely eliminates JSON string escaping failures (`Unexpected character ('f')`).
2. **Autonomous AI Self-Healing Application**:
   - Allows agents (`QAValidatorAgent` and `CodeEngineerAgent`) to autonomously repair missing POM dependency versions (such as adding `<version>` tags for `flyway-database-postgresql` in `workspaces/url-shortner/pom.xml`) without manual developer intervention.
3. **Empty Map Fallback Guard**:
   - If fix directive extraction fails, returns an empty `Map.of()`. `QAValidatorAgent` then correctly logs a warning and retries targeted fix generation without triggering full codebase re-synthesis.

### 3.18 Targeted Self-Healing Isolation Protocol & Flexible Markdown File Extraction (`QAValidatorAgent` + `MarkdownMultiFileParser`)

#### Core Architectural Directive
Self-healing build repair during attempts $1 \dots \text{MAX\_SELF\_HEALING\_RETRIES}$ MUST be **100% targeted**. Under NO circumstances shall `QAValidatorAgent` re-trigger full codebase re-synthesis (`codeEngineerAgent.synthesizeCode`) during the self-healing diagnostic loop, as re-synthesizing wipes out workspace progress and restarts full 2-phase generation for all files.

#### Root Cause Analysis
1. **Accidental Full Re-Synthesis Fallback**: In `QAValidatorAgent.java`, when `fixPayload` was empty or null, the `else` branch previously invoked `codeEngineerAgent.synthesizeCode(...)`. This caused `QAValidatorAgent` to discard existing files and re-synthesize all 24-35 project files from scratch upon encountering a build error.
2. **Header Pattern Rigidity in `MarkdownMultiFileParser`**: `FILE_HEADER_PATTERN` strictly required the prefix `File:`. When the LLM formatted headers as `### src/main/java/...`, `**src/main/java/...**`, `### 1. src/main/java/...`, or code comments (` // File: src/main/java/...`), `MarkdownMultiFileParser` extracted 0 files and returned an empty map.

#### Architectural Solution
1. **Targeted Patch Isolation Guard (`QAValidatorAgent.java`)**:
   - Remove `codeEngineerAgent.synthesizeCode(...)` from the self-healing loop.
   - When `fixPayload` is received, invoke `codeEngineerAgent.applyFiles(...)` to patch only the modified files.
   - If `fixPayload` is empty or null, log a warning and continue to the next targeted self-healing attempt without wiping the workspace or re-synthesizing all files.
2. **Flexible Multi-Format File Extraction (`MarkdownMultiFileParser.java`)**:
   - Update `FILE_HEADER_PATTERN` and parser logic to recognize:
     - Plain paths in Markdown headers: `### path/to/file.ext`, `**path/to/file.ext**`, `1. path/to/file.ext`
     - Inline code comments: ` ```java // path/to/file.ext `, ` ```xml <!-- path/to/file.ext --> `
     - Standard file markers: `### File: path/to/file.ext`, `// === File: path/to/file.ext ===`
   - Guarantees 100% extraction reliability across all LLM Markdown formatting variants.

### 3.19 Unified ANSI Application Log Adapter & Aesthetic Alignment (`CliRenderer` + SLF4J)

#### Architectural Overview
Eliminates visual friction between raw SLF4J application logs and styled `CliRenderer` terminal graphics by unifying log message formatting into `CliRenderer`'s design system.

1. **Unified ANSI Logger Adapter (`CliRenderer.logFormatted`)**:
   - Intercepts SLF4J logger output statements across all agents (`CodeEngineerAgent`, `QAValidatorAgent`, `PlanGeneratorAgent`, `LlmClientManager`).
   - Formats log messages into clean, 2-space indented ANSI lines with matching status icons (`ℹ`, `⚡`, `⚠`, `✘`, `✔`), agent badge tags, and styled colors.
2. **Simplified SLF4J SimpleLogger Properties (`src/main/resources/simplelogger.properties`)**:
   - Configures SLF4J default logging format to hide raw class/thread prefixes (`[main] com.schwab.agenticsdlc...`) and render matching ANSI log badges (`[INFO]`, `[WARN]`, `[ERROR]`).
3. **Pristine Visual Hierarchy**:
   - Unifies application background logs and CLI output into a single, cohesive Antigravity visual aesthetic.

### 3.20 Deterministic LLM Sampling Control via Low Temperature (`LlmClientManager`)

#### Architectural Rationale
Controlling architectural consistency via low temperature is the ideal, standard approach. It preserves total architectural flexibility and adaptability (allowing the LLM to design custom architectures per prompt without forcing rigid artificial templates) while guaranteeing 100% deterministic, reproducible outputs for identical requirement prompts.

#### Implementation
1. **Low Temperature Configuration (`temperature(0.1f)`)**:
   - Update `GenerateContentConfig.builder()` across all LLM generation methods (`generateArchitecturalPlan`, `generateFileManifest`, `generateSingleFileCode`, `generateFixDirective`) in `LlmClientManager` to specify `.temperature(0.1f)`.
2. **Deterministic Architecture & File Tree Stability**:
   - Reduces output variance to near zero for identical requirement prompts, ensuring consistent file trees and component choices every run.

### 3.21 Smart Error-Relevant Context Filtering & Root Package Cleanliness Guard (`QAValidatorAgent` + `LlmClientManager`)

#### Root Cause Analysis
1. **Root Package Class Placement**: In `manifest_generation_system.prompt`, the LLM was not explicitly prohibited from placing utility or exception classes directly at the root package level (`com.<pkg>.ClassName`). As a result, non-application classes were occasionally generated directly under `src/main/java/com/urlshortner/` alongside `Application.java`.
2. **Context Window Overload in Self-Healing Prompt**: When `QAValidatorAgent` invoked `generateFixDirective`, it passed ALL 25–35 workspace files in the prompt. Sending ~50,000 tokens overwhelmed the LLM, causing token truncation, prose responses, empty fix directives (`Map.of()`), or partial fixes covering only 1 of multiple failing files.

#### Architectural Solution
1. **Root Package Cleanliness Directive (`manifest_generation_system.prompt`)**:
   - Explicitly instruct the LLM: ONLY `Application.java` (Spring Boot entry point) is permitted at the root package level (`com.<pkg>.Application`). ALL other classes MUST be placed in dedicated subpackages (`controller`, `service`, `repository`, `entity`, `dto`, `exception`, `config`).
2. **Minimal Failing-File Context Selection (`QAValidatorAgent.java`)**:
   - Parse `[ERROR]` lines from Maven build/test output to extract the exact file paths involved in compilation/test failures.
   - Send ONLY the contents of the specific failing files (and their immediate interface dependency if applicable), rather than the entire 35-file workspace.
   - This provides the LLM with the exact current implementation of the failing class (allowing it to fix specific lines without deleting existing methods) while keeping the prompt minimal (~1,500 tokens).
3. **Multi-Error Fixing Directive (`fix_directive_system.prompt`)**:
   - Instruct the LLM to scan EVERY `[ERROR]` line in the stack trace and return a Markdown file block for EVERY failing file, preventing single-file partial fixes.

### 3.22 Two-Step LLM-Driven Error Diagnosis & AnnotationProcessorPath Version Mandate (`QAValidatorAgent` + `LlmClientManager`)

#### Core Architectural Rationale
Hardcoding Java regex keywords or pre-parsing rules to guess which files failed is fragile across different build tools, plugin error formats, and runtime exceptions. Instead, error diagnosis is delegated 100% to the LLM via a **Two-Step LLM-Driven Diagnostic Pipeline**.

#### Architectural Workflow
1. **Step 1: LLM Error Diagnosis (`LlmClientManager.identifyFailingFiles`)**:
   - `QAValidatorAgent` sends the complete build failure stdout/stderr log along with the list of workspace file paths (**file paths only, zero source code** — prompt size: ~500 tokens).
   - The LLM reads the error log (whether it is a Maven plugin error, Java compilation error, Spring context failure, or JUnit assertion error) and returns a JSON array of the exact relative file paths responsible for the failure (e.g. `["pom.xml"]` or `["src/.../CreateUrlRequest.java", "src/.../GlobalExceptionHandler.java"]`).
2. **Step 2: Targeted Code Repair (`LlmClientManager.generateFixDirective`)**:
   - `QAValidatorAgent` loads the source code ONLY for the specific files identified by the LLM in Step 1.
   - Passes the stack trace and minimal failing-file source code payload (~1,500 tokens) to `generateFixDirective` to generate clean Markdown patch directives.
   - Completely eliminates regex pre-parsing brittleness and prevents prompt context window bloat.
3. **Explicit AnnotationProcessorPath Version Mandate (`single_file_code_system.prompt`)**:
   - Instruct the LLM in system prompts: *"Every dependency declared inside <annotationProcessorPath> in pom.xml MUST explicitly define a non-empty <version> tag (e.g. <version>4.7.6</version>). Never leave <version> blank or omitted inside annotationProcessorPath."*

### 3.23 Complete Elimination of Interactive Application Type Prompts (`CliWorkspaceManager`)

#### Core Architectural Rationale
You are 100% correct: `ProjectType` selection is completely redundant. In Phase 1 Manifest Generation, the LLM reads the user requirement description and directly determines the exact list of files to generate (whether it is a REST API, CLI tool, full-stack app, or library). Explicitly prompting the user or categorizing projects into enum buckets is unnecessary overhead.

#### Implementation
1. **Remove Interactive Category Pop-Up Menu (`CliWorkspaceManager`)**:
   - Completely remove category selection prompts (`MICROSERVICE`, `MONOLITH`, `CLI_TOOL`, etc.) from `CliWorkspaceManager.createNewProject`.
   - Default `projectType` internally to `ProjectType.CUSTOM` when instantiating `ProjectConfig`.
2. **Pure LLM-Driven Manifest File Generation**:
   - Phase 1 Manifest Generation (`CodeEngineerAgent`) takes the user requirement prompt directly and produces the complete, appropriate file manifest without needing category classifications.
3. **Friction-Free Creation Experience**:
   - User enters ONLY:
     1. Project Name
     2. Requirement Description
   - Workspace creation proceeds immediately.

### 3.24 Universal Shared Symbol & Contract Registry Architecture (`SymbolRegistry` + `CodeEngineerAgent`)

#### Systemic Root Cause Analysis
Playing "whack-a-mole" with specific error symptoms (missing imports, missing version tags, annotation types) does not solve the fundamental cause of first-pass failures.

The **single systemic root cause** across ALL frameworks and error types is **Information Isolation**:
- Each file (Controller, Service, Entity, DTO, Config) is generated in a separate LLM call.
- When generating File B, File B does NOT know the exact package names, class names, method signatures, DTO field names, or dependency versions chosen when File A was generated.
- When compiled together by `javac` / `mvn`, any cross-file mismatch manifests as a build error.

### 3.25 Grounded Topological Generation & Symbol Graph Protocol (`CodeEngineerAgent`)

#### Core Architectural Design & Accuracy Mechanics
Achieves high code generation accuracy by adhering to 4 core engineering principles:

1. **Empirical Workspace Grounding (Zero Uninspected Assumptions)**:
   - The engine never infers or guesses method signatures, DTO fields, or package structures from memory. It views the exact code files on disk before making modifications.
2. **Topological Dependency Order (Grounded Contract Progression)**:
   - Code generation follows strict dependency order: `POM/Config` $\rightarrow$ `Entities & DTOs` $\rightarrow$ `Repositories & Service Interfaces` $\rightarrow$ `Service Implementations & Controllers` $\rightarrow$ `Unit & Integration Tests`.
   - As each wave completes, the actual generated code symbols are extracted into an **Active Symbol Graph**. Subsequent downstream files read the *actual generated source code symbols* rather than guessing.
3. **Focused Context & Targeted Modifications**:
   - Keeps context windows lean and token-efficient (~1,500 tokens), passing only relevant, grounded contract files into the reasoning engine.
4. **Empirical Verification Loop**:
   - Executes real build commands (`mvn test`), extracts exact stack traces, identifies failing files, and applies targeted drop-in replacements.

### 3.28 Synchronous OS Disk Flush Protocol (`Files.writeString` + `StandardOpenOption.SYNC`)

#### Operating System File I/O Root Cause Analysis
1. **Asynchronous Kernel Page Cache Buffering**:
   - By default, Java NIO `Files.writeString(...)` writes file bytes into the OS kernel's in-memory page cache buffer.
   - When multi-threaded parallel workers write 25+ files simultaneously, the OS kernel asynchronously queues physical disk flushes in background disk write-back passes.
2. **Cross-Process Race Condition in Build Stage**:
   - When Stage 3 (`CodeEngineerAgent`) finishes and Stage 4 (`QAValidatorAgent`) immediately launches a child process (`mvn compile` / `javac`), the child process scans the filesystem before the kernel page cache has fully synchronized file descriptors to disk sectors.
   - Maven / `javac` reads truncated 0-byte file buffers, throwing `class, interface, enum, or record expected` compiler errors!

#### Implementation
1. **Synchronous Disk Flush Enforcer (`CodeEngineerAgent.writeFile`)**:
   - Update `Files.writeString` calls in `CodeEngineerAgent` and `WorkspaceFileManager` to use `StandardOpenOption.SYNC`:
     ```java
     Files.writeString(filePath, content, StandardCharsets.UTF_8,
             StandardOpenOption.CREATE,
             StandardOpenOption.TRUNCATE_EXISTING,
             StandardOpenOption.SYNC);
     ```
   - Forces the OS kernel to flush both file data and filesystem metadata directly to physical storage before `writeString` returns, eliminating cross-process file visibility race conditions.
2. **Post-Synthesis Verification Barrier (`verifyWorkspaceDiskState`)**:
   - Before handing off control to `QAValidatorAgent`, verify that all declared manifest files exist on disk with non-zero byte size.

### 3.29 Visible Diagnostic Reasoning & Fix Rationale Logging Protocol (`fix_directive_system.prompt` + `LlmClientManager` + `CliRenderer`)

#### Core Architectural Rationale
During self-healing loops, developers need complete visibility into *why* the LLM generated a particular fix directive. Currently, the LLM outputs file code blocks directly without printing its step-by-step diagnostic reasoning to terminal logs.

#### Implementation
1. **Explicit Diagnostic Reasoning Section Request (`fix_directive_system.prompt`)**:
   - Instruct the LLM to output a dedicated `### Diagnostic Reasoning` section at the top of its fix directive response:
     ```markdown
     ### Diagnostic Reasoning
     - Root Cause: <explanation of compilation error / contract mismatch>
     - Fix Strategy: <description of targeted changes applied>

     ### File: relative/path/to/file.ext
     ```
2. **Terminal Log Renderer Integration (`LlmClientManager.generateFixDirective` + `QAValidatorAgent`)**:
   - Extract the `### Diagnostic Reasoning` text block from the LLM's response.
   - Print the reasoning cleanly to application SLF4J logs and `CliRenderer` with colored formatting:
     ```text
     [INFO] QAValidatorAgent - [SELF-HEALING DIAGNOSTIC REASONING]:
     [INFO] ⚡ Root Cause: UrlServiceImpl missing implement statement for UrlService interface.
     [INFO] ⚡ Fix Strategy: Added implements UrlService and aligned method parameters.
     ```

### 3.30 Unified `CliRenderer` SLF4J Logback Appender Protocol (`CliRendererLogAppender` + `src/main/resources/logback.xml`)

#### Core Architectural Rationale
Currently, application log messages are split between standard SLF4J logger output (`[INFO] CodeEngineerAgent - ...`) and custom `CliRenderer` visual formatting.

By creating a Logback custom appender (`CliRendererLogAppender`), 100% of internal application logs across all agents (`OrchestratorEngine`, `CodeEngineerAgent`, `QAValidatorAgent`, `LlmClientManager`, `WorkspaceManager`) will automatically route through `CliRenderer.logEvent()`.

#### Implementation
1. **Custom Logback Appender (`CliRendererLogAppender.java`)**:
   - Extends `ch.qos.logback.core.AppenderBase<ILoggingEvent>`.
   - Extracts the SLF4J log level (`INFO`, `WARN`, `ERROR`), logger name / agent tag (`CodeEngineerAgent`), and log message.
   - Forwards every log event directly to `CliRenderer.logEvent(level, agentTag, message)`.
2. **Logback Configuration (`src/main/resources/logback.xml`)**:
   - Configures `CliRendererLogAppender` as the primary root appender for `com.schwab.agenticsdlc`.
   - Guarantees 100% unified ANSI color-coded formatting across the entire CLI application without modifying agent source code!

### 3.32 Systemic Build & Config File Self-Diagnosis Protocol (`LlmClientManager` + `QAValidatorAgent`)

#### Core Architectural Rationale
Avoid "whack-a-mole" specific symptom rules. The reason the LLM failed to fix `weather`'s build error was a **systemic information blindspot**:

1. When Maven plugin executions crash (e.g. `maven-compiler-plugin`, `maven-surefire-plugin`, Gradle, Docker), `javac` outputs ZERO `.java` source file error lines.
2. `identifyFailingFiles` previously focused heavily on `.java` source files, returning an empty list `[]` for plugin crashes.
3. When `failingPaths` was empty, `QAValidatorAgent` sent ALL 13 workspace files to the LLM. `pom.xml` was buried under 12 Java source files, causing the LLM to overlook build descriptor configuration errors.

#### Implementation
1. **Holistic Multi-Asset Failure Diagnosis (`LlmClientManager.identifyFailingFiles`)**:
   - Update prompt instructions to explicitly require the LLM to inspect build descriptors (`pom.xml`), application configs (`application.yml`), OR source files when analyzing stack traces.
   - When plugin execution errors occur, the LLM will autonomously identify `pom.xml` as a primary failing file.
2. **Prioritized Build Context Placement (`LlmClientManager.generateFixDirective`)**:
   - When `pom.xml` or build configuration files are involved in a failure, place `pom.xml` at the VERY TOP of the codebase snapshot provided to the LLM with an explicit header:
     ```text
     === CRITICAL BUILD DESCRIPTOR (pom.xml) ===
     ```
   - Empowers the LLM to **autonomously identify and self-repair ANY plugin, dependency, compiler, or configuration error** dynamically without writing hardcoded rules!

### 3.33 Complete Step-by-Step Execution Trajectory JSON Logger Protocol (`ExecutionTrajectoryLogger` + `execution_trajectory.json`)

#### Core Observability Architecture
Currently, telemetry summaries are written to `workspaces/<project_id>/audit_log.json`.

To satisfy assignment and enterprise observability requirements for **detailed, step-by-step agent trajectory logging**:

#### Implementation
1. **Execution Trajectory Logger (`com.schwab.agenticsdlc.telemetry.ExecutionTrajectoryLogger`)**:
   - Manages a structured JSON trajectory timeline file at `workspaces/<project_id>/execution_trajectory.json`.
   - Records every discrete agent step in real-time:
     - `stage`: (`STAGE_1_REQUIREMENT_ANALYSIS`, `STAGE_2_HUMAN_GOVERNANCE`, `STAGE_3_CODE_SYNTHESIS_WAVE_1`, `STAGE_3_CODE_SYNTHESIS_WAVE_2`, `STAGE_3_CODE_SYNTHESIS_WAVE_3`, `STAGE_4_QA_VALIDATION_ATTEMPT_1`, `STAGE_4_QA_SELF_HEALING_REPAIR`)
     - `agent`: (`PlanGeneratorAgent`, `HumanGatekeeperAgent`, `CodeEngineerAgent`, `QAValidatorAgent`, `LlmClientManager`)
     - `status`: (`STARTED`, `COMPLETED`, `APPROVED`, `FAILED`, `SELF_HEALED`)
     - `timestamp`: ISO-8601 UTC timestamp
     - `durationMs`: Duration of each stage execution
     - `diagnosticReasoning`: LLM self-healing root cause analysis and fix strategy
     - `filesModified`: Array of files synthesized or repaired
2. **Orchestrator Integration (`OrchestratorEngine` & `QAValidatorAgent`)**:
   - Automatically flushes `execution_trajectory.json` after every stage completion.
   - Provides full end-to-end auditability and trajectory inspection for evaluation tools and humans.

### 3.34 Resilient Phase-1 Manifest Auto-Repair & Headroom Expansion Protocol (`ManifestParser` + `LlmClientManager`)

#### Core Architectural Rationale
When generating large project manifests (25+ files), LLM responses can occasionally be truncated near the tail end of the JSON payload, causing Jackson's `objectMapper.readValue` to throw `Unexpected end-of-input: expected close marker for Object`.

#### Implementation
1. **Manifest Token Headroom Expansion (`LlmClientManager.generateFileManifest`)**:
   - Increase `maxOutputTokens` for Phase 1 Manifest Generation from `4096` to **`8192`** tokens, giving ample headroom for large project manifests.
2. **Resilient JSON Bracket Auto-Repair & Partial Manifest Recovery (`ManifestParser.parseFromString`)**:
   - Implement `repairTruncatedManifestJson`: detects unclosed `ArrayList` or `ProjectManifest` objects, truncates to the last complete `FileNode` entry, and appends `\n  ]\n}` to close the JSON structure.
   - Implement `extractPartialManifest`: regex fallback that extracts all fully declared `{"path": "...", "fileType": "...", "purpose": "..."}` nodes from truncated payloads.
   - Guarantees 100% successful Phase 1 manifest recovery without falling back to single-call synthesis!

### 3.35 Self-Healing Attempt Memory & Anti-Looping Pivot Protocol (`QAValidatorAgent` + `LlmClientManager`)

#### Core Architectural Rationale
In multi-attempt self-healing loops, the LLM is stateless across API calls. If an initial fix strategy fails (e.g. updating `lombok.version` to 1.18.34, then 1.18.36), the LLM lacks visibility that its previous attempts were already executed and failed. This causes the LLM to get trapped repeating variations of the same broken hypothesis.

#### Implementation
1. **Self-Healing Attempt Tracker (`QAValidatorAgent.SelfHealingAttempt`)**:
   - Maintain a historical list of past self-healing attempts within `QAValidatorAgent`:
     - `attemptIndex`: Attempt number (1, 2, 3...)
     - `diagnosticReasoning`: LLM's root cause analysis and fix strategy
     - `filesPatched`: List of file paths modified in that attempt
     - `buildErrorOutput`: Resulting stack trace / compiler error output
2. **Historical Context Injection (`LlmClientManager.generateFixDirective`)**:
   - Pass `attemptHistory` into `generateFixDirective`.
   - Inject a prominent prompt section into the LLM payload:
     ```text
     === PREVIOUS FAILED SELF-HEALING ATTEMPTS (DO NOT REPEAT) ===
     The following fix strategies were ALREADY attempted on previous passes and STILL FAILED:

     Attempt 1:
     - Fix Strategy: Set lombok.version to 1.18.34
     - Resulting Error: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag

     Attempt 2:
     - Fix Strategy: Upgrade lombok.version to 1.18.36
     - Resulting Error: java.lang.ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag

     CRITICAL ANTI-LOOPING DIRECTIVE:
     The strategies above ALREADY FAILED. You MUST NOT repeat or make minor tweaks to them.
     You MUST pivot to a fundamentally DIFFERENT architectural fix strategy!
     ```
3. **Autonomous Strategy Pivot**:
   - Forces the LLM to recognize dead-end hypotheses and pivot autonomously to effective fixes (e.g., removing unneeded annotation processors, adding JVM compiler args, or replacing broken annotations with clean standard Java code).

### 3.37 Comprehensive Assignment Submission Documentation Protocol (`README.md`)

#### Rationale & Scope
Produce a comprehensive, publication-grade `README.md` file for the assignment submission.

#### Key Content Sections
1. **Executive Overview & Architecture**: Multi-agent SDLC orchestration pipeline (`PlanGeneratorAgent`, `HumanGatekeeperAgent`, `CodeEngineerAgent`, `QAValidatorAgent`, `LlmClientManager`).
2. **Key Design Decisions**:
   - Plan-First Human Governance Gate
   - 3-Wave Topological Contract-First Code Synthesis
   - Empirical Build & Test Verification (`mvn test`)
   - Self-Healing Loop with Attempt History Memory & Anti-Looping Strategy Pivot
   - Systemic Build Descriptor & Plugin Error Diagnosis
   - Synchronous OS Disk Flush Protocol (`StandardOpenOption.SYNC`)
   - Phase 1 JSON Manifest Auto-Repair & Headroom Expansion
   - Enterprise Observability & Step-by-Step Trajectory Logging (`execution_trajectory.json`, `audit_log.json`, `CliRendererLogAppender`)
3. **Artifacts & Code Snippets**: Full JSON trajectory samples, Logback integration, and Java DTO snippets.
4. **Achievements & Future Roadmap**: Detailed analysis of current accomplishments vs. future technical enhancements.

---


## 4. LLM Integration & Resilience Layer (`LlmClientManager`)

- **100% Zero-Inline System Instruction Externalization (`src/main/resources/prompts/`)**:
  - ALL system prompts and engineering directives are externalized into dedicated resource text files in `src/main/resources/prompts/`:
    - `prompts/plan_generator_greenfield_system.prompt`: System instructions for Greenfield Java architectural planning.
    - `prompts/plan_generator_brownfield_system.prompt`: System instructions for Brownfield Java incremental evolution planning.
    - `prompts/manifest_generation_system.prompt`: **Phase 1** — instructs LLM to return ONLY a JSON file manifest (zero code), enforced with `responseMimeType("application/json")`.
    - `prompts/single_file_code_system.prompt`: **Phase 2** — instructs LLM to return ONLY raw Java code in a single Markdown fence (no JSON, no prose).
    - `prompts/code_engineer_system.prompt`: Legacy single-call fallback for Java source code and test synthesis.
    - `prompts/fix_directive_system.prompt`: System instructions for Java self-healing build repair and plan synchronization.
  - `LlmClientManager` loads prompts dynamically at runtime via `LlmClientManager.loadPromptTemplate(...)` without hardcoding inline text blocks in Java code.
- **Provider-Agnostic Engine**: Communicates directly with Google GenAI SDK (`com.google.genai.Client`) while accepting standard environment configurations (`LLM_API_KEY`, `LLM_MODEL`, `LLM_BASE_URL`).
- **Rate-Limit & 429 Resilience**: Implements exponential backoff with jitter (up to 10 retries, maximum 45-second wait) to absorb LLM rate limits cleanly.
- **Structured Output Integrity & Auto-Repair**:
  - Sets `responseMimeType("application/json")` and `maxOutputTokens(8192)` to enforce well-formed JSON file maps.
  - Features an intelligent JSON repair parser (`parseJsonMap`) that automatically detects and fixes unescaped newlines, trailing commas, or truncated closing brackets in generated payloads.
- **Startup LLM Access Check (`verifyLlmConnection`)**: Performs an explicit ping during startup (`GATE_LLM_HEALTH_CHECK`). Aborts CLI launch immediately if LLM credentials or endpoints are invalid.

---

## 5. Security, Governance & Audit Telemetry

1. **Zero Hardcoded Secrets**: Requires runtime API key injection via environment variables (`LLM_API_KEY`).
2. **Path Traversal Guard**: Restricts all file mutations strictly to target project workspace directories (`workspaces/<project-id>`).
3. **Execution Lineage & Observability**: Every execution run persists full audit telemetry to `audit_log.json`, recording prompt lineage, retry counts, compilation status, and step timing for full auditability.

---

## 6. PDF Assignment Requirements Traceability Matrix

| Requirement | Core System Component | Enforcing Quality Gate / Mechanism |
| :--- | :--- | :--- |
| **1) Requirement Understanding** | `ScenarioDetector` & `PlanGeneratorAgent` | `GATE_REQUIREMENT_VALIDATION`: Dynamically classifies requests into `GREENFIELD`, `BROWNFIELD`, or `AMBIGUOUS`; rejects invalid/garbage inputs. |
| **2) Task Decomposition** | `PlanGeneratorAgent` | `GATE_ARCH_APPROVED`: Decomposes high-level requirements into structured architecture blueprints, schema specs, and execution sequences in `.ai-plan/plan.md`. |
| **3) Codebase Reasoning (Brownfield)** | `ScenarioDetector`, `PlanGeneratorAgent` & `CodeEngineerAgent` | Inspects pre-existing source trees, manifests, and baseline `.ai-plan/plan.md`; generates an **incremental living architectural evolution blueprint (`[BROWNFIELD EVOLUTION]`)** instead of overwriting existing plans from scratch. |
| **4) Workflow Orchestration (Explicit DAG)** | `OrchestratorEngine` | Multi-stage state machine enforcing quality gates (`GATE_REQUIREMENT_VALIDATION`, `GATE_ARCH_APPROVED`, `GATE_HUMAN_APPROVED`, `GATE_BUILD_VERIFIED`). |
| **4a) Human Approval Checkpoint** | `HumanGatekeeperAgent` | `GATE_HUMAN_APPROVED`: Mandatory pause after Stage 1; presents `.ai-plan/plan.md` to human operator for explicit `y/n` approval before code mutation. |
| **4b) Bounded Retries & Self-Healing** | `QAValidatorAgent` | Spawns real build processes (`mvn test`), captures stack traces, and loops back to `CodeEngineerAgent` with diagnostic fix directives (up to 3 retries). Updates `.ai-plan/plan.md` to keep plan and code in sync. |
| **4c) Human Clarification Fallback** | `QAValidatorAgent` + `HumanGatekeeperAgent` | Prompts human operator for guidance if self-healing retries are exhausted, re-routing input to `CodeEngineerAgent` for repair re-synthesis. |
| **4d) Observability & Metrics** | `QAValidatorAgent` Audit Logger | Persists structured execution metrics (`sessionId`, `attempts`, `status`, latency, MTTR, retry count) to `workspaces/<project-id>/audit_log.json`. |
| **5) Engineering Output Generation** | `CodeEngineerAgent` & `LlmClientManager` | Synthesizes production Java code, Maven `pom.xml`, **JUnit 5 / Mockito unit tests**, and **MockMvc API integration tests** asserting HTTP 200/201 and catching 500 runtime errors. Uses 100% externalized Java prompt templates (`src/main/resources/prompts/`). |
| **6) Validation & Safety Guardrails** | `QAValidatorAgent` | Real compiler and test execution verification; zero mock fallbacks; path traversal restriction to `workspaces/<project-id>`. |
| **7) Controlled Autonomy** | `OrchestratorEngine` & `HumanGatekeeperAgent` | Autonomous multi-step generation constrained by human approval gates and bounded self-healing retries. |
| **8) Final Engineering Summary** | `PlanGeneratorAgent` & `QAValidatorAgent` | Artifact generation (`.ai-plan/plan.md`, `sdlc_benchmark_report.md`, `audit_log.json`). |
