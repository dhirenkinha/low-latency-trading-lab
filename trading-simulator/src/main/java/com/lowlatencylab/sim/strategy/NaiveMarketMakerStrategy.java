package com.lowlatencylab.sim.strategy;

import com.lowlatencylab.sim.model.Order;
import com.lowlatencylab.sim.model.OrderType;
import com.lowlatencylab.sim.model.Side;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class NaiveMarketMakerStrategy implements Strategy {
    private final String owner;
    private final AtomicLong idGenerator;

    public NaiveMarketMakerStrategy(String owner, AtomicLong idGenerator) {
        this.owner = owner;
        this.idGenerator = idGenerator;
    }

    @Override
    public List<Order> onTick(String symbol, long midPrice, long spreadTicks, int quantity, long nowNanos) {
        long bid = midPrice - (spreadTicks / 2);
        long ask = midPrice + (spreadTicks / 2);

        Order buyQuote = new Order(
            idGenerator.incrementAndGet(),
            symbol,
            owner,
            Side.BUY,
            OrderType.LIMIT,
            bid,
            quantity,
            nowNanos
        );

        Order sellQuote = new Order(
            idGenerator.incrementAndGet(),
            symbol,
            owner,
            Side.SELL,
            OrderType.LIMIT,
            ask,
            quantity,
            nowNanos
        );

        return List.of(buyQuote, sellQuote);
    }
}
