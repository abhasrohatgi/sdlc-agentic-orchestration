package com.schwab.agenticsdlc.cli;

/**
 * ANSI terminal rendering utilities for the AgenticSDLC CLI.
 * Provides colours, box drawing, banners, stage trackers, and structured output.
 * Pure Java stdlib — no third-party dependencies.
 */
public final class CliRenderer {

    private CliRenderer() {}

    // ─── ANSI Escape Codes ────────────────────────────────────────────────────
    public static final String RESET   = "\u001B[0m";
    public static final String BOLD    = "\u001B[1m";
    public static final String DIM     = "\u001B[2m";
    public static final String ITALIC  = "\u001B[3m";

    // Foreground colours
    public static final String BLACK   = "\u001B[30m";
    public static final String RED     = "\u001B[31m";
    public static final String GREEN   = "\u001B[32m";
    public static final String YELLOW  = "\u001B[33m";
    public static final String BLUE    = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN    = "\u001B[36m";
    public static final String WHITE   = "\u001B[37m";

    // Bright foreground
    public static final String BRIGHT_BLACK   = "\u001B[90m";
    public static final String BRIGHT_RED     = "\u001B[91m";
    public static final String BRIGHT_GREEN   = "\u001B[92m";
    public static final String BRIGHT_YELLOW  = "\u001B[93m";
    public static final String BRIGHT_BLUE    = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN    = "\u001B[96m";
    public static final String BRIGHT_WHITE   = "\u001B[97m";

    // ─── Icons ────────────────────────────────────────────────────────────────
    public static final String ICON_SUCCESS  = "✔";
    public static final String ICON_FAIL     = "✘";
    public static final String ICON_ACTIVE   = "⚡";
    public static final String ICON_PENDING  = "○";
    public static final String ICON_ARROW    = "→";
    public static final String ICON_BULLET   = "•";
    public static final String ICON_WARN     = "⚠";
    public static final String ICON_INFO     = "ℹ";
    public static final String ICON_LOCK     = "🔒";
    public static final String ICON_ROCKET   = "🚀";
    public static final String ICON_GEAR     = "⚙";

