# 0005. Serve agent output from recorded cassettes by default

- Status: accepted
- Date: 2026-08-05

## Context and problem statement

The orchestrator's stage agents produce requirements, designs, code, tests and
documentation. How they execute determines whether a reviewer believes the system is
agentic at all — and it is the easiest place in this project to accidentally build theatre.

Constraints:

- No `ANTHROPIC_API_KEY` is available on this machine.
- A reviewer must be able to run everything with no key, no cost, and no network.
- The orchestrator's own test suite must be deterministic, or the reliability metrics and
  gate assertions become flaky.
- A reviewer will reasonably ask whether the "agents" are just a template engine wearing a
  state machine.

## Considered options

1. **Live LLM call on every stage.**
2. **Deterministic template agents only**, with documentation arguing that governance rather
   than the model call is the deliverable.
3. **Record real LLM responses once; replay them offline by default.**

## Decision

We chose **option 3**, the record/replay ("cassette") pattern familiar from HTTP testing
libraries.

`AgentRuntime` is a port with four adapters:

| Adapter | Role |
|---|---|
| `ReplayAgentRuntime` | **Default.** Serves recorded responses keyed by input hash. |
| `ClaudeCliAgentRuntime` | Recording path. Shells out to `claude -p --output-format json`. |
| `ClaudeApiAgentRuntime` | Anthropic SDK, if a key is ever available. |
| `TemplateAgentRuntime` | **Not** the default — the fallback rung in the failure ladder. |

All four sit behind `GovernedAgentRuntime`, a decorator applying policy pre-checks, wall
clock, step and token budgets, output-schema validation and audit emission. Autonomy
boundaries live in the decorator so they cannot be bypassed by swapping the backend.

The recording path deserves note: **no API key is required.** The Claude Code CLI is
installed (2.1.222) and its non-interactive mode produces real model output through existing
auth. That is what makes option 3 reachable at all.

Two rules make or break this:

- **A cassette miss fails loudly** — "no recording for input hash X; run with `--profile
  record`". It must never fall back silently to a template. A silent fallback is precisely
  the theatre this decision exists to avoid.
- **Recorded latency is replayed.** Without it, two "parallel" stages both complete in
  three milliseconds, the audit timeline shows no genuine overlap, and the end-to-end latency
  metric is meaningless.

Prompts are committed alongside cassettes. Prompts are engineering artifacts, and showing
them is evidence.

## Consequences

**Good:** artifacts are real model output; runs are free, offline, reproducible and fast
enough that a twenty-run benchmark for the reliability metrics is practical; the test suite
is deterministic; and the framing is a standard, respected testing practice rather than a
mock.

**Bad:**

- Cassettes must be re-recorded when a prompt changes, and a stale cassette set is a real
  maintenance burden.
- `claude -p`'s output shape is not a stable public contract the way an API is. It is used
  only at record time and behind an adapter, so a change breaks recording, never replay.
- Replay cannot demonstrate the model *adapting* to a genuinely novel input. This is stated
  as a limitation rather than papered over.
- If recording cannot be performed before submission, the shipped artifacts come from
  `TemplateAgentRuntime` and must be **labelled as such** in the scenario writeups. Claiming
  otherwise would be worse than the limitation itself.

**Rejected alternatives and why:**

- *Live call per stage* — non-deterministic, needs a key the reviewer may not have, costs
  tokens per run, makes the twenty-run metrics benchmark impractical, and turns a live demo
  into a coin flip.
- *Templates only* — the honest objection ("is this actually agentic?") has no good answer,
  and pointing at an unexercised live adapter behind a flag is not one, because the reviewer
  will not run it.
