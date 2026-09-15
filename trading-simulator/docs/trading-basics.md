# Trading Domain Basics (Beginner)

## 1) Market and Symbol

A market is where participants buy and sell instruments.
A symbol is the instrument identifier, for example `AAPL`.

In this simulator, we trade one symbol: `FOO`.

## 2) Bid, Ask, and Spread

- Bid: Highest current buy price in the order book.
- Ask: Lowest current sell price in the order book.
- Spread: `ask - bid`.

If best bid is 100 and best ask is 101, spread is 1 tick.

## 3) Order Types

- Limit order: "Buy or sell at this price or better."
- Market order: "Execute immediately at the best available prices."

Limit orders provide liquidity (rest in book).
Market orders remove liquidity (consume resting orders).

## 4) Matching Engine

The matching engine is the core exchange logic.
It matches incoming aggressive orders against resting opposite-side orders.

This simulator uses **price-time priority**:

- Better price wins first.
- If same price, older order wins first (FIFO).

## 5) Trade and Fill

When a buy and sell order match, a trade is generated.
An order can be partially filled across multiple counterparties.

## 6) Position and Risk

- Position: Net inventory for a trader.
  - Buy increases position.
  - Sell decreases position.
- Risk limit: Prevents position from exceeding max absolute size.

This simulator rejects orders that would break max position.

## 7) Market Maker vs Taker

- Market maker posts both bid and ask quotes and earns spread.
- Taker sends aggressive orders and pays spread.

The included strategy class is a tiny market-maker style example.

## 8) Where Concepts Map in Code

- Order model: `model/Order.java`
- Order book storage: `book/OrderBook.java`
- Matching + trades: `engine/MatchingEngine.java`
- Risk checks: `risk/RiskManager.java`
- Strategy behavior: `strategy/NaiveMarketMakerStrategy.java`
- Visual flow diagrams: `architecture-flow.md`

## 9) Suggested Learning Sequence

1. Run the simulator and inspect console output.
2. Step through `MatchingEngine.submit()` with debugger.
3. Change order sizes/prices in `Main` and observe trades.
4. Add order cancel support.
5. Add latency timing around `submit()`.

That loop (run -> observe -> modify -> measure) is exactly how HFT systems are learned in practice.
