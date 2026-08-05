# 0002. Run the engine as a single-writer reducer over an event-sourced log

- Status: accepted
- Date: 2026-08-05

## Context and problem statement

The engine must simultaneously satisfy several requirements from §4.4 that pull in different
directions:

- parallel stage execution with synchronization
- stateful execution that survives process restart, including a run paused at an approval
- audit-grade observability and traceability
- reliability metrics computed over the run's history

The obvious shape — a mutable `RunState` row in a database, updated by whichever stage
thread finishes, with an audit log written alongside — has three specific problems:

1. **Concurrent writers.** Two stages completing simultaneously both mutate run state.
   Optimistic locking with `@Version` plus a retry loop is easy to write and subtly easy to
   get wrong, and every future change to the engine has to preserve that correctness.
2. **A single run-level status enum is incoherent under parallelism.** Two stages can await
   approval while a third retries. There is no single value that describes the run.
3. **A side-channel audit log is not audit-grade.** If state lives in one place and the
   audit lives in another, state can change without the audit reflecting it. The hash chain
   then protects a description of history rather than history itself.

## Considered options

1. **Mutable `RunState` row + optimistic locking + separate audit log.**
2. **Actor-per-run with an internal mutable state object**, audit written as a side effect.
3. **Single-writer command loop over an append-only, hash-chained event log**, with
   `RunState` as a fold over that log.

## Decision

We chose **option 3**.

One thread per run consumes commands from a queue. Each command goes through a pure reducer
returning `(newState, events, effects)`. Events are appended in one transaction — that
append is the durability point — then state is replaced and effects are dispatched. Effects
never mutate state; they post commands back onto the queue.

## Consequences

**Good:**

- No locks, no `@Version`, no lost updates, and no optimistic-retry logic to maintain.
- The reducer is a pure function, so the entire DAG semantics — join barriers, retry ladders,
  fingerprint invalidation, approval binding, budget enforcement — is unit-testable with a
  fixed `Clock` and zero threads. Only tests that specifically assert parallel overlap need
  concurrency.
- Restart-resume is replay from sequence zero. No snapshotting machinery.
- The event log *is* the audit log. Tampering with it is rewriting history rather than
  falsifying a description of it, which is what makes the hash chain worth having. See
  [0004](0004-target-java-21-while-building-on-jdk-25.md) for the related decision to hash a
  canonical encoding rather than serializer output.
- Run-level status is derived from per-stage states, so the incoherence in problem (2)
  cannot be expressed.

**Bad:**

- Replaying every event on resume is O(events per run). Fine at hundreds of events per run;
  it would need snapshotting at millions. Documented as a limitation rather than solved.
- One loop per run assumes a single orchestrator instance. Horizontal scaling would need a
  distributed lock or partitioned ownership. Out of scope and stated as such.
- Contributors must respect reducer purity. This is a convention the compiler cannot
  enforce, so it is stated as an invariant in `CLAUDE.md` and checked by the
  `code-reviewer` subagent.
- Effects dispatched after the state write mean a crash between the two re-runs the effect
  on resume. Handled by idempotency-by-construction rather than by trying to make the two
  atomic — see the workspace reconciliation in
  [0003](0003-roll-back-via-per-attempt-commits-in-an-isolated-workspace.md).

**Rejected alternatives and why:**

- *Mutable row + optimistic locking* — solves the write conflict but leaves the audit as a
  side-channel, keeps the incoherent run-level status, and makes the engine untestable
  without a database.
- *Actor with internal mutable state* — fixes concurrency but not testability: the state
  transition logic stays entangled with the actor's I/O, so exercising a retry ladder still
  requires driving a live actor rather than calling a function.
