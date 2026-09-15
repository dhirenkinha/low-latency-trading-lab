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
    private final OrderBook orderBook = new OrderBook();
    private final RiskManager riskManager;
    private final Map<String, Integer> positions = new HashMap<>();

    public MatchingEngine(RiskManager riskManager) {
        this.riskManager = riskManager;
    }

    public OptionalLong bestBid() {
        return orderBook.bestBid();
    }

    public OptionalLong bestAsk() {
        return orderBook.bestAsk();
    }

    public Optional<Order> findOrderById(long orderId) {
        return orderBook.findOrderById(orderId);
    }

    public boolean cancelOrder(long orderId) {
        return orderBook.cancelOrder(orderId);
    }

    public Optional<Order> modifyOrder(long orderId, long newPrice, int newQty) {
        Optional<Order> modified = orderBook.modifyOrder(orderId, newPrice, newQty);
        if (modified.isPresent()) {
            modified.get().setStatus(OrderStatus.RESTING);
        }
        return modified;
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

        int requestedDelta = incoming.side() == Side.BUY ? incoming.remainingQty() : -incoming.remainingQty();
        int currentPosition = positions.getOrDefault(incoming.owner(), 0);
        if (!riskManager.canAccept(currentPosition, requestedDelta)) {
            return SubmissionResult.rejected(
                "Risk reject for " + incoming.owner() + ": projected position would breach +/-" + riskManager.maxAbsolutePosition()
            );
        }

        List<Trade> trades = new ArrayList<>();
        while (!incoming.isFilled() && canCross(incoming)) {
            Side restingSide = incoming.side().opposite();
            Deque<Order> levelQueue = orderBook.bestLevel(restingSide);
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
                orderBook.removeBestLevelIfEmpty(restingSide);
            }
        }

        boolean restingAdded = false;
        if (!incoming.isFilled() && incoming.type() == OrderType.LIMIT) {
            incoming.setStatus(OrderStatus.RESTING);
            orderBook.addResting(incoming);
            restingAdded = true;
        }

        return SubmissionResult.accepted(trades, restingAdded);
    }

    public int positionOf(String owner) {
        return positions.getOrDefault(owner, 0);
    }

    public String snapshot(int depth) {
        return orderBook.snapshot(depth);
    }

    private boolean canCross(Order incoming) {
        if (incoming.side() == Side.BUY) {
            var bestAsk = orderBook.bestAsk();
            if (bestAsk.isEmpty()) {
                return false;
            }
            return incoming.type() == OrderType.MARKET || incoming.price() >= bestAsk.getAsLong();
        }

        var bestBid = orderBook.bestBid();
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
        positions.merge(trade.buyOwner(), trade.quantity(), Integer::sum);
        positions.merge(trade.sellOwner(), -trade.quantity(), Integer::sum);
    }
}
