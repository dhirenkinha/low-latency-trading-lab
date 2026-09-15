# Java-First Mini Trading Simulator

This project is the first step in your HFT learning path:

1. Learn market microstructure concepts through code.
2. Build a working simulator in Java.
3. Measure and identify hot paths.
4. Replace critical paths with C++ later.

## Run

```bash
cd trading-simulator
mvn -q compile exec:java
```

## What This Simulator Includes

- One-symbol order book with bids and asks
- Price-time priority matching engine
- Support for `LIMIT` and `MARKET` orders
- Basic risk manager (`max absolute position`)
- Very simple market-making strategy example

## Learning Map

- Trading basics: `docs/trading-basics.md`
- Architecture flow diagrams: `docs/architecture-flow.md`
- Entry point: `src/main/java/com/lowlatencylab/sim/Main.java`
- Matching logic: `src/main/java/com/lowlatencylab/sim/engine/MatchingEngine.java`
- Order book: `src/main/java/com/lowlatencylab/sim/book/OrderBook.java`

## Next Milestones

1. Add cancel/replace order support.
2. Add latency measurements for submit and match path.
3. Add replay mode from CSV market events.
4. Move matching hot path to C++.
