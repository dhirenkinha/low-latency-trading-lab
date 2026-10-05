package com.lowlatencylab.sim.model;

public final class Order implements Comparable<Order> {
    private final long id;
    private final String symbol;
    private final String owner;
    private final Side side;
    private final OrderType type;
    private long price;
    private final long timestampNanos;
    private final long sequenceNumber;
    private int remainingQty;
    private OrderStatus status;

    public Order(long id, String symbol, String owner, Side side, OrderType type, long price, int quantity, long timestampNanos) {
        this(id, symbol, owner, side, type, price, quantity, timestampNanos, System.nanoTime());
    }

    public Order(long id, String symbol, String owner, Side side, OrderType type, long price, int quantity, long timestampNanos, long sequenceNumber) {
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
        this.sequenceNumber = sequenceNumber;
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

    public long sequenceNumber() {
        return sequenceNumber;
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
    public int compareTo(Order other) {
        if (this.sequenceNumber < other.sequenceNumber) {
            return -1;
        }
        if (this.sequenceNumber > other.sequenceNumber) {
            return 1;
        }
        return 0;
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
            ", seq=" + sequenceNumber +
            '}';
    }
}
