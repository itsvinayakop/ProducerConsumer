package com.producerconsumer;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Orchestrates the lifecycle of one Producer and one Consumer thread.
 *
 * Lifecycle contract (single-use per instance):
 *   IDLE → startTransfer() → RUNNING → stopTransfer() or natural end → STOPPED
 *
 * Design choices:
 * - AtomicBoolean 'started' / 'stopped' with CAS prevent double-start and double-stop.
 * - AtomicBoolean 'producerDone' shared with Consumer signals "no more items will arrive."
 * - AtomicInteger 'enqueuedCount' incremented per successful enqueue for live metrics.
 * - ExecutorService (fixed pool of 2) manages thread lifecycle cleanly.
 */
public class DataTransferManager {

    private static final Logger log = Logger.getLogger(DataTransferManager.class.getName());

    private final SharedQueue sharedQueue;

    // ── Lifecycle guards ────────────────────────────────────────────────────
    /** CAS guard: throws on second call to startTransfer(). */
    private final AtomicBoolean started = new AtomicBoolean(false);
    /** CAS guard: makes stopTransfer() safe to call multiple times. */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    // ── Shared state with Consumer ──────────────────────────────────────────
    /** Flipped to true by the producer wrapper after the last enqueue completes. */
    private final AtomicBoolean producerDone = new AtomicBoolean(false);

    /** Live count of items successfully placed in the queue by the Producer. */
    private final AtomicInteger enqueuedCount = new AtomicInteger(0);

    private volatile ExecutorService executor;
    private volatile List<Item> destinationContainer;

    public DataTransferManager(SharedQueue sharedQueue) {
        this.sharedQueue = sharedQueue;
    }

    /**
     * Submits Consumer and Producer to a fresh thread pool.
     *
     * @throws IllegalStateException if called more than once on this instance.
     *         This manager is single-use; create a new instance for a new run.
     */
    public void startTransfer(List<Item> source, List<Item> destination) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "DataTransferManager is single-use. Create a new instance for a new transfer.");
        }

        producerDone.set(false);
        enqueuedCount.set(0);
        this.destinationContainer = destination;
        this.executor = Executors.newFixedThreadPool(2);

        Consumer consumer = new Consumer(sharedQueue, destination, producerDone);

        // Producer wrapper: counts successful enqueues, then sets producerDone
        // only after run() returns — guaranteeing the flag is true only when
        // all items are in the queue.
        Runnable producerTask = () -> {
            new Producer(source, sharedQueue).run();
            producerDone.set(true);
            log.info("Producer done. Total enqueued: " + enqueuedCount.get());
        };

        // ── Producer lambda also increments enqueuedCount per item ──────────
        // We wrap enqueuedCount tracking directly here so Producer stays clean.
        Runnable countingProducerTask = () -> {
            log.info("Producer thread started. Items to produce: " + source.size());
            for (Item item : source) {
                try {
                    sharedQueue.enqueue(item);
                    enqueuedCount.incrementAndGet();
                    log.fine("Enqueued: " + item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warning("Producer interrupted at item " + item.getId() + ". Exiting early.");
                    return;
                }
            }
            producerDone.set(true);
            log.info("Producer finished. Total enqueued: " + enqueuedCount.get());
        };

        executor.submit(consumer);          // Consumer starts first; ready to drain immediately
        executor.submit(countingProducerTask);

        log.info("DataTransferManager: transfer started.");
    }

    /**
     * Interrupts all running threads. Idempotent — safe to call multiple times.
     * Threads respond via InterruptedException in their next blocking call.
     */
    public void stopTransfer() {
        if (!stopped.compareAndSet(false, true)) {
            log.fine("stopTransfer() already called — ignoring duplicate.");
            return;
        }
        log.info("DataTransferManager: stopping transfer.");
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * Blocks until both threads exit or the timeout elapses.
     *
     * @param timeoutSeconds max wait (use Long.MAX_VALUE for no limit)
     * @return true if both threads exited cleanly within the timeout
     */
    public boolean waitForCompletion(long timeoutSeconds) throws InterruptedException {
        if (executor == null) return true;
        executor.shutdown(); // no-op if shutdownNow() was already called
        boolean finished = executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            log.warning("Timeout waiting for workers. Forcing shutdown.");
            executor.shutdownNow();
        }
        return finished;
    }

    /**
     * Real-time snapshot of transfer progress.
     * All reads are from atomic fields — no locking required.
     */
    public String getQueueStatus() {
        int consumed = (destinationContainer != null) ? destinationContainer.size() : 0;
        return String.format("%s | Enqueued=%d | Consumed=%d | ProducerDone=%b",
                sharedQueue.getQueueStatus(),
                enqueuedCount.get(),
                consumed,
                producerDone.get());
    }

    /**
     * Returns the number of items currently buffered in the queue
     * (produced but not yet consumed).
     *
     * Personal addition: this "in-flight" metric makes it easy to observe
     * back-pressure in real time and is useful for tuning queue capacity.
     */
    public int getInFlightCount() {
        return sharedQueue.size();
    }
}
