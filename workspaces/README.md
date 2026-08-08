# AgenticSDLC Workspaces Catalog

This directory serves as the isolated workspace root for projects created, managed, or refactored by the **AgenticSDLC** multi-agent orchestration framework.

---

## 📁 Directory Structure & File Artifacts

For every project managed by **AgenticSDLC**, a dedicated subdirectory is created under `workspaces/<project_id>/` containing:

```text
workspaces/<project_id>/
├── .ai-plan/
│   └── plan.md                      # Architectural plan generated in Stage 1
├── AGENTS.md                        # Project-level agent behavioral directives & quality gates
├── project_config.json              # Project configuration (stack, scenario type, paths)
├── execution_trajectory.json        # Step-by-step real-time agent trajectory audit log
├── pom.xml                          # Primary build descriptor
└── src/                             # Synthesized Java source code & test suites
```

---

## 🔍 Key Project Artifacts

- **`execution_trajectory.json`**: Captures every discrete step executed by the agents (Stage 1 Plan Generation, Stage 2 Human Approval, Stage 3 Code Waves, Stage 4 Self-Healing attempts, stack traces, diagnostic reasoning, and file patches).
- **`project_config.json`**: Stores project metadata, language stack (JAVA), and scenario classification (`GREENFIELD` / `BROWNFIELD`).
