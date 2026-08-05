---
paths:
  - "**/src/test/java/**/*.java"
---

# Testing conventions

## Stack

JUnit Jupiter 6, AssertJ, Mockito — all versions inherited from the Spring Boot BOM. Do not
pin them independently; that is how the Byte Buddy / Java 25 class-file incompatibility gets
reintroduced.

Spring Boot 4 removed `@MockBean` and `@SpyBean`. Use **`@MockitoBean`** and
**`@MockitoSpyBean`**.

## What a test should assert

Assert observable behaviour, not implementation. `assertThat(response.status()).isEqualTo(302)`
is a test; `verify(service).lookup(any())` usually is not — it passes after a refactor that
broke the feature.

Use `@DisplayName` to state the property being protected, especially where the reason is not
obvious from the method name. Several tests in this repo exist to protect a specific
invariant, and a future reader needs to know that before deleting one.

## Engine tests

`orchestrator-core` tests must run with **no Spring context and no real threads**. Use a
fixed `Clock` (`Clock.fixed(...)`) or a controllable fake. The reducer is pure, so its whole
behaviour — join barriers, retry ladders, fingerprint invalidation, approval binding — is
testable synchronously. Only tests that specifically assert parallel overlap should start
threads.

## Contract tests

Where a port has more than one adapter (notably `LinkRepository`), write **one** abstract
contract test class and subclass it per adapter. Two implementations passing the same suite
is what proves the abstraction holds; two independently written suites prove nothing.

## Integration tests

Docker is not available, so Testcontainers is not an option. Use `@SpringBootTest` with
MockMvc against in-process H2. Name them `*IT` and let Failsafe run them.

## Do not weaken these tests

`DeleteRemovesLinkImmediatelyTest` and `ExpiredLinkIsNotServedTest` in `url-shortener` look
like ordinary behavioural tests. They are also the trap that a naive cache implementation
springs during the brownfield orchestration scenario. If one of them starts failing after a
caching change, the correct response is to fix the cache invalidation — never to relax the
test.
