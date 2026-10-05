package com.lowlatencylab.sim.engine;

import com.lowlatencylab.sim.book.OrderBook;
import com.lowlatencylab.sim.model.Order;
import com.lowlatencylab.sim.model.OrderStatus;
import com.lowlatencylab.sim.model.OrderType;
import com.lowlatencylab.sim.model.Side;
import com.lowlatencylab.sim.model.Trade;
import com.lowlatencylab.sim.risk.RiskManager;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public final class MatchingEngine {
    private final Map<String, OrderBook> books = new HashMap<>();
    private final RiskManager riskManager;
    private final Map<String, Integer> positions = new HashMap<>();
    private final Map<String, Map<String, Integer>> positionsByOwnerAndSymbol = new HashMap<>();
    private long globalSequenceNumber = 0;

    public MatchingEngine(RiskManager riskManager) {
        this.riskManager = riskManager;
    }

    public OptionalLong bestBid() {
        return books.values().stream()
            .map(OrderBook::bestBid)
            .filter(OptionalLong::isPresent)
            .mapToLong(OptionalLong::getAsLong)
            .max();
    }

    public OptionalLong bestAsk() {
        return books.values().stream()
            .map(OrderBook::bestAsk)
            .filter(OptionalLong::isPresent)
            .mapToLong(OptionalLong::getAsLong)
            .min();
    }

    public OptionalLong bestBid(String symbol) {
        return bookFor(symbol).bestBid();
    }

    public OptionalLong bestAsk(String symbol) {
        return bookFor(symbol).bestAsk();
    }

    public Optional<Order> findOrderById(long orderId) {
        for (OrderBook book : books.values()) {
            Optional<Order> order = book.findOrderById(orderId);
            if (order.isPresent()) {
                return order;
            }
        }
        return Optional.empty();
    }

    public Optional<Order> findOrderById(String symbol, long orderId) {
        return bookFor(symbol).findOrderById(orderId);
    }

    public Optional<Order> findOrderByOwner(String symbol, String owner, long orderId) {
        return bookFor(symbol).findOrderById(owner, orderId);
    }

    public boolean cancelOrder(long orderId) {
        for (OrderBook book : books.values()) {
            if (book.cancelOrder(orderId)) {
                return true;
            }
        }
        return false;
    }

    public boolean cancelOrder(String symbol, long orderId) {
        return bookFor(symbol).cancelOrder(orderId);
    }

    public boolean cancelOrder(String symbol, String owner, long orderId) {
        return bookFor(symbol).cancelOrder(owner, orderId);
    }

    public Optional<Order> modifyOrder(long orderId, long newPrice, int newQty) {
        for (Map.Entry<String, OrderBook> entry : books.entrySet()) {
            Optional<Order> modified = entry.getValue().modifyOrder(orderId, newPrice, newQty);
            if (modified.isPresent()) {
                return modified;
            }
        }
        return Optional.empty();
    }

    public Optional<Order> modifyOrder(String symbol, long orderId, long newPrice, int newQty) {
        return bookFor(symbol).modifyOrder(orderId, newPrice, newQty);
    }

    public Optional<Order> modifyOrder(String symbol, String owner, long orderId, long newPrice, int newQty) {
        Optional<Order> target = findOrderByOwner(symbol, owner, orderId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        return bookFor(symbol).modifyOrder(owner, orderId, newPrice, newQty);
    }

    public SubmissionResult submit(Order incoming) {
        if (incoming == null) {
            return SubmissionResult.rejected("incoming order is null");
        }
        if (incoming.symbol() == null || incoming.symbol().isBlank()) {
            return SubmissionResult.rejected("symbol is required");
        }
        if (incoming.side() == null) {
            return SubmissionResult.rejected("side is required");
        }
        if (incoming.type() == null) {
            return SubmissionResult.rejected("order type is required");
        }
        if (incoming.remainingQty() <= 0) {
            return SubmissionResult.rejected("quantity must be > 0");
        }
        if (incoming.type() == OrderType.LIMIT && incoming.price() <= 0) {
            return SubmissionResult.rejected("limit price must be > 0");
        }

        // Assign global sequence number for price-time priority
        long incomingSeq = globalSequenceNumber++;

        OrderBook book = bookFor(incoming.symbol());
        int requestedDelta = incoming.side() == Side.BUY ? incoming.remainingQty() : -incoming.remainingQty();
        int currentPosition = currentPositionForOwnerAndSymbol(incoming.owner(), incoming.symbol());
        if (!riskManager.canAccept(currentPosition, requestedDelta)) {
            return SubmissionResult.rejected(
                "Risk reject for " + incoming.owner() + ": projected position would breach +/-" + riskManager.maxAbsolutePosition()
            );
        }

        List<Trade> trades = new ArrayList<>();
        while (!incoming.isFilled() && canCross(incoming, book)) {
            Side restingSide = incoming.side().opposite();
            Deque<Order> levelQueue = book.bestLevel(restingSide);
            if (levelQueue == null || levelQueue.isEmpty()) {
                break;
            }

            Order resting = levelQueue.peekFirst();
            int tradeQty = Math.min(incoming.remainingQty(), resting.remainingQty());
            long tradePrice = resting.price();

            incoming.reduce(tradeQty);
            resting.reduce(tradeQty);

            Trade trade = buildTrade(incoming, resting, tradePrice, tradeQty, System.nanoTime());
            trades.add(trade);
            applyPositionChanges(trade);

            if (resting.isFilled()) {
                levelQueue.removeFirst();
                book.removeBestLevelIfEmpty(restingSide);
            }
        }

        boolean restingAdded = false;
        if (!incoming.isFilled() && incoming.type() == OrderType.LIMIT) {
            incoming.setStatus(OrderStatus.RESTING);
            book.addResting(incoming);
            restingAdded = true;
        }

        return SubmissionResult.accepted(trades, restingAdded);
    }

    public int positionOf(String owner) {
        return positions.getOrDefault(owner, 0);
    }

    public int positionOf(String owner, String symbol) {
        return positionsByOwnerAndSymbol
            .getOrDefault(owner, Map.of())
            .getOrDefault(symbol, 0);
    }

    public String snapshot(int depth) {
        StringBuilder sb = new StringBuilder();
        if (books.isEmpty()) {
            return "ORDER BOOK\n<empty>\n";
        }
        for (String symbol : books.keySet()) {
            sb.append("=== ").append(symbol).append(" ===\n");
            sb.append(books.get(symbol).snapshot(depth));
            sb.append('\n');
        }
        return sb.toString();
    }

    public String snapshot(String symbol, int depth) {
        return bookFor(symbol).snapshot(depth);
    }

    private OrderBook bookFor(String symbol) {
        return books.computeIfAbsent(symbol, ignored -> new OrderBook());
    }

    private boolean canCross(Order incoming, OrderBook book) {
        if (incoming.side() == Side.BUY) {
            var bestAsk = book.bestAsk();
            if (bestAsk.isEmpty()) {
                return false;
            }
            return incoming.type() == OrderType.MARKET || incoming.price() >= bestAsk.getAsLong();
        }

        var bestBid = book.bestBid();
        if (bestBid.isEmpty()) {
            return false;
        }
        return incoming.type() == OrderType.MARKET || incoming.price() <= bestBid.getAsLong();
    }

    private static Trade buildTrade(Order incoming, Order resting, long price, int qty, long tsNanos) {
        if (incoming.side() == Side.BUY) {
            return new Trade(incoming.symbol(), incoming.id(), resting.id(), incoming.owner(), resting.owner(), price, qty, tsNanos);
        }
        return new Trade(incoming.symbol(), resting.id(), incoming.id(), resting.owner(), incoming.owner(), price, qty, tsNanos);
    }

    private void applyPositionChanges(Trade trade) {
        updateOwnerPosition(trade.buyOwner(), trade.symbol(), trade.quantity());
        updateOwnerPosition(trade.sellOwner(), trade.symbol(), -trade.quantity());
        positions.merge(trade.buyOwner(), trade.quantity(), Integer::sum);
        positions.merge(trade.sellOwner(), -trade.quantity(), Integer::sum);
    }

    private void updateOwnerPosition(String owner, String symbol, int delta) {
        Map<String, Integer> symbolPositions = positionsByOwnerAndSymbol.computeIfAbsent(owner, ignored -> new HashMap<>());
        symbolPositions.merge(symbol, delta, Integer::sum);
    }

    private int currentPositionForOwnerAndSymbol(String owner, String symbol) {
        return positionsByOwnerAndSymbol
            .getOrDefault(owner, Map.of())
            .getOrDefault(symbol, 0);
    }
}
