#!/usr/bin/env bash
#
# PostToolUse hook: compile the module containing a just-edited Java file.
#
# Why this exists: the assignment's whole thesis is that agents should execute under
# deterministic gates rather than self-reported success. This hook is that idea applied to
# the repository itself - the same relationship the orchestrator's exit gates have with its
# stage agents. Claude Code documents hooks as the only *enforced* configuration mechanism;
# CLAUDE.md is advisory context by comparison.
#
# The load-bearing detail is `exit 2`. On PostToolUse, exit 2 cannot block the tool (it has
# already run) but it does feed stderr back to Claude as an error to fix. That turns a
# compile failure into self-correction instead of a surprise three edits later.
#
# Scoped to a single module rather than the whole reactor to keep the feedback loop short.

set -uo pipefail

INPUT=$(cat)

# jq is not guaranteed to be present; degrade to a no-op rather than failing every edit.
if ! command -v jq >/dev/null 2>&1; then
  exit 0
fi

FILE=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')

case "$FILE" in
  *.java) ;;
  *) exit 0 ;;   # not Java, nothing to do
esac

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-.}"
cd "$PROJECT_DIR" || exit 0

# Map the edited file back to its Maven module by finding the nearest enclosing pom.xml.
REL="${FILE#"$PROJECT_DIR"/}"
MODULE="${REL%%/*}"
if [ ! -f "$MODULE/pom.xml" ]; then
  exit 0   # edited file is not inside a module (e.g. a stray script); skip
fi

# -o (offline) keeps this fast and prevents a network stall on every keystroke-sized edit.
# -am builds required upstream modules, since core changes break agents/app.
BUILD_OUT=$(mvn -o -q -B -ntp -pl "$MODULE" -am -DskipTests compile 2>&1)
BUILD_RC=$?

if [ $BUILD_RC -ne 0 ]; then
  {
    echo "Compilation failed in module '$MODULE' after editing $REL."
    echo
    echo "$BUILD_OUT" | tail -n 60
  } >&2
  exit 2
fi

exit 0
