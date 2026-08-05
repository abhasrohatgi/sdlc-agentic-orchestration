---
description: Capture an architecture decision as a MADR record in docs/decisions/. Use when a choice closes off an alternative someone would otherwise reasonably try.
argument-hint: [short decision title]
allowed-tools: Read, Write, Glob, Bash(ls *)
---

# Write an architecture decision record

Record the decision described in `$ARGUMENTS`.

## When this is worth doing

An ADR earns its place when a future reader would otherwise re-litigate the decision or,
worse, quietly undo it. If the choice is obvious or reversible in five minutes, skip it.

The most valuable part of an ADR here is the **rejected** alternatives — this project is
assessed partly on "clarity and defensibility of decisions", and showing the option you
declined with the reason you declined it is stronger evidence than describing what you built.

## Steps

1. `ls docs/decisions/` and take the next free four-digit number.
2. Write `docs/decisions/NNNN-kebab-case-title.md` using the MADR structure below.
3. Link it from any code comment that would otherwise repeat the reasoning.

## Format (MADR)

```markdown
# NNNN. <Title stated as the decision, not the topic>

- Status: accepted
- Date: YYYY-MM-DD

## Context and problem statement

What forced a choice. Include the constraint that made this non-obvious.

## Considered options

1. <option> — what it would have meant
2. <option>
3. <option>

## Decision

We chose <option>, because <reason tied to the constraint above>.

## Consequences

**Good:** what this buys.

**Bad:** what it costs, honestly. An ADR with no downsides listed is not credible.

**Rejected alternatives and why:** the specific reason each other option loses. Be concrete
— "more complex" is not a reason; "requires a conflict-resolution agent and produces a demo
that fails messily" is.
```

## Conventions

Title states the decision ("Use file-backed H2 for run state"), not the topic ("Database").
Status is `accepted`, `superseded by NNNN`, or `rejected`. Never edit an accepted ADR to
change its decision — supersede it with a new one, so the reasoning trail survives.
