package com.producerconsumer;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Pulls Items from the SharedQueue and writes them to a destination list.
 *
 * Shutdown design: uses dequeueWithTimeout() instead of blocking dequeue()
 * so the thread can periodically check the exit condition without hanging
 * when the producer is done and the queue is empty.
 *
 * Exit condition ordering (important):
 *   1. Attempt timed poll.
 *   2. If an item arrived → process it, loop again.
 *   3. Only if poll returned null → check producerDone + isEmpty().
 * This ordering guarantees no item is missed even if producerDone flips
 * true in the instant between a successful enqueue and this check.
 */
public class Consumer implements Runnable {

    private static final Logger log = Logger.getLogger(Consumer.class.getName());

    /** How long to wait for an item before re-checking the exit condition. */
    private static final long POLL_TIMEOUT_MS = 500;

    private final SharedQueue sharedQueue;
    private final List<Item> destinationContainer;

    /**
     * Set to true by DataTransferManager after the producer's last enqueue.
     * Consumer exits only when this is true AND the queue is empty.
     */
    private final AtomicBoolean producerDone;

    public Consumer(SharedQueue sharedQueue, List<Item> destinationContainer,
                    AtomicBoolean producerDone) {
        this.sharedQueue = sharedQueue;
        this.destinationContainer = destinationContainer;
        this.producerDone = producerDone;
    }

    @Override
    public void run() {
        log.info("Consumer started.");

        while (true) {
            try {
                // Step 1: Try to get an item within the timeout window.
                Item item = sharedQueue.dequeueWithTimeout(POLL_TIMEOUT_MS);

                if (item != null) {
                    // Step 2: Item received — add and loop for more.
                    // destinationContainer is a Collections.synchronizedList; add() is thread-safe.
                    destinationContainer.add(item);
                    log.fine("Consumed: " + item);
                } else {
                    // Step 3: Poll timed out. ONLY NOW check whether we should stop.
                    // Checking after a failed poll (not before) prevents the race where
                    // producerDone becomes true a split-second before the final enqueue lands.
                    if (producerDone.get() && sharedQueue.isEmpty()) {
                        log.info("Queue drained and producer done. Consumer exiting.");
                        break;
                    }
                    // Not done yet — loop and keep waiting.
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore flag for JVM visibility
                log.warning("Consumer interrupted. Exiting.");
                break;
            }
        }

        log.info("Consumer finished. Items consumed: " + destinationContainer.size());
    }
}
