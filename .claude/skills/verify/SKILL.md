---
description: Run the full build and report evidence of what passed and failed. Use before claiming any change is done, and whenever asked to check whether the project is green.
allowed-tools: Bash(mvn *), Read, Grep, Glob
---

# Verify

Produce evidence, not an assertion that things look fine.

## Steps

1. Run the full reactor build:

   ```bash
   mvn -B -ntp verify
   ```

2. If it fails, find the first genuine failure rather than the last line of output. Maven
   reports the failing module in the reactor summary; the root cause is usually well above
   the stack trace.

3. Report, always in this shape:
   - total tests run, failures, errors, skipped — per module
   - the exact name of each failing test and the assertion that failed
   - whether the enforcer rule (`core-stays-framework-free`) passed
   - what you changed, if anything, and whether the failure was pre-existing

4. Never report success without having actually run the command in this session. If the
   build was not run, say so.

## Scoped alternatives

Engine only, fast, no Spring context:

```bash
mvn -B -ntp -pl orchestrator-core test
```

One module and its upstream dependencies:

```bash
mvn -B -ntp -pl <module> -am test
```

## Expected baseline

A clean tree builds green. If `orchestrator-core` fails at `validate` with a banned
dependency message, something added Spring or a JSON library to it — that rule is
deliberate, so remove the dependency rather than the rule.
