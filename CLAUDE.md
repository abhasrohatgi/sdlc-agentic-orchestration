# Agentic SDLC Orchestrator + URL Shortener

Two codebases in one Maven reactor. Understanding the relationship between them is the
single thing most likely to prevent wasted work:

- `url-shortener` is a real service **and** it is the **target codebase** the orchestrator
  plans against, modifies, tests and gates. When you change it, ask whether you are
  changing a deliverable or changing the orchestrator's input.
- `orchestrator-*` is the SDLC engine that operates on it.

## Build and run

```bash
mvn -q -B -ntp verify          # everything: compile, test, enforcer, JaCoCo report
mvn -B -ntp -pl orchestrator-core test    # engine tests only, fast, no Spring context
mvn -q -pl url-shortener spring-boot:run  # shortener on :8081
mvn -q -pl orchestrator-app spring-boot:run   # orchestrator on :8080
```

There is no `mvnw` wrapper. Use the system Maven (3.9.11).

## Module boundaries

| Module | Contains | Depends on |
|---|---|---|
| `orchestrator-core` | Domain, DAG, gates, reducer, event log, policy, metrics | **nothing** |
| `orchestrator-agents` | AgentRuntime adapters, git workspace, process gate | core |
| `orchestrator-app` | REST, persistence, dashboard, CLI | core, agents |
| `url-shortener` | The service under orchestration | nothing |

## Invariants

**`orchestrator-core` must not depend on Spring or on a JSON library.** This is enforced by
maven-enforcer at `validate`, not merely documented — adding such a dependency fails the
build. The reason is that the entire engine has to stay unit-testable with a fake `Clock`
and no application context.

**Never hash the output of a general-purpose serializer.** Audit hashes go through
`CanonicalJson` in `orchestrator-core`. Jackson's output is not a stable contract across
versions, and a drift there would silently invalidate every stored hash in the event log —
invisible until someone tried to verify an old run.

**No gate may read the scenario name.** A gate's inputs are the workspace, the process exit
code, and the surefire XML. If a gate ever branches on which scenario is running, the whole
demo becomes theatre and the assignment is failed. This is the highest-stakes rule here.

**The reducer is pure.** `Reducer.apply(state, command)` performs no I/O, reads no clock,
and starts no threads. Effects are returned as data and dispatched by the caller. Keep it
that way; it is what makes the DAG semantics testable without concurrency.

**Only one mutating stage runs at a time.** This is a scheduling invariant, not a lock, so
that it can be asserted in a test. Read-only stages stay genuinely parallel.

**`url-shortener` ships without a cache, on purpose.** Adding one is the brownfield
scenario's job. Do not "helpfully" add caching to the redirect path.

## Non-obvious gotchas

- **Build JDK is 25; `maven.compiler.release` is 21.** Do not use Java 22–25 APIs
  (`ScopedValue`, module import declarations, flexible constructor bodies). Pass run context
  explicitly through the `Blackboard`.
- **`StructuredTaskScope` is still preview in Java 25.** Use virtual threads +
  `CompletableFuture`. This build does not enable preview features.
- **Subprocess stages must not run on virtual threads.** `Process.waitFor()` blocks in
  native code and pins the carrier. Use the dedicated bounded platform-thread pool.
- **XML comments cannot contain a double hyphen.** Writing `--enable-preview` inside a pom
  comment makes the pom unparseable.
- **The Mockito javaagent path comes from `dependency:properties`.** The
  `${org.mockito:mockito-core:jar}` token in Surefire's `argLine` only resolves because
  maven-dependency-plugin's `properties` goal runs at `initialize`. Removing it makes every
  forked test JVM die with "Error opening zip file or JAR manifest missing".
- **Spring Boot 4 breaking changes that bite here:** `@MockBean`/`@SpyBean` are gone — use
  `@MockitoBean`. Jackson is now 3 (`tools.jackson.*`), so Jackson 2 imports will not
  resolve.
- **Docker is not installed.** Testcontainers is unavailable. Integration tests use
  in-process H2.
- **`.work/` is gitignored and contains nested git repositories** (one per run workspace).
  Do not `git add` it, and do not run git commands from inside it expecting the outer repo.
- **The orchestrator's H2 is file-backed** (`.work/orchestrator`). Switching it to `mem:`
  would make the restart-resume behaviour silently fake.

## Testing

Prefer a failing test that reproduces the problem before the fix. Engine tests use a fake
`Clock` and assert behaviour, not implementation. Coverage is reported but never gated.

`url-shortener` carries two behavioural tests that look ordinary and are not:
`DeleteRemovesLinkImmediatelyTest` and `ExpiredLinkIsNotServedTest`. They are the trap that
a naive cache implementation springs during the brownfield scenario. Do not weaken them.

## Documentation conventions

- `docs/decisions/` — MADR architecture decision records, including rejected alternatives.
- `docs/specs/` — one spec per feature, written before implementation.
- `docs/scenarios/` — writeups citing real run IDs and committed audit output.

Record a decision as an ADR when it closes off an alternative someone would otherwise
reasonably try.
