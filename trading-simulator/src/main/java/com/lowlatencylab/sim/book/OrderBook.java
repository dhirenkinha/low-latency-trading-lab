package com.lowlatencylab.sim.book;

import com.lowlatencylab.sim.model.Order;
import com.lowlatencylab.sim.model.Side;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;

public final class OrderBook {
    private final NavigableMap<Long, Deque<Order>> bids = new TreeMap<>((a, b) -> Long.compare(b, a));
    private final NavigableMap<Long, Deque<Order>> asks = new TreeMap<>();

    public OptionalLong bestBid() {
        if (bids.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(bids.firstKey());
    }

    public OptionalLong bestAsk() {
        if (asks.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(asks.firstKey());
    }

    public void addResting(Order order) {
        levelMap(order.side())
            .computeIfAbsent(order.price(), ignored -> new ArrayDeque<>())
            .addLast(order);
    }

    public Deque<Order> bestLevel(Side side) {
        NavigableMap<Long, Deque<Order>> map = levelMap(side);
        if (map.isEmpty()) {
            return null;
        }
        return map.firstEntry().getValue();
    }

    public long bestPrice(Side side) {
        return levelMap(side).firstKey();
    }

    public void removeBestLevelIfEmpty(Side side) {
        NavigableMap<Long, Deque<Order>> map = levelMap(side);
        if (map.isEmpty()) {
            return;
        }
        Map.Entry<Long, Deque<Order>> first = map.firstEntry();
        if (first != null && first.getValue().isEmpty()) {
            map.remove(first.getKey());
        }
    }

    public String snapshot(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append("ORDER BOOK\n");
        sb.append("ASKS:\n");
        appendSide(sb, asks, depth);
        sb.append("BIDS:\n");
        appendSide(sb, bids, depth);
        return sb.toString();
    }

    private NavigableMap<Long, Deque<Order>> levelMap(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    private static void appendSide(StringBuilder sb, NavigableMap<Long, Deque<Order>> levels, int depth) {
        int printed = 0;
        for (Map.Entry<Long, Deque<Order>> entry : levels.entrySet()) {
            int totalQty = entry.getValue().stream().mapToInt(Order::remainingQty).sum();
            sb.append("  price=").append(entry.getKey())
                .append(" qty=").append(totalQty)
                .append(" orders=").append(entry.getValue().size())
                .append('\n');
            printed++;
            if (printed >= depth) {
                break;
            }
        }
        if (printed == 0) {
            sb.append("  <empty>\n");
        }
    }
}
