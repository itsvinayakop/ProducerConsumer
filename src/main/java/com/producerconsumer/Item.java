package com.producerconsumer;

import java.time.Instant;

/**
 * Represents a single unit of work transferred between Producer and Consumer.
 * Immutable by design — thread-safe without synchronization.
 */
public final class Item {

    private final int id;
    private final String data;
    private final Instant timestamp;

    public Item(int id, String data) {
        this.id = id;
        this.data = data;
        this.timestamp = Instant.now();
    }

    public int getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("Item{id=%d, data='%s', timestamp=%s}", id, data, timestamp);
    }
}
