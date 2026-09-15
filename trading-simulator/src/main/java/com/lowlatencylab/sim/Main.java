package com.lowlatencylab.sim;

import com.lowlatencylab.sim.engine.MatchingEngine;
import com.lowlatencylab.sim.engine.SubmissionResult;
import com.lowlatencylab.sim.model.Order;
import com.lowlatencylab.sim.model.OrderType;
import com.lowlatencylab.sim.model.Side;
import com.lowlatencylab.sim.risk.RiskManager;
import com.lowlatencylab.sim.strategy.NaiveMarketMakerStrategy;

import java.util.concurrent.atomic.AtomicLong;

public final class Main {
    private static final String SYMBOL = "FOO";

    public static void main(String[] args) {
        AtomicLong idGen = new AtomicLong(0);
        MatchingEngine engine = new MatchingEngine(new RiskManager(100));

        System.out.println("=== STEP 1: Seed book with a simple market maker ===");
        NaiveMarketMakerStrategy mm = new NaiveMarketMakerStrategy("MM1", idGen);
        mm.onTick(SYMBOL, 100, 2, 10, System.nanoTime())
            .forEach(order -> printResult("MM quote", engine.submit(order)));

        System.out.println(engine.snapshot(5));



        System.out.println("=== STEP 2: Aggressive buy crosses ask ===");
        Order aggressiveBuy = new Order(
            idGen.incrementAndGet(), SYMBOL, "TAKER_A", Side.BUY, OrderType.MARKET, 0, 6, System.nanoTime()
        );
        printResult("TAKER_A market buy", engine.submit(aggressiveBuy));
        System.out.println(engine.snapshot(5));

        System.out.println("=== STEP 3: Aggressive sell crosses bid ===");
        Order aggressiveSell = new Order(
            idGen.incrementAndGet(), SYMBOL, "TAKER_B", Side.SELL, OrderType.MARKET, 0, 4, System.nanoTime()
        );
        printResult("TAKER_B market sell", engine.submit(aggressiveSell));
        System.out.println(engine.snapshot(5));

        System.out.println("=== STEP 4: Risk reject example ===");
        Order tooLargeBuy = new Order(
            idGen.incrementAndGet(), SYMBOL, "TAKER_A", Side.BUY, OrderType.MARKET, 0, 200, System.nanoTime()
        );
        printResult("TAKER_A oversized order", engine.submit(tooLargeBuy));

        System.out.println("=== FINAL POSITIONS ===");
        System.out.println("MM1=" + engine.positionOf("MM1"));
        System.out.println("TAKER_A=" + engine.positionOf("TAKER_A"));
        System.out.println("TAKER_B=" + engine.positionOf("TAKER_B"));
    }

    private static void printResult(String label, SubmissionResult result) {
        System.out.println("--- " + label + " ---");
        if (!result.accepted()) {
            System.out.println("REJECTED: " + result.reason());
            return;
        }

        if (result.trades().isEmpty()) {
            System.out.println("No trade. restingOrderAdded=" + result.restingOrderAdded());
            return;
        }

        result.trades().forEach(trade -> System.out.println(
            "TRADE symbol=" + trade.symbol() +
                " price=" + trade.price() +
                " qty=" + trade.quantity() +
                " buyOwner=" + trade.buyOwner() +
                " sellOwner=" + trade.sellOwner()
        ));
    }
}
