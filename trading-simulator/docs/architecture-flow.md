# Trading Simulator Architecture Flow

This page shows the simulator flow in small diagrams so you can connect the trading concepts to the code.

## 1) High-Level Request Flow

```mermaid
flowchart LR
    A[Trader or Strategy] --> B[Create Order]
    B --> C[Risk Manager]
    C -->|Accepted| D[Matching Engine]
    C -->|Rejected| E[Reject Result]
    D --> F[Order Book]
    D --> G[Trade Events]
    G --> H[Position Updates]
```

Meaning:

- A trader or strategy sends an order.
- Risk checks run before matching.
- If accepted, the matching engine tries to trade it.
- If it does not fully trade, remaining quantity can rest in the order book.
- Any executed trade updates positions.

## 2) Order Book Structure

```mermaid
flowchart TB
    OB[Order Book]
    OB --> BIDS[Bids: buy orders]
    OB --> ASKS[Asks: sell orders]
    BIDS --> BB[Best Bid = highest buy price]
    ASKS --> BA[Best Ask = lowest sell price]
```

Meaning:

- `Bids` are people willing to buy.
- `Asks` are people willing to sell.
- The best bid is the highest buy price.
- The best ask is the lowest sell price.

## 3) Matching Flow For An Incoming Buy

```mermaid
flowchart TD
    A[Incoming Buy Order] --> B{Best ask exists?}
    B -->|No| C[Add to book if limit order]
    B -->|Yes| D{Buy price crosses ask?}
    D -->|No| C
    D -->|Yes| E[Match with oldest order at best ask]
    E --> F{Incoming order fully filled?}
    F -->|No| G{More opposite orders available?}
    G -->|Yes| E
    G -->|No| C
    F -->|Yes| H[Return trades]
    C --> H
```

Meaning:

- A buy order only trades against sell orders.
- It starts at the best ask.
- If the buy order is aggressive enough, it consumes liquidity.
- If it still has quantity left and it is a limit order, it rests in the book.

## 4) Market Maker vs Taker

```mermaid
sequenceDiagram
    participant MM as Market Maker
    participant Book as Order Book
    participant TK as Taker

    MM->>Book: Post buy at 99
    MM->>Book: Post sell at 101
    TK->>Book: Send market buy
    Book-->>TK: Fill at 101
    Book-->>MM: Sell order executed
```

Meaning:

- The market maker places resting quotes.
- The taker wants immediate execution.
- The taker trades at the current best available price.
- The maker earns the spread only if it can buy lower and sell higher over time.

## 5) Code Mapping

- Order creation and demo flow: `src/main/java/com/lowlatencylab/sim/Main.java`
- Risk checks: `src/main/java/com/lowlatencylab/sim/risk/RiskManager.java`
- Matching logic: `src/main/java/com/lowlatencylab/sim/engine/MatchingEngine.java`
- Book state: `src/main/java/com/lowlatencylab/sim/book/OrderBook.java`
- Market-maker quotes: `src/main/java/com/lowlatencylab/sim/strategy/NaiveMarketMakerStrategy.java`