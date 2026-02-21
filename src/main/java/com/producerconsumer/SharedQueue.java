package com.producerconsumer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe bounded queue backed by ArrayBlockingQueue.
 */
public class SharedQueue {

    private final int capacity;
    private final BlockingQueue<Item> queue;

    public SharedQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /** Blocks until space is available. */
    public void enqueue(Item item) throws InterruptedException {
        queue.put(item);
    }

    /** Blocks until an item is available. */
    public Item dequeue() throws InterruptedException {
        return queue.take();
    }

    /** Timed dequeue used for graceful shutdown. Returns null on timeout. */
    public Item dequeueWithTimeout(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public boolean isFull() {
        return queue.remainingCapacity() == 0;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public String getQueueStatus() {
        int size = queue.size();
        int percent = (int) ((size * 100.0) / capacity);
        return String.format("Queue[%d/%d (%d%% full)]", size, capacity, percent);
    }
}