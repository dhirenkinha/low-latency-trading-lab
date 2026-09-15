package com.lowlatencylab.sim.model;

public final class Order {
    private final long id;
    private final String symbol;
    private final String owner;
    private final Side side;
    private final OrderType type;
    private final long price;
    private final long timestampNanos;
    private int remainingQty;

    public Order(long id, String symbol, String owner, Side side, OrderType type, long price, int quantity, long timestampNanos) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (type == OrderType.LIMIT && price <= 0) {
            throw new IllegalArgumentException("limit price must be > 0");
        }
        this.id = id;
        this.symbol = symbol;
        this.owner = owner;
        this.side = side;
        this.type = type;
        this.price = price;
        this.remainingQty = quantity;
        this.timestampNanos = timestampNanos;
    }

    public long id() {
        return id;
    }

    public String symbol() {
        return symbol;
    }

    public String owner() {
        return owner;
    }

    public Side side() {
        return side;
    }

    public OrderType type() {
        return type;
    }

    public long price() {
        return price;
    }

    public int remainingQty() {
        return remainingQty;
    }

    public long timestampNanos() {
        return timestampNanos;
    }

    public boolean isFilled() {
        return remainingQty == 0;
    }

    public void reduce(int tradedQty) {
        if (tradedQty <= 0 || tradedQty > remainingQty) {
            throw new IllegalArgumentException("invalid tradedQty: " + tradedQty);
        }
        remainingQty -= tradedQty;
    }

    @Override
    public String toString() {
        return "Order{" +
            "id=" + id +
            ", owner='" + owner + '\'' +
            ", side=" + side +
            ", type=" + type +
            ", price=" + price +
            ", remainingQty=" + remainingQty +
            '}';
    }
}
