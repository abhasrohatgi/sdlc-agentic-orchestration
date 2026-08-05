---
description: Execute one of the three SDLC orchestration scenarios (greenfield, brownfield, ambiguous) end to end and summarise the run from its audit log. Use to demo or validate the orchestrator.
argument-hint: [greenfield|brownfield|ambiguous]
disable-model-invocation: true
allowed-tools: Bash(mvn *), Bash(java -jar *), Bash(curl -s http://localhost:*), Read, Grep, Glob
---

# Run an orchestration scenario

Scenario: `$ARGUMENTS`

`disable-model-invocation` is set deliberately: a scenario run writes files, spawns Maven
subprocesses and consumes budget. It should start because a human asked, never because the
work looked ready.

## Steps

1. Package if needed: `mvn -q -B -ntp -DskipTests package`
2. Start the orchestrator if it is not already up: `java -jar orchestrator-app/target/*.jar`
3. Start the run and capture the run ID.
4. Poll status until the run reaches a terminal state or an approval checkpoint.
5. At a checkpoint, **stop and report** — do not approve on the user's behalf. Approval is
   the human's job; that separation is the point of the whole system.

## What to report afterwards

Read it from the audit log and the metrics endpoint, not from the console tail:

- the graph before and after decomposition (node count changes when `PlanDelta` is applied)
- every gate evaluation with its verdict and the input it judged
- retries, with the failure class for each and what feedback the next attempt received
- any rollback: what was reverted, which artifacts were retracted, which approvals revoked
- reliability metrics: success rate, retry and rollback frequency, MTTR, end-to-end latency
- the head hash of the event chain, and whether verification passed

## What a good run looks like

The interesting part is rarely a clean pass. A gate that failed for a real reason, retried
with the failure output as feedback, and then succeeded is stronger evidence that the
governance works than a run where nothing went wrong.

If a run succeeds on the first attempt every time, say so plainly rather than presenting it
as a demonstration of the retry ladder.
