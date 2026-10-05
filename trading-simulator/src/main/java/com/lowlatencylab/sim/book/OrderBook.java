package com.lowlatencylab.sim.book;

import com.lowlatencylab.sim.model.Order;
import com.lowlatencylab.sim.model.OrderStatus;
import com.lowlatencylab.sim.model.OrderType;
import com.lowlatencylab.sim.model.Side;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
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

    public Optional<Order> findOrderById(long orderId) {
        for (NavigableMap<Long, Deque<Order>> map : List.of(bids, asks)) {
            for (Deque<Order> orders : map.values()) {
                for (Order order : orders) {
                    if (order.id() == orderId) {
                        return Optional.of(order);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Order> findOrderById(String owner, long orderId) {
        for (NavigableMap<Long, Deque<Order>> map : List.of(bids, asks)) {
            for (Deque<Order> orders : map.values()) {
                for (Order order : orders) {
                    if (order.id() == orderId && order.owner().equals(owner)) {
                        return Optional.of(order);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public boolean cancelOrder(long orderId) {
        return cancelOrderFromMap(bids, orderId) || cancelOrderFromMap(asks, orderId);
    }

    public boolean cancelOrder(String owner, long orderId) {
        return cancelOrderFromMap(bids, owner, orderId) || cancelOrderFromMap(asks, owner, orderId);
    }

    public Optional<Order> cancelOrder(Side side, long orderId) {
        NavigableMap<Long, Deque<Order>> map = levelMap(side);
        for (Map.Entry<Long, Deque<Order>> entry : map.entrySet()) {
            Deque<Order> orders = entry.getValue();
            Iterator<Order> iterator = orders.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order.id() == orderId) {
                    iterator.remove();
                    order.setStatus(OrderStatus.CANCELLED);
                    removeEmptyLevelIfNeeded(map, entry.getKey());
                    return Optional.of(order);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Order> cancelOrder(Side side, String owner, long orderId) {
        NavigableMap<Long, Deque<Order>> map = levelMap(side);
        for (Map.Entry<Long, Deque<Order>> entry : map.entrySet()) {
            Deque<Order> orders = entry.getValue();
            Iterator<Order> iterator = orders.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order.id() == orderId && order.owner().equals(owner)) {
                    iterator.remove();
                    order.setStatus(OrderStatus.CANCELLED);
                    removeEmptyLevelIfNeeded(map, entry.getKey());
                    return Optional.of(order);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Order> modifyOrder(long orderId, long newPrice, int newQty) {
        Optional<Order> target = findOrderById(orderId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        Order order = target.get();
        if (order.type() == OrderType.MARKET || !order.isOpen()) {
            return Optional.empty();
        }
        if (newPrice <= 0 || newQty <= 0) {
            return Optional.empty();
        }

        NavigableMap<Long, Deque<Order>> map = levelMap(order.side());
        for (Map.Entry<Long, Deque<Order>> entry : map.entrySet()) {
            Deque<Order> orders = entry.getValue();
            if (orders.remove(order)) {
                removeEmptyLevelIfNeeded(map, entry.getKey());
                order.modify(newPrice, newQty);
                addResting(order);
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    public Optional<Order> modifyOrder(String owner, long orderId, long newPrice, int newQty) {
        Optional<Order> target = findOrderById(owner, orderId);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        Order order = target.get();
        if (order.type() == OrderType.MARKET || !order.isOpen()) {
            return Optional.empty();
        }
        if (newPrice <= 0 || newQty <= 0) {
            return Optional.empty();
        }

        NavigableMap<Long, Deque<Order>> map = levelMap(order.side());
        for (Map.Entry<Long, Deque<Order>> entry : map.entrySet()) {
            Deque<Order> orders = entry.getValue();
            if (orders.remove(order)) {
                removeEmptyLevelIfNeeded(map, entry.getKey());
                order.modify(newPrice, newQty);
                addResting(order);
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    public int totalQtyAtLevel(Side side, long price) {
        Deque<Order> level = levelMap(side).get(price);
        if (level == null) {
            return 0;
        }
        return level.stream().mapToInt(Order::remainingQty).sum();
    }

    public int orderCountAtLevel(Side side, long price) {
        Deque<Order> level = levelMap(side).get(price);
        return level == null ? 0 : level.size();
    }

    public int totalQtyAtBestLevel(Side side) {
        NavigableMap<Long, Deque<Order>> map = levelMap(side);
        if (map.isEmpty()) {
            return 0;
        }
        return totalQtyAtLevel(side, map.firstKey());
    }

    public int orderCountAtBestLevel(Side side) {
        NavigableMap<Long, Deque<Order>> map = levelMap(side);
        if (map.isEmpty()) {
            return 0;
        }
        return orderCountAtLevel(side, map.firstKey());
    }

    public void addResting(Order order) {
        NavigableMap<Long, Deque<Order>> map = levelMap(order.side());
        Deque<Order> level = map.computeIfAbsent(order.price(), ignored -> new ArrayDeque<>());
        level.addLast(order);
        order.setStatus(OrderStatus.RESTING);
        validateBook();
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

    private boolean cancelOrderFromMap(NavigableMap<Long, Deque<Order>> map, long orderId) {
        for (Map.Entry<Long, Deque<Order>> entry : map.entrySet()) {
            Deque<Order> orders = entry.getValue();
            Iterator<Order> iterator = orders.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order.id() == orderId) {
                    iterator.remove();
                    order.setStatus(OrderStatus.CANCELLED);
                    removeEmptyLevelIfNeeded(map, entry.getKey());
                    return true;
                }
            }
        }
        return false;
    }

    private boolean cancelOrderFromMap(NavigableMap<Long, Deque<Order>> map, String owner, long orderId) {
        for (Map.Entry<Long, Deque<Order>> entry : map.entrySet()) {
            Deque<Order> orders = entry.getValue();
            Iterator<Order> iterator = orders.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order.id() == orderId && order.owner().equals(owner)) {
                    iterator.remove();
                    order.setStatus(OrderStatus.CANCELLED);
                    removeEmptyLevelIfNeeded(map, entry.getKey());
                    return true;
                }
            }
        }
        return false;
    }

    private void removeEmptyLevelIfNeeded(NavigableMap<Long, Deque<Order>> map, long price) {
        Deque<Order> level = map.get(price);
        if (level != null && level.isEmpty()) {
            map.remove(price);
        }
    }

    private void validateBook() {
        for (NavigableMap<Long, Deque<Order>> map : List.of(bids, asks)) {
            Iterator<Map.Entry<Long, Deque<Order>>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Deque<Order>> entry = iterator.next();
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }
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
