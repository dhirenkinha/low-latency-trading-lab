package com.lowlatencylab.sim.model;

public final class Trade {
    private final String symbol;
    private final long buyOrderId;
    private final long sellOrderId;
    private final String buyOwner;
    private final String sellOwner;
    private final long price;
    private final int quantity;
    private final long timestampNanos;

    public Trade(String symbol, long buyOrderId, long sellOrderId, String buyOwner, String sellOwner, long price, int quantity, long timestampNanos) {
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.buyOwner = buyOwner;
        this.sellOwner = sellOwner;
        this.price = price;
        this.quantity = quantity;
        this.timestampNanos = timestampNanos;
    }

    public String symbol() {
        return symbol;
    }

    public long buyOrderId() {
        return buyOrderId;
    }

    public long sellOrderId() {
        return sellOrderId;
    }

    public String buyOwner() {
        return buyOwner;
    }

    public String sellOwner() {
        return sellOwner;
    }

    public long price() {
        return price;
    }

    public int quantity() {
        return quantity;
    }

    public long timestampNanos() {
        return timestampNanos;
    }
}
