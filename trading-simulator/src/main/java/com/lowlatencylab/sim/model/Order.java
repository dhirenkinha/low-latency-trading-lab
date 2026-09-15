package com.lowlatencylab.sim.model;

public final class Order {
    private final long id;
    private final String symbol;
    private final String owner;
    private final Side side;
    private final OrderType type;
    private long price;
    private final long timestampNanos;
    private int remainingQty;
    private OrderStatus status;

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
        this.status = OrderStatus.NEW;
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

    public OrderStatus status() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        this.status = status;
    }

    public boolean isFilled() {
        return remainingQty == 0;
    }

    public boolean isOpen() {
        return status != OrderStatus.FILLED && status != OrderStatus.CANCELLED;
    }

    public void reduce(int tradedQty) {
        if (tradedQty <= 0 || tradedQty > remainingQty) {
            throw new IllegalArgumentException("invalid tradedQty: " + tradedQty);
        }
        remainingQty -= tradedQty;
        if (remainingQty == 0) {
            status = OrderStatus.FILLED;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void modify(long newPrice, int newQty) {
        if (type == OrderType.MARKET) {
            throw new IllegalStateException("market orders cannot be modified");
        }
        if (newPrice <= 0) {
            throw new IllegalArgumentException("modified price must be > 0");
        }
        if (newQty <= 0) {
            throw new IllegalArgumentException("modified quantity must be > 0");
        }
        this.price = newPrice;
        this.remainingQty = newQty;
        this.status = OrderStatus.RESTING;
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
            ", status=" + status +
            '}';
    }
}
