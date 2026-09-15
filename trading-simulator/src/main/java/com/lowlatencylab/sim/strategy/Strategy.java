package com.lowlatencylab.sim.strategy;

import com.lowlatencylab.sim.model.Order;

import java.util.List;

public interface Strategy {
    List<Order> onTick(String symbol, long midPrice, long spreadTicks, int quantity, long nowNanos);
}
