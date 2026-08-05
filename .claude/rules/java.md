---
paths:
  - "**/src/main/java/**/*.java"
  - "**/src/test/java/**/*.java"
---

# Java conventions for this repository

Loaded only when a Java file is read or edited, so that `CLAUDE.md` stays small.

## Language level

Target is Java 21 (`maven.compiler.release=21`) even though the build JDK is 25. Available
and encouraged: records, sealed interfaces, exhaustive pattern-matching `switch`, record
patterns, text blocks, virtual threads. **Not available:** `ScopedValue`, module import
declarations, flexible constructor bodies, `StructuredTaskScope`.

## Modelling

Prefer a sealed interface with record implementations over an enum plus a payload bag when
variants carry different data. Commands, events and gate results are all modelled this way,
which makes `switch` exhaustiveness the compiler's problem rather than a runtime default
branch.

Make illegal states unrepresentable in the type rather than guarded by a check. A value
object with a validating canonical constructor beats a `String` plus a validator called at
three of the four call sites.

## Nullability

Do not return `null` from anything public. Use `Optional` for genuinely absent values and
an empty collection for empty ones. Validate constructor arguments with
`Objects.requireNonNull` and a message naming the parameter.

## Concurrency

Virtual threads for I/O-bound work. **Never** run a subprocess on a virtual thread —
`Process.waitFor()` blocks in native code and pins the carrier. Subprocesses go on the
dedicated bounded platform-thread pool.

Inject `Clock`; never call `Instant.now()` directly. Every duration and timestamp in the
engine must be controllable from a test, or the MTTR and latency assertions become flaky.

## Comments

Comment the *why*, not the *what*. A comment earns its place when it records a decision, a
rejected alternative, or a non-obvious constraint. Do not narrate code that already reads
clearly.

## Error handling

Fail loudly and early on programmer error (`IllegalArgumentException`, `IllegalStateException`
with a message that names the offending value). Reserve checked exceptions and result types
for conditions a caller can genuinely act on. Never swallow an exception to keep a demo
running — a silent fallback is exactly the failure mode this project argues against.