    // ─── Banner ───────────────────────────────────────────────────────────────
    /**
     * Prints the full AgenticSDLC startup banner.
     */
    public static void printBanner() {
        System.out.println();
        System.out.println(CYAN + BOLD + "  ╔══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + BOLD + "  ║" + RESET + "                                                              " + CYAN + BOLD + "║" + RESET);
        System.out.println(CYAN + BOLD + "  ║" + RESET + "   " + BRIGHT_CYAN + BOLD + "⚡  AgenticSDLC" + RESET + "  " + DIM + "—" + RESET + "  " + WHITE + BOLD + "Autonomous Code Orchestration Engine" + RESET + "   " + CYAN + BOLD + "║" + RESET);
        System.out.println(CYAN + BOLD + "  ║" + RESET + "                                                              " + CYAN + BOLD + "║" + RESET);
        System.out.println(CYAN + BOLD + "  ║" + RESET + "   " + BRIGHT_BLACK + "Java 21  •  Spring Boot 3  •  Gemini LLM  •  Multi-Agent" + RESET + "   " + CYAN + BOLD + "║" + RESET);
        System.out.println(CYAN + BOLD + "  ║" + RESET + "                                                              " + CYAN + BOLD + "║" + RESET);
        System.out.println(CYAN + BOLD + "  ╚══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    // ─── Section Headers ─────────────────────────────────────────────────────
    public static void printHeader(String title) {
        System.out.println();
        System.out.println(CYAN + BOLD + "  ┌─ " + title + " " + "─".repeat(Math.max(0, 58 - title.length())) + RESET);
    }

    public static void printSubHeader(String title) {
        System.out.println(BRIGHT_BLACK + "  ├── " + WHITE + title + RESET);
    }

    public static void printDivider() {
        System.out.println(BRIGHT_BLACK + "  " + "─".repeat(62) + RESET);
    }

    // ─── Status Lines ─────────────────────────────────────────────────────────
    public static void success(String message) {
        System.out.println(BRIGHT_GREEN + "  " + ICON_SUCCESS + "  " + RESET + message);
    }

    public static void error(String message) {
        System.out.println(BRIGHT_RED + "  " + ICON_FAIL + "  " + RESET + message);
    }

    public static void warn(String message) {
        System.out.println(BRIGHT_YELLOW + "  " + ICON_WARN + "  " + RESET + message);
    }

    public static void info(String message) {
        System.out.println(BRIGHT_BLACK + "  " + ICON_INFO + "  " + RESET + message);
    }

    public static void bullet(String key, String value) {
        System.out.println("    " + BRIGHT_BLACK + key + RESET + "  " + WHITE + value + RESET);
    }

    // ─── Stage Tracker ────────────────────────────────────────────────────────
    public enum StageStatus { PENDING, ACTIVE, DONE, FAILED }

    /**
     * Prints a pipeline stage tracker row.
     *
     * @param stages  stage labels in order
     * @param current index of the currently active stage (0-based)
     */
    public static void printStageTracker(String[] stages, int current) {
        StringBuilder sb = new StringBuilder("  ");
        for (int i = 0; i < stages.length; i++) {
            if (i > 0) {
                sb.append(BRIGHT_BLACK).append("  ").append(ICON_ARROW).append("  ").append(RESET);
            }
            if (i < current) {
                // Completed
                sb.append(BRIGHT_GREEN).append(BOLD).append(ICON_SUCCESS).append(" ").append(stages[i]).append(RESET);
            } else if (i == current) {
                // Active
                sb.append(BRIGHT_CYAN).append(BOLD).append(ICON_ACTIVE).append(" ").append(stages[i]).append(RESET);
            } else {
                // Pending
                sb.append(BRIGHT_BLACK).append(ICON_PENDING).append(" ").append(stages[i]).append(RESET);
            }
        }
        System.out.println(sb);
    }

    // ─── Progress Bar ─────────────────────────────────────────────────────────
    /**
     * Prints an in-place progress bar. Call with {@code \r} behaviour via the returned string.
     *
     * @param label    label shown before the bar
     * @param done     number of completed items
     * @param total    total items
     * @param threads  active thread count (0 to omit)
     */
    public static void printProgressBar(String label, int done, int total, int threads) {
        int width = 20;
        int filled = total == 0 ? 0 : (done * width) / total;
        String bar = CYAN + "█".repeat(filled) + BRIGHT_BLACK + "░".repeat(width - filled) + RESET;
        String threadInfo = threads > 0 ? BRIGHT_BLACK + "  " + ICON_BULLET + "  " + threads + " threads" + RESET : "";
        System.out.print("\r  " + BRIGHT_BLACK + label + RESET + "  " + bar + "  " + WHITE + done + "/" + total + RESET + threadInfo + "    ");
        if (done == total) System.out.println(); // newline when complete
    }

    // ─── Project Info Box ─────────────────────────────────────────────────────
    public static void printProjectBox(String name, String id, String stack, String category,
                                        String scenario, String path) {
        System.out.println();
        System.out.println(CYAN + "  ┌─ Active Workspace " + "─".repeat(43) + RESET);
        System.out.printf(CYAN + "  │" + RESET + "  %-14s" + WHITE + BOLD + "%s" + RESET + "%n", "Project", name);
        System.out.printf(CYAN + "  │" + RESET + "  %-14s" + BRIGHT_BLACK + "%s" + RESET + "%n", "ID", id);
        System.out.printf(CYAN + "  │" + RESET + "  %-14s" + BRIGHT_CYAN + "%s" + RESET + "%n", "Stack", stack);
        System.out.printf(CYAN + "  │" + RESET + "  %-14s" + "%s" + "%n", "Category", category);
        System.out.printf(CYAN + "  │" + RESET + "  %-14s" + BRIGHT_YELLOW + "%s" + RESET + "%n", "Scenario", scenario);
        System.out.printf(CYAN + "  │" + RESET + "  %-14s" + BRIGHT_BLACK + "%s" + RESET + "%n", "Location", path);
        System.out.println(CYAN + "  └" + "─".repeat(61) + RESET);
        System.out.println();
    }

    // ─── Plan Approval Box ────────────────────────────────────────────────────
    /**
     * Prints a formatted plan summary box for human governance approval.
     *
     * @param planContent  full plan.md content (first 2000 chars shown if long)
     * @param planPath     file path to full plan
     * @param projectName  project name
     * @param fileCount    estimated file count from manifest (or -1 if unknown)
     */
    public static void printPlanApprovalBox(String planContent, String planPath,
                                             String projectName, int fileCount) {
        System.out.println();
        System.out.println(BRIGHT_YELLOW + BOLD + "  ╔══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(BRIGHT_YELLOW + BOLD + "  ║" + RESET + BOLD + "     " + ICON_LOCK + "  HUMAN GOVERNANCE CHECKPOINT — PLAN REVIEW" + "               " + BRIGHT_YELLOW + BOLD + "║" + RESET);
        System.out.println(BRIGHT_YELLOW + BOLD + "  ╠══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.printf(BRIGHT_YELLOW + BOLD + "  ║" + RESET + "  %-14s" + WHITE + BOLD + "%-44s" + BRIGHT_YELLOW + BOLD + "║" + RESET + "%n", "Project:", truncate(projectName, 44));
        System.out.printf(BRIGHT_YELLOW + BOLD + "  ║" + RESET + "  %-14s" + BRIGHT_BLACK + "%-44s" + BRIGHT_YELLOW + BOLD + "║" + RESET + "%n", "Plan File:", truncate(planPath, 44));
        if (fileCount > 0) {
            System.out.printf(BRIGHT_YELLOW + BOLD + "  ║" + RESET + "  %-14s" + BRIGHT_CYAN + "%-44s" + BRIGHT_YELLOW + BOLD + "║" + RESET + "%n", "Est. Files:", fileCount + " source files to be generated");
        }
        System.out.println(BRIGHT_YELLOW + BOLD + "  ╠══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.println(BRIGHT_YELLOW + BOLD + "  ║" + RESET + "  " + BRIGHT_BLACK + "Architectural Plan Preview:" + RESET + " " + ".".repeat(33) + BRIGHT_YELLOW + BOLD + "║" + RESET);
        System.out.println(BRIGHT_YELLOW + BOLD + "  ╚══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();

        // Print plan content with left indent & beautified markdown rendering
        String display = planContent.length() > 3000
                ? planContent.substring(0, 3000) + "\n\n[...truncated — open " + planPath + " for full plan]\n"
                : planContent;
        System.out.println(formatMarkdown(display));
        System.out.println();
    }

    /**
     * Renders Markdown text with vibrant ANSI terminal styling (headers, code blocks, bullet points, callouts).
     */
    public static String formatMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        StringBuilder sb = new StringBuilder();
        String[] lines = markdown.split("\n");
        boolean inCodeBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                if (inCodeBlock) {
                    String lang = trimmed.length() > 3 ? trimmed.substring(3).trim() : "";
                    sb.append("    ").append(CYAN).append("┌─ ").append(BOLD).append(lang.toUpperCase()).append(" ─────────────────────────────────").append(RESET).append("\n");
                } else {
                    sb.append("    ").append(CYAN).append("└──────────────────────────────────────").append(RESET).append("\n");
                }
                continue;
            }

            if (inCodeBlock) {
                sb.append("    │ ").append(BRIGHT_WHITE).append(line).append(RESET).append("\n");
                continue;
            }

            if (trimmed.startsWith("# ")) {
                String heading = trimmed.substring(2).trim();
                sb.append("\n    ").append(BRIGHT_CYAN).append(BOLD).append("█ ").append(heading.toUpperCase()).append(RESET).append("\n");
            } else if (trimmed.startsWith("## ")) {
                String heading = trimmed.substring(3).trim();
                sb.append("\n    ").append(CYAN).append(BOLD).append("▶ ").append(heading).append(RESET).append("\n");
            } else if (trimmed.startsWith("### ")) {
                String heading = trimmed.substring(4).trim();
                sb.append("    ").append(WHITE).append(BOLD).append("• ").append(heading).append(RESET).append("\n");
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                String item = trimmed.substring(2).trim();
                sb.append("      ").append(BRIGHT_GREEN).append(ICON_BULLET).append(" ").append(RESET).append(WHITE).append(item).append(RESET).append("\n");
            } else if (trimmed.startsWith("> [!NOTE]") || trimmed.startsWith("> [!IMPORTANT]") || trimmed.startsWith("> [!WARNING]")) {
                sb.append("    ").append(BRIGHT_YELLOW).append("💡 ").append(BOLD).append(trimmed.substring(1).trim()).append(RESET).append("\n");
            } else if (trimmed.startsWith("> ")) {
                sb.append("    ").append(BRIGHT_BLACK).append("│ ").append(ITALIC).append(trimmed.substring(2)).append(RESET).append("\n");
            } else {
                String formatted = line.replaceAll("\\*\\*(.*?)\\*\\*", BRIGHT_WHITE + BOLD + "$1" + RESET);
                sb.append("    ").append(BRIGHT_BLACK).append(formatted).append(RESET).append("\n");
            }
        }
        return sb.toString();
    }

    // ─── Unified ANSI Logger Adapter ─────────────────────────────────────────
    public static void logEvent(String level, String agentTag, String message) {
        String icon = switch (level.toUpperCase()) {
            case "WARN", "WARNING" -> BRIGHT_YELLOW + ICON_WARN + RESET;
            case "ERROR", "FAILURE" -> BRIGHT_RED + ICON_FAIL + RESET;
            case "SUCCESS" -> BRIGHT_GREEN + ICON_SUCCESS + RESET;
            default -> BRIGHT_CYAN + ICON_INFO + RESET;
        };
        String tag = agentTag != null && !agentTag.isBlank()
                ? BRIGHT_CYAN + BOLD + "[" + agentTag + "]" + RESET + " "
                : "";
        System.out.println("  " + icon + "  " + tag + WHITE + message + RESET);
    }

    // ─── Menu Rendering ───────────────────────────────────────────────────────
    public static void printMenuTitle(String title, String subtitle) {
        System.out.println();
        System.out.println(CYAN + BOLD + "  ╔══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.printf(CYAN + BOLD + "  ║" + RESET + "  %-60s" + CYAN + BOLD + "║" + RESET + "%n", BOLD + title + RESET);
        if (subtitle != null && !subtitle.isBlank()) {
            System.out.printf(CYAN + BOLD + "  ║" + RESET + "  " + BRIGHT_BLACK + "%-58s" + RESET + CYAN + BOLD + "║" + RESET + "%n", subtitle);
        }
        System.out.println(CYAN + BOLD + "  ╚══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    public static void printMenuItem(int number, String label, String description) {
        String num = BRIGHT_CYAN + BOLD + "  [" + number + "]" + RESET;
        String lbl = WHITE + BOLD + "  " + label + RESET;
        String desc = description != null ? BRIGHT_BLACK + "       " + description + RESET : "";
        System.out.println(num + lbl);
        if (!desc.isBlank()) System.out.println(desc);
    }

    public static void printPrompt(String prompt) {
        System.out.print("\n  " + BRIGHT_CYAN + BOLD + "❯ " + RESET + WHITE + prompt + RESET + " ");
        System.out.flush();
    }

    public static void printSuccess(String title, String detail) {
        System.out.println();
        System.out.println(BRIGHT_GREEN + BOLD + "  " + ICON_SUCCESS + "  " + title + RESET);
        if (detail != null && !detail.isBlank()) {
            System.out.println(BRIGHT_BLACK + "     " + detail + RESET);
        }
    }

    // ─── Pipeline Complete Banner ─────────────────────────────────────────────
    public static void printPipelineSuccess(String projectName, String workspacePath) {
        System.out.println();
        System.out.println(BRIGHT_GREEN + BOLD + "  ╔══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(BRIGHT_GREEN + BOLD + "  ║" + RESET + BOLD + "   " + ICON_ROCKET + "  PIPELINE COMPLETED SUCCESSFULLY" + "                          " + BRIGHT_GREEN + BOLD + "║" + RESET);
        System.out.println(BRIGHT_GREEN + BOLD + "  ╠══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.printf(BRIGHT_GREEN + BOLD + "  ║" + RESET + "  %-14s" + WHITE + BOLD + "%-44s" + BRIGHT_GREEN + BOLD + "║" + RESET + "%n", "Project:", truncate(projectName, 44));
        System.out.printf(BRIGHT_GREEN + BOLD + "  ║" + RESET + "  %-14s" + BRIGHT_BLACK + "%-44s" + BRIGHT_GREEN + BOLD + "║" + RESET + "%n", "Output:", truncate(workspacePath, 44));
        System.out.println(BRIGHT_GREEN + BOLD + "  ╚══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    public static void printPipelineFailed(String reason) {
        System.out.println();
        System.out.println(BRIGHT_RED + BOLD + "  ╔══════════════════════════════════════════════════════════════╗" + RESET);
        System.out.println(BRIGHT_RED + BOLD + "  ║" + RESET + BOLD + "   " + ICON_FAIL + "  PIPELINE FAILED" + "                                          " + BRIGHT_RED + BOLD + "║" + RESET);
        System.out.println(BRIGHT_RED + BOLD + "  ╠══════════════════════════════════════════════════════════════╣" + RESET);
        System.out.printf(BRIGHT_RED + BOLD + "  ║" + RESET + "  " + BRIGHT_BLACK + "%-58s" + BRIGHT_RED + BOLD + "║" + RESET + "%n", truncate(reason, 58));
        System.out.println(BRIGHT_RED + BOLD + "  ╚══════════════════════════════════════════════════════════════╝" + RESET);
        System.out.println();
    }

    // ─── LLM Health Check ─────────────────────────────────────────────────────
    public static void printLlmCheckPassed() {
        System.out.println(BRIGHT_GREEN + "  " + ICON_SUCCESS + "  " + RESET + "LLM connection verified" + BRIGHT_BLACK + " — Gemini API reachable" + RESET);
    }

    public static void printLlmCheckFailed() {
        System.out.println(BRIGHT_RED + "  " + ICON_FAIL + "  " + RESET + BOLD + "LLM connection FAILED" + RESET);
        System.out.println(BRIGHT_BLACK + "     Set: " + YELLOW + "export LLM_API_KEY=\"your-api-key\"" + RESET);
    }

    // ─── Agent Status Badges & Diff Preview ──────────────────────────────────
    public static void printAgentBadge(String agentName, String statusMessage) {
        System.out.println("  " + BRIGHT_CYAN + BOLD + "🤖 [" + agentName + "]" + RESET + "  " + YELLOW + BOLD + ICON_ACTIVE + " " + statusMessage + RESET);
    }

    public static void printDiffPreview(String filePath, String diffText) {
        System.out.println();
        System.out.println(CYAN + "  ┌─ Diff Patch Preview: " + WHITE + BOLD + filePath + CYAN + " " + "─".repeat(Math.max(0, 38 - filePath.length())) + RESET);
        if (diffText != null) {
            String[] lines = diffText.split("\n");
            int limit = Math.min(lines.length, 30);
            for (int i = 0; i < limit; i++) {
                String line = lines[i];
                if (line.startsWith("+")) {
                    System.out.println("  " + BRIGHT_GREEN + line + RESET);
                } else if (line.startsWith("-")) {
                    System.out.println("  " + BRIGHT_RED + line + RESET);
                } else if (line.startsWith("@")) {
                    System.out.println("  " + BRIGHT_CYAN + line + RESET);
                } else {
                    System.out.println("  " + BRIGHT_BLACK + line + RESET);
                }
            }
            if (lines.length > limit) {
                System.out.println("  " + DIM + "... [" + (lines.length - limit) + " lines truncated]" + RESET);
            }
        }
        System.out.println(CYAN + "  └" + "─".repeat(61) + RESET);
        System.out.println();
    }

    // ─── Util ─────────────────────────────────────────────────────────────────
    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
