package com.schwab.agenticsdlc.cli;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.InputStream;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive Arrow-Key Terminal Menu Navigator.
 * Supports interactive keyboard arrow key navigation (↑ / ↓ / ENTER) in interactive TTY terminals,
 * with standard fallback for non-TTY environments and automated test pipelines.
 */
public class CliMenuNavigator {

    private final Scanner scanner;

    public CliMenuNavigator() {
        this(new Scanner(System.in));
    }

    public CliMenuNavigator(Scanner scanner) {
        this.scanner = scanner;
    }

    public static class MenuItem {
        private final String label;
        private final String description;

        public MenuItem(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Prompts the user to select an option using keyboard arrow keys (or fallback numeric input).
     *
     * @param title    Menu header title
     * @param items    List of menu items
     * @param defaultIdx Default selected index (0-based)
     * @return Selected index (0-based)
     */
    public int selectOption(String title, List<MenuItem> items, int defaultIdx) {
        if (items == null || items.isEmpty()) {
            return -1;
        }

        // Check if we are in an interactive TTY
        if (System.console() != null && isTtyAvailable()) {
            return runArrowKeyMenu(title, items, defaultIdx);
        } else {
            return runFallbackNumericMenu(title, items, defaultIdx);
        }
    }

    /**
     * Prompts for a yes/no confirmation with interactive arrow key selection.
     */
    public boolean confirmAction(String promptText, boolean defaultYes) {
        List<MenuItem> options = List.of(
                new MenuItem("Yes", "Proceed with action"),
                new MenuItem("No", "Cancel or skip")
        );
        int defaultIndex = defaultYes ? 0 : 1;
        int selected = selectOption(promptText, options, defaultIndex);
        return selected == 0;
    }

    private boolean isTtyAvailable() {
        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            boolean isAnsi = terminal.getType() != null && !terminal.getType().equals("dumb");
            terminal.close();
            return isAnsi;
        } catch (Exception e) {
            return false;
        }
    }

    private int runArrowKeyMenu(String title, List<MenuItem> items, int initialSelected) {
        int selected = Math.max(0, Math.min(initialSelected, items.size() - 1));
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            terminal.enterRawMode();
            InputStream in = terminal.input();

            boolean done = false;
            while (!done) {
                // Clear and render menu
                clearMenuScreen(items.size() + 4);
                CliRenderer.printHeader(title);
                System.out.println(CliRenderer.BRIGHT_BLACK + "  (Use ↑/↓ arrow keys to navigate, ENTER to select)" + CliRenderer.RESET);
                System.out.println();

                for (int i = 0; i < items.size(); i++) {
                    MenuItem item = items.get(i);
                    if (i == selected) {
                        System.out.print(CliRenderer.BRIGHT_CYAN + CliRenderer.BOLD + "  ❯ " + CliRenderer.RESET);
                        System.out.println(CliRenderer.WHITE + CliRenderer.BOLD + item.getLabel() + CliRenderer.RESET);
                        if (item.getDescription() != null && !item.getDescription().isBlank()) {
                            System.out.println("      " + CliRenderer.BRIGHT_CYAN + item.getDescription() + CliRenderer.RESET);
                        }
                    } else {
                        System.out.print(CliRenderer.BRIGHT_BLACK + "    " + CliRenderer.RESET);
                        System.out.println(CliRenderer.BRIGHT_BLACK + item.getLabel() + CliRenderer.RESET);
                        if (item.getDescription() != null && !item.getDescription().isBlank()) {
                            System.out.println("      " + CliRenderer.DIM + item.getDescription() + CliRenderer.RESET);
                        }
                    }
                }

                int c = in.read();
                if (c == 13 || c == 10) { // ENTER
                    done = true;
                } else if (c == 27) { // ESC sequence for arrow keys
                    int c2 = in.read();
                    if (c2 == 91) {
                        int c3 = in.read();
                        if (c3 == 65) { // UP arrow
                            selected = (selected - 1 + items.size()) % items.size();
                        } else if (c3 == 66) { // DOWN arrow
                            selected = (selected + 1) % items.size();
                        }
                    }
                } else if (c == 'k' || c == 'K') { // Vim UP
                    selected = (selected - 1 + items.size()) % items.size();
                } else if (c == 'j' || c == 'J') { // Vim DOWN
                    selected = (selected + 1) % items.size();
                }
            }
            System.out.println();
            return selected;
        } catch (Exception e) {
            return runFallbackNumericMenu(title, items, initialSelected);
        }
    }

    private int runFallbackNumericMenu(String title, List<MenuItem> items, int defaultIdx) {
        CliRenderer.printHeader(title);
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            CliRenderer.printMenuItem(i + 1, item.getLabel(), item.getDescription());
        }
        CliRenderer.printPrompt("Enter selection [1-" + items.size() + ", default " + (defaultIdx + 1) + "]:");

        if (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isBlank()) {
                return defaultIdx;
            }
            try {
                int val = Integer.parseInt(line) - 1;
                if (val >= 0 && val < items.size()) {
                    return val;
                }
            } catch (NumberFormatException ignored) {}
        }
        return defaultIdx;
    }

    private void clearMenuScreen(int lineCount) {
        // ANSI escape sequence to move cursor up and clear lines
        System.out.print("\u001B[" + lineCount + "A\u001B[0J");
        System.out.flush();
    }
}
