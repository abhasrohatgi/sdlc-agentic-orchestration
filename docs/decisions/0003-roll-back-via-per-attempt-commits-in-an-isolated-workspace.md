# 0003. Roll back via per-attempt commits in an isolated run workspace

- Status: accepted
- Date: 2026-08-05

## Context and problem statement

§4.4 requires rollback and safe-stop. Because implementation stages write real files and
exit gates run a real Maven build ([0001](0001-url-shortener-is-the-orchestrators-target-codebase.md)),
rollback has to actually restore a filesystem — and it has to do so while other stages may
be running in parallel.

Two hazards make this harder than it looks:

1. **Git's index and worktree are process-global.** Two concurrent stages running
   `git add -A` contend on `.git/index.lock`, and one stage's `git reset --hard` destroys a
   sibling stage's uncommitted work. That is guaranteed data loss, not a rare interleaving.
2. **A file write plus a subprocess cannot be made atomic with a database commit.** A crash
   after the files are written but before the completion record is durable leaves the system
   unable to tell, on restart, whether the work already happened.

## Considered options

1. **One git worktree per parallel branch, merged at the join.**
2. **Copy the workspace per branch, last writer wins at the join.**
3. **Single shared workspace, with mutation restricted to one stage at a time by a
   scheduling invariant.**

## Decision

We chose **option 3**.

- Stage kinds are classified `Mutating` or read-only. Only implementation-class and
  documentation-class stages mutate; test stages write only to `target/`, which is
  gitignored and therefore freely re-runnable.
- **The scheduler never dispatches two mutating stages concurrently.** This is a scheduling
  invariant rather than a lock, specifically so that it can be asserted in a test
  (`observedMaxConcurrentMutating == 1`) instead of trusted.
- Read-only stages remain genuinely parallel. Each records the base commit it ran against
  and is treated as stale by the same input-fingerprint rule that drives re-planning, so one
  mechanism covers two problems.
- The workspace is bootstrapped with `git archive HEAD | tar -x`, which brings the whole
  multi-module tree including the parent pom — necessary for `mvn -pl url-shortener -am test`
  to work at all — respects gitignore, and records the outer HEAD sha as the run's
  `baseRevision` for free provenance.
- Mutating stages write to a per-attempt staging directory, then apply, `git add -A`,
  `git commit -m "stage=<id> attempt=<attemptId>"`. A git ref update is an atomic rename.
- Rollback is `git reset --hard <preStageSha>` **followed by `git clean -fdx -e target`**.
  `reset --hard` alone does not remove untracked files, so a failed attempt that *added*
  files would leave them behind and any "restores byte-for-byte" test would pass while the
  property was false.
- Crash recovery **asks the workspace, not the database**: `git log --grep=<attemptId>`. A
  commit present means the side effect completed, so a completion event is synthesised.
  Absent means reset, discard the staging directory, and re-run. Attempt IDs are
  `UUIDv5(runId, stageId, attemptNumber)` so replay recomputes the same identifier.

Rollback is modelled as one audited compound action covering the workspace revision,
retracted artifacts and revoked approvals — not merely a file operation. Blackboard artifacts
are versioned append-only, so retraction writes a tombstone rather than deleting. Failed
attempts' staging directories and Maven logs are preserved: **rollback must never destroy
evidence.**

## Consequences

**Good:** zero git index contention, real parallelism for read-only stages, genuine
filesystem rollback, and a crash-recovery story that is testable with `kill -9` rather than
hoped for. Roughly fifty lines of mechanism.

**Bad:** implementation stages are serialised, so a workflow with several independent code
changes cannot apply them concurrently. For the scale of these scenarios the cost is
negligible, but it is a real ceiling. Also, `.work/` contains nested git repositories inside
the project repository; it is gitignored, but it will confuse anyone running git commands
from within it.

**Rejected alternatives and why:**

- *Worktree per branch with a merge step* — the appealing option, and wrong here. Real merge
  conflicts between agent-authored changes need a conflict-resolution agent to be anything
  other than a hard failure, which is roughly a week of work and produces a demo that fails
  messily rather than instructively. The parallelism it buys is parallelism across *mutating*
  stages, which these scenarios do not need.
- *Copy per branch, last writer wins* — silently loses work. A system whose entire argument
  is about governed, auditable change cannot have a data-loss path in its rollback design.
