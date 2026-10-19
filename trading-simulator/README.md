# Java-First Mini Trading Simulator

This project is the first step in your HFT learning path:

1. Learn market microstructure concepts through code.
2. Build a working simulator in Java.
3. Measure and identify hot paths.
4. Replace critical paths with C++ later.

## Run

From the repository root:

```bash
cd trading-simulator
mvn test
mvn -q compile exec:java
```

`mvn test` runs the automated matching-engine tests before the demo.

## What This Simulator Includes

- Multiple symbols, each with an independent order book
- Price-time priority matching engine
- Support for `LIMIT` and `MARKET` orders
- Partial fills across multiple price levels
- Order cancellation and modification with ownership checks
- Position tracking per owner and symbol
- Basic risk manager (`max absolute position`)
- Very simple market-making strategy example

## Learning Map

- Trading basics: `docs/trading-basics.md`
- Architecture flow diagrams: `docs/architecture-flow.md`
- Entry point and executable scenarios: `src/main/java/com/lowlatencylab/sim/Main.java`
- Matching logic: `src/main/java/com/lowlatencylab/sim/engine/MatchingEngine.java`
- Per-symbol order books: `src/main/java/com/lowlatencylab/sim/book/OrderBook.java`
- Automated matching tests: `src/test/java/com/lowlatencylab/sim/engine/MatchingEngineTest.java`

## Current Test Coverage

The automated tests cover:

- Resting limit orders
- Market-order execution
- Price priority and FIFO ordering
- Partial fills
- Cancellation and owner validation
- Order modification
- Symbol isolation
- Risk rejection
- Invalid submissions

## Cancel/Replace Semantics

The simulator treats modification as cancel/replace:

- Only open limit orders may be modified.
- The new quantity means remaining quantity and must be positive.
- Remaining quantity may be reduced, but not increased.
- A price or quantity change removes and reinserts the order, so it loses time priority.
- Filled and cancelled orders cannot be modified.
- A partially filled order may be modified or cancelled; executed quantity is never restored.

## Next Milestones

1. Add latency measurements for submit and match paths.
2. Add replay mode from CSV market events.
3. Expand strategy behavior with inventory-aware quoting.
4. Profile the Java implementation and identify hot paths.
5. Move only measured matching hot paths to C++.
