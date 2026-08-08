package com.schwab.agenticsdlc.cli;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Non-blocking terminal spinner for AgenticSDLC CLI.
 *
 * <p>Runs a background daemon thread that renders a Braille-pattern spinner
 * updating at 80ms intervals. Supports live message updates mid-spin.
 *
 * <p>Usage:
 * <pre>{@code
 *   CliSpinner spinner = new CliSpinner();
 *   spinner.start("Generating file manifest...");
 *   // ... do async LLM work ...
 *   spinner.update("Parsing 27 files...");
 *   // ... more work ...
 *   spinner.success("Manifest received — 27 files declared.");
 *   // OR: spinner.fail("Manifest generation failed.");
 * }</pre>
 */
public class CliSpinner {

    private static final String[] FRAMES = {
            "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"
    };

    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private final AtomicInteger frameIndex = new AtomicInteger(0);
    private final AtomicReference<String> message = new AtomicReference<>("");
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CliSpinner() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cli-spinner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the spinner with an initial message.
     *
     * @param initialMessage message to display alongside the spinner
     */
    public synchronized void start(String initialMessage) {
        if (running.get()) stop();
        message.set(initialMessage);
        running.set(true);
        frameIndex.set(0);

        task = scheduler.scheduleAtFixedRate(() -> {
            if (!running.get()) return;
            String frame = FRAMES[frameIndex.getAndIncrement() % FRAMES.length];
            String msg = message.get();
            System.out.print("\r  " + CliRenderer.BRIGHT_CYAN + CliRenderer.BOLD + frame + CliRenderer.RESET
                    + "  " + msg + "    ");
            System.out.flush();
        }, 0, 80, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the spinner message without stopping.
     *
     * @param newMessage updated message to display
     */
    public void update(String newMessage) {
        message.set(newMessage);
    }

    /**
     * Stops the spinner and prints a success line.
     *
     * @param successMessage final message shown with a ✔ icon
     */
    public synchronized void success(String successMessage) {
        stop();
        System.out.println("\r  " + CliRenderer.BRIGHT_GREEN + CliRenderer.BOLD
                + CliRenderer.ICON_SUCCESS + CliRenderer.RESET
                + "  " + successMessage + "    ");
    }

    /**
     * Stops the spinner and prints a failure line.
     *
     * @param failMessage final message shown with a ✘ icon
     */
    public synchronized void fail(String failMessage) {
        stop();
        System.out.println("\r  " + CliRenderer.BRIGHT_RED + CliRenderer.BOLD
                + CliRenderer.ICON_FAIL + CliRenderer.RESET
                + "  " + failMessage + "    ");
    }

    /**
     * Silently stops the spinner and clears the line.
     */
    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        // Clear the spinner line
        System.out.print("\r" + " ".repeat(80) + "\r");
        System.out.flush();
    }

    /**
     * Shuts down the background scheduler. Call once at application exit.
     */
    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }
}
