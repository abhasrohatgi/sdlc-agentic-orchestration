# 0001. Treat the URL shortener as the orchestrator's target codebase

- Status: accepted
- Date: 2026-08-05

## Context and problem statement

The assignment asks for two things that read as separate deliverables: a URL shortener with
core APIs, analytics and reliability features (§2), and an agentic SDLC orchestration layer
described in a single dense paragraph flagged as the *Critical Differentiator* (§4.4).

A third requirement forces the issue. §4.3 asks for **brownfield codebase reasoning** —
identifying impacted modules, services, APIs and data flows. That is not demonstrable
without a real codebase to reason about. Equally, §4.4 requires exit gates, bounded retries,
rollback and MTTR, none of which mean anything unless some stage can actually fail at
something real.

So the question is not "which deliverable matters more" but "what is the relationship
between them".

## Considered options

1. **Two independent deliverables.** Build the shortener; build an orchestrator that runs an
   SDLC workflow over a fictional or stubbed target.
2. **Orchestrator only.** Treat the shortener as illustrative and focus everything on §4.4.
3. **The shortener is the orchestrator's target codebase.** The orchestrator plans against,
   modifies, tests and gates changes to the shortener living in the same reactor.

## Decision

We chose **option 3**.

The orchestrator's implementation stages write real files into a sandboxed copy of the
shortener, and its exit gates run the real Maven build and parse the real surefire reports.

## Consequences

**Good:**

- §4.3 becomes genuine. The brownfield scenario reasons about an actual call path
  (`RedirectController → LinkService → LinkRepository`) with actual invalidation obligations.
- Gates verify something. "Exit gate: tests pass" is a subprocess exit code and a parsed XML
  report, not a boolean an agent asserted about itself.
- Rollback, retry, MTTR and end-to-end latency become measured quantities rather than
  described ones.
- The failure in the brownfield scenario can be *emergent* rather than planted — see
  [0005](0005-serve-agent-output-from-recorded-cassettes.md) and the scenario writeup.

**Bad:**

- The two codebases are coupled in the repository layout, and a reviewer must understand the
  relationship before the structure makes sense. Mitigated by stating it in the first
  paragraph of `CLAUDE.md` and the README.
- Shortener design choices are now partly driven by what makes a good orchestration target.
  Most visibly, the shortener ships **without a cache** so that the brownfield scenario has
  something real to add. That looks like an omission until you know why.
- Build times inside a gate include a real Maven run, which makes runs slower than a
  simulated orchestrator would be.

**Rejected alternatives and why:**

- *Two independent deliverables* — a stubbed target means gates cannot compile or test
  anything, so retry, rollback and MTTR all degrade to simulation. That is precisely the
  "theatre" failure mode this project needs to avoid, and an evaluator would find it in
  minutes.
- *Orchestrator only* — §2 names analytics and reliability as in-scope, and §6 rewards
  "realism/quality of outputs" and "core engineering principles: modular, testable,
  reliable, secure, scalable". An evaluator working from a checklist would mark those absent.
