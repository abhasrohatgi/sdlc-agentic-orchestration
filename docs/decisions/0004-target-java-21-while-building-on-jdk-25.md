# 0004. Target Java 21 while building on JDK 25

- Status: accepted
- Date: 2026-08-05

## Context and problem statement

The only JDK installed on the development machine is OpenJDK 25.0.1. This work is an
interview submission, so it will be built by a reviewer whose toolchain we do not control
and cannot ask about.

Separately, the engine needs structured fan-out with cancellation for parallel stages, and
`StructuredTaskScope` is the tool conceptually designed for exactly that.

## Considered options

1. **`release=25`** — use everything Java 25 offers, including `ScopedValue`.
2. **`release=21`** — target the previous LTS.
3. **`release=25` plus `--enable-preview`** for `StructuredTaskScope`.
4. **maven-toolchains** pinned to a specific JDK.

## Decision

We chose **`release=21`**, with virtual threads plus `CompletableFuture` for concurrency, and
no preview features.

## Consequences

**Good:**

- Builds and runs on any JDK from 21 to 25. A reviewer on the current LTS is not blocked.
- Everything this codebase actually uses is final at 21: records, sealed interfaces,
  exhaustive pattern-matching `switch`, record patterns, text blocks, virtual threads.
- `CompletableFuture.allOf` maps directly onto DAG fan-in, so the join semantics are
  expressed in a stable API.

**Bad:**

- `ScopedValue` (final in 25) is unavailable, so run context cannot be propagated ambiently
  into parallel branches. Context is threaded explicitly through the `Blackboard` instead.
  This is arguably better — explicit provenance is exactly what the decision-lineage
  requirement needs, and `Blackboard.read(key)` recording the access is how provenance
  becomes machine-derived rather than self-reported — but it is more code at each call site.
- We forgo Java 22–25 conveniences (module import declarations, flexible constructor bodies,
  compact source files). No material cost here.
- Cancellation of parallel branches must be implemented explicitly rather than inherited
  from a scope, which is where `ProcessGate` and `CancellationScope` come from.

**Rejected alternatives and why:**

- *`release=25`* — buys `ScopedValue` and little else this project needs, at the price of
  excluding any reviewer not on the newest JDK. Bad trade for a submission.
- *`--enable-preview`* — `StructuredTaskScope` is still preview in Java 25 (JEP 505, *fifth*
  preview), its API changed shape between 21 and 25 (`open()` factories replaced public
  constructors), and further previews are queued for 26 and 27. Shipping a preview API in
  work described as "production-grade" would be indefensible, and preview flags must be
  passed at compile time, test time and runtime — three places to get wrong.
- *maven-toolchains* — on a machine with exactly one JDK it adds a failure mode (the build
  hard-fails if `~/.m2/toolchains.xml` does not list a matching JDK) without adding safety.
  `maven.compiler.release` achieves the same guarantee with no configuration file.

## Related toolchain notes

Two consequences of building on JDK 25 that are not language-level and cost real debugging
time if forgotten:

- **The Mockito javaagent must be wired explicitly.** JDK 25 warns on dynamic agent loading
  (JEP 451) and a future JDK will refuse it. `spring-boot-starter-parent` does not configure
  it, so Surefire's `argLine` passes `-javaagent:${org.mockito:mockito-core:jar}`, and that
  token only resolves because maven-dependency-plugin's `properties` goal runs at
  `initialize`.
- **JaCoCo must be at least 0.8.14.** Java 25 support was experimental in 0.8.13. The agent
  instruments JDK and library classes regardless of our own `release` level, so the newer
  version is required even though our bytecode is major version 65.
