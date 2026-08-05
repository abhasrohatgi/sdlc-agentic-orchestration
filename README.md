# Agentic SDLC Orchestrator + URL Shortener

An agentic software-engineering system: a governed SDLC orchestration engine, and the URL
shortener service it operates on.

> **Status: in progress.** M0 (build foundation and agentic configuration) is complete.
> See [Build status](#build-status) for what currently runs.

## The idea in one paragraph

These are not two unrelated deliverables. The **URL shortener is the target codebase the
orchestrator acts on**. The orchestrator decomposes a requirement into a dependency graph,
executes stages against a sandboxed copy of the shortener, writes real code, runs the real
Maven build as an exit gate, pauses for human approval on high-impact changes, retries with
the failure output as feedback, and rolls back when a gate or a policy says no — recording
everything in a hash-chained event log that is the system of record rather than a commentary
on it.

That framing is what makes the governance real: an exit gate is a subprocess exit code and a
parsed surefire report, not a boolean an agent asserted about itself. The reasoning is in
[ADR 0001](docs/decisions/0001-url-shortener-is-the-orchestrators-target-codebase.md).

## Requirements

- **JDK 21 or newer.** The build targets Java 21 (`maven.compiler.release=21`) and is
  developed on JDK 25; anything in that range works. See
  [ADR 0004](docs/decisions/0004-target-java-21-while-building-on-jdk-25.md).
- **Maven 3.9+.** There is no wrapper; use the system Maven.
- **No Docker, no database, no API key.** Everything runs in-process. This is deliberate —
  a reviewer should be able to clone and run without setup.

## Quick start

```bash
mvn -q -B -ntp verify                          # build and test everything
mvn -q -pl url-shortener spring-boot:run       # shortener on :8081
mvn -q -pl orchestrator-app spring-boot:run    # orchestrator on :8080
```

## Repository layout

```
orchestrator-core/     Domain, DAG, gates, reducer, event log, policy, metrics.
                       Framework-free by build-enforced invariant.
orchestrator-agents/   AgentRuntime adapters, git-backed run workspace, process gate.
orchestrator-app/      REST control surface, persistence, Mermaid dashboard, sdlc CLI.
url-shortener/         The service under orchestration.

docs/decisions/        Architecture decision records (MADR), including rejected options.
docs/specs/            One spec per feature, written before implementation.
docs/scenarios/        Scenario writeups citing real run IDs.

.claude/               Agentic development configuration — see below.
scenarios/             Executable workflow definitions (greenfield, brownfield, ambiguous).
.work/                 Runtime state: run workspaces, event store, logs. Gitignored.
```

## Agentic development configuration

This repository is built with Claude Code, and its configuration is part of the submission
rather than incidental. It is worth separating what is a documented product feature from
what is our own convention:

**Documented Claude Code features used here**

| Path | What it does |
|---|---|
| `CLAUDE.md` | Project context loaded every session. Kept under 200 lines on purpose — a bloated one gets ignored. |
| `.claude/rules/*.md` | Path-gated instructions. `java.md` and `testing.md` load only when a matching file is touched, which is the documented way to keep `CLAUDE.md` small. |
| `.claude/skills/*/SKILL.md` | Reusable procedures (`/verify`, `/adr`, `/spec`, `/run-scenario`). Skills, not the legacy `commands/`, since commands were merged into skills. |
| `.claude/agents/*.md` | Subagents: an adversarial `code-reviewer` and a `test-writer`. |
| `.claude/settings.json` | Committed permissions and hooks. A `PostToolUse` hook compiles the edited module and **exits 2** so a compile error lands back in the model's context for self-correction. |

**Our own conventions** — `docs/decisions/` follows [MADR](https://adr.github.io/madr/), and
`docs/specs/` generalises the documented "write a spec, then execute it in a fresh session"
pattern to one file per feature.

The hook deserves a note, because it is the same argument the orchestrator makes. Hooks are
the only Claude Code mechanism that is *enforced* rather than advisory; `CLAUDE.md` is
context the model may weigh against other things. That is exactly the relationship the
orchestrator's exit gates have with its stage agents — the agent proposes, the deterministic
gate disposes.

Two limitations worth stating, both documented behaviour:

- Subagent edits and file changes made through Bash are **outside** Claude Code's
  checkpointing, so `/rewind` will not undo them. This repository relies on frequent small
  git commits instead.
- `.claude/settings.json` is committed for shared enforcement; `.claude/settings.local.json`
  is gitignored for personal overrides.

## Build status

| Milestone | State |
|---|---|
| M0 — build foundation, agentic config, canonical JSON + hash chain | **done** |
| M1 — shortener core (deliberately without a cache) | pending |
| M2 — analytics, reliability, observability | pending |
| M3 — engine: reducer + hash-chained event log | pending |
| M4 — workspace, process control, policy, governed runtime | pending |
| M5 — REST, persistence, CLI, dashboard, cassettes | pending |
| M6 — three scenario runs + metrics benchmark | pending |
| M7 — architecture overview, scenario writeups, engineering summary | pending |

Full plan, including the scope cut order and the risks being carried, is tracked against
these milestones.

## Assignment mapping

| Requirement | Where |
|---|---|
| §4.1 Requirement understanding | Ambiguous scenario: ambiguity register + clarification checkpoint |
| §4.2 Task decomposition | `PlanDelta` — the decomposition stage emits its own downstream subgraph at runtime |
| §4.3 Codebase reasoning (brownfield) | Brownfield scenario over the real shortener call path |
| §4.4 Workflow orchestration | `orchestrator-core` — the engine, gates, governance, metrics |
| §4.5 Engineering output | `url-shortener`, OpenAPI schema, test suites, these docs |
| §4.6 Validation and risk control | Policy engine, exit gates, `docs/decisions/`, limitations sections |
| §4.7 Controlled autonomy | `GovernedAgentRuntime`, `AutonomyLevel`, approval checkpoints |
| §4.8 Engineering summary | `docs/ENGINEERING_SUMMARY.md` |
