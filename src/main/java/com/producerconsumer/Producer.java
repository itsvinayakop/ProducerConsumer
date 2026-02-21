package com.producerconsumer;

import java.util.List;
import java.util.logging.Logger;

/**
 * Reads Items from a source list and pushes them into the SharedQueue.
 *
 * Design choice: enqueue() blocks when the queue is full via ArrayBlockingQueue.put()
 * — no busy-waiting, no sleep() loops.
 * On interrupt, the flag is restored and the method returns early.
 * producerDone is NOT set here — the wrapper in DataTransferManager sets it
 * after run() returns, ensuring atomicity with the last enqueue.
 */
public class Producer implements Runnable {

    private static final Logger log = Logger.getLogger(Producer.class.getName());

    private final List<Item> sourceContainer;
    private final SharedQueue sharedQueue;

    public Producer(List<Item> sourceContainer, SharedQueue sharedQueue) {
        this.sourceContainer = sourceContainer;
        this.sharedQueue = sharedQueue;
    }

    @Override
    public void run() {
        log.info("Producer started. Items to produce: " + sourceContainer.size());

        for (Item item : sourceContainer) {
            try {
                // Blocks if the queue is full; wakes when Consumer removes an item.
                sharedQueue.enqueue(item);
                log.fine("Enqueued: " + item);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restore flag; caller (executor) will retire thread
                log.warning("Producer interrupted at item " + item.getId() + ". Exiting early.");
                return;
            }
        }

        log.info("Producer finished. All " + sourceContainer.size() + " items enqueued.");
    }
}
