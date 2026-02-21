# Producer-Consumer System in Java

A thread-safe, production-quality implementation of the classic **Producer-Consumer** concurrency pattern, built with Java's `java.util.concurrent` primitives — no manual `wait/notify` required.

---

## 🧠 Overview

The Producer-Consumer pattern decouples **data generation** from **data processing** using a shared, bounded buffer. This implementation demonstrates:

- Zero-busy-wait blocking via `ArrayBlockingQueue`
- Graceful shutdown without `System.exit()`  
- Race-condition-free exit via timed polling + `AtomicBoolean`
- Real-time progress monitoring (`getQueueStatus`, `getInFlightCount`)
- Configurable logging verbosity via a JVM flag

---

## 🏗️ Architecture

```
┌──────────────┐      enqueue()      ┌─────────────┐      dequeue()      ┌──────────────┐
│   Producer   │ ──────────────────► │ SharedQueue │ ──────────────────► │   Consumer   │
│  (Runnable)  │   blocks if FULL    │  (capacity) │   blocks if EMPTY   │  (Runnable)  │
└──────────────┘                     └─────────────┘                     └──────────────┘
        │                                                                        │
        └──────────────────── DataTransferManager ──────────────────────────────┘
                              (orchestrates lifecycle)
```

### Class Responsibilities

| Class | Role |
|---|---|
| `Item` | Immutable data unit — `id`, `data`, `timestamp`. Thread-safe by design. |
| `SharedQueue` | Bounded queue wrapper around `ArrayBlockingQueue`. Exposes typed blocking `enqueue`/`dequeue`. |
| `Producer` | Reads from a source `List<Item>` and enqueues items. Backs off automatically when the queue is full. |
| `Consumer` | Drains the queue into a destination `List<Item>`. Polls with a timeout to check the exit condition safely. |
| `DataTransferManager` | Single-use orchestrator. Manages thread lifecycle, shutdown, and live metrics. |
| `Main` | Demo entry point. Prints live status every 300 ms while the transfer runs. |

---

## 🔑 Key Design Decisions

### 1. Back-pressure via `ArrayBlockingQueue.put()`
`Producer.enqueue()` calls `queue.put()` which **blocks automatically** when the queue is full. No `Thread.sleep()`, no busy-spinning.

### 2. Race-condition-free shutdown
The Consumer uses `dequeueWithTimeout()` instead of a blocking take. Exit is triggered only when **both** conditions are true — *after* a failed poll:
```
producerDone == true  AND  queue.isEmpty()
```
This ordering guarantees no item is missed if `producerDone` flips true in the same instant as the last enqueue.

### 3. Single-use `DataTransferManager`
Guards via `AtomicBoolean.compareAndSet()` prevent double-start and double-stop. Create a new instance for each transfer run.

### 4. `getInFlightCount()` for observability
Returns the number of items buffered in the queue at any moment — useful for diagnosing back-pressure and tuning `QUEUE_CAPACITY`.

---

## 📁 Project Structure

```
ProducerConsumer/
├── pom.xml
└── src/
    └── main/
        └── java/
            └── com/producerconsumer/
                ├── Item.java
                ├── SharedQueue.java
                ├── Producer.java
                ├── Consumer.java
                ├── DataTransferManager.java
                └── Main.java
```

---

## 🚀 Getting Started

### Prerequisites
- Java 11+
- Maven 3.6+

### Build

```bash
mvn clean package
```

This produces `target/producer-consumer.jar`.

### Run

```bash
java -jar target/producer-consumer.jar
```

**Enable verbose (per-item) logging:**

```bash
java -Dverbose.logging=true -jar target/producer-consumer.jar
```

### Example Output

```
=== Producer-Consumer Demo ===
Items: 25  |  Queue capacity: 10

[STATUS] Queue[5/10 (50% full)] | Enqueued=8 | Consumed=3 | ProducerDone=false | In-Flight=5
[STATUS] Queue[2/10 (20% full)] | Enqueued=16 | Consumed=14 | ProducerDone=false | In-Flight=2
[STATUS] Queue[0/10 (0% full)] | Enqueued=25 | Consumed=25 | ProducerDone=true | In-Flight=0

=== Transfer Complete ===
Items produced  : 25
Items consumed  : 25
Clean exit      : true
✓ All items transferred successfully.
```

---

## ⚙️ Configuration

All tunable constants live at the top of `Main.java`:

| Constant | Default | Description |
|---|---|---|
| `TOTAL_ITEMS` | `25` | Number of items to transfer |
| `QUEUE_CAPACITY` | `10` | Max items the shared buffer can hold |
| `STATUS_INTERVAL_MS` | `300` | How often the main thread prints status |

---

## 🧵 Concurrency Model

```
Thread Pool (size=2, managed by DataTransferManager)
  ├── consumer-thread  → runs Consumer.run()
  └── producer-thread  → runs counting producer lambda → sets producerDone=true
Main Thread
  └── polls destination.size() every 300ms → calls waitForCompletion() → prints report
```

Shutdown hook registered via `Runtime.getRuntime().addShutdownHook()` ensures `stopTransfer()` is called on JVM signals (e.g., Ctrl+C).

---

## 🛠️ Tech Stack

- **Language**: Java 11
- **Build**: Apache Maven
- **Concurrency**: `java.util.concurrent` (`ArrayBlockingQueue`, `ExecutorService`, `AtomicBoolean`, `AtomicInteger`)
- **Logging**: `java.util.logging` (configurable via `-Dverbose.logging=true`)

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).