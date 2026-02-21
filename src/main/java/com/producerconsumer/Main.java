package com.producerconsumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Demo entry point.
 *
 * Kept intentionally thin — all business logic lives in the domain classes.
 * run() is extracted from main() so it is testable without JVM exit side-effects.
 *
 * Pass -Dverbose.logging=true at the JVM level to trace every enqueue/dequeue.
 */
public class Main {

    private static final int TOTAL_ITEMS        = 25;
    private static final int QUEUE_CAPACITY     = 10;
    private static final int STATUS_INTERVAL_MS = 300;

    public static void main(String[] args) throws InterruptedException {
        configureLogging();
        boolean success = run();
        if (!success) {
            // Print clearly and exit naturally — no System.exit(), no stack trace.
            System.err.println("[ERROR] Transfer did not complete successfully.");
            // For CI/scripts that check the exit code, uncomment the line below:
            // System.exit(1);
        }
    }

    /**
     * Executes the full transfer and returns true if all items arrived safely.
     * Extracted from main() so this logic can be tested without JVM exit side-effects.
     */
    static boolean run() throws InterruptedException {
        // ── 1. Source data ─────────────────────────────────────────────────
        List<Item> source = new ArrayList<>(TOTAL_ITEMS);
        for (int i = 1; i <= TOTAL_ITEMS; i++) {
            source.add(new Item(i, "payload-" + i));
        }

        // ── 2. Infrastructure ──────────────────────────────────────────────
        SharedQueue sharedQueue = new SharedQueue(QUEUE_CAPACITY);
        // synchronizedList makes Consumer's add() thread-safe without extra locking.
        List<Item> destination = Collections.synchronizedList(new ArrayList<>());
        DataTransferManager manager = new DataTransferManager(sharedQueue);

        // Shutdown hook: ensures threads are interrupted on Ctrl+C.
        // stopTransfer() is idempotent — safe even if main() also calls it.
        Runtime.getRuntime().addShutdownHook(
                new Thread(manager::stopTransfer, "shutdown-hook"));

        // ── 3. Start ───────────────────────────────────────────────────────
        System.out.println("\n=== Producer-Consumer Demo ===");
        System.out.printf("Items: %d  |  Queue capacity: %d%n%n", TOTAL_ITEMS, QUEUE_CAPACITY);

        manager.startTransfer(source, destination);

        // ── 4. Live status (main thread polls while workers run) ───────────
        while (destination.size() < TOTAL_ITEMS) {
            System.out.printf("[STATUS] %s | In-Flight=%d%n",
                    manager.getQueueStatus(),
                    manager.getInFlightCount());
            Thread.sleep(STATUS_INTERVAL_MS);
        }

        // ── 5. Join threads cleanly ────────────────────────────────────────
        boolean cleanExit = manager.waitForCompletion(10);

        // ── 6. Report ──────────────────────────────────────────────────────
        System.out.println();
        System.out.println("=== Transfer Complete ===");
        System.out.printf("Items produced  : %d%n", source.size());
        System.out.printf("Items consumed  : %d%n", destination.size());
        System.out.printf("Clean exit      : %b%n", cleanExit);

        boolean allTransferred = destination.size() == source.size();
        System.out.println(allTransferred
                ? "✓ All items transferred successfully."
                : "✗ Item count mismatch — some items may have been lost.");

        return allTransferred && cleanExit;
    }

    /**
     * Configures java.util.logging.
     * Default level: INFO. Set -Dverbose.logging=true for FINE (trace every item).
     */
    private static void configureLogging() {
        // Honour a JVM system property so verbosity is configurable without code changes.
        boolean verbose = Boolean.getBoolean("verbose.logging");
        Level level = verbose ? Level.FINE : Level.INFO;

        Logger root = Logger.getLogger("");
        root.setLevel(level);

        // Copy handler array before modifying to avoid iterator-invalidation.
        Handler[] existing = root.getHandlers();
        for (Handler h : existing) {
            root.removeHandler(h);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        handler.setLevel(level);
        root.addHandler(handler);
    }
}
