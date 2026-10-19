# low-latency-trading-lab

Projects for HFT and low-latency systems learning.

## Current Project

- `trading-simulator/`: Java-first mini trading simulator with domain-learning docs

Start here:

```bash
cd trading-simulator
mvn test
mvn -q compile exec:java
```

The simulator currently supports multiple symbols, price-time matching, order
cancellation and modification, position tracking, risk checks, and automated
JUnit tests.
