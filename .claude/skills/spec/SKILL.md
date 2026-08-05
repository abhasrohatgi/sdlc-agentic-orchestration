---
description: Interview the user about a feature, then write a self-contained spec to docs/specs/. Use before implementing anything non-trivial, so implementation runs from a written spec rather than from memory.
argument-hint: [feature description]
allowed-tools: Read, Write, Glob, Grep
---

# Write a feature spec

Feature: `$ARGUMENTS`

This follows the documented explore-plan-implement pattern: interview first, write a spec,
then implement from the spec — ideally in a fresh session with clean context.

## Steps

1. **Interview using the AskUserQuestion tool.** Ask about technical implementation, API
   shape, edge cases, failure modes, and trade-offs. Skip questions whose answer is obvious
   from the codebase — read it first. Dig into the parts the user may not have considered.

2. Keep interviewing until the hard parts are settled, then write
   `docs/specs/NNNN-kebab-case-name.md`.

3. A spec is finished when someone could implement it without asking you anything.

## Required sections

- **Problem** — what is wrong or missing today, and why it matters now.
- **Scope** — what is being built.
- **Out of scope** — stated explicitly. This section prevents more wasted work than any
  other.
- **Interfaces** — the actual files, types and method signatures involved. Name them.
- **Edge cases and failure modes** — including what happens under concurrency and on
  restart, both of which are load-bearing in this project.
- **Verification** — a concrete end-to-end check that proves the feature works. A command
  to run and the observable result to expect, not "add tests".

## Quality bar

Time spent making the spec precise pays back more than time spent supervising the
implementation. Name real files. State what is out of scope. End with a verification step
that someone else could execute.
